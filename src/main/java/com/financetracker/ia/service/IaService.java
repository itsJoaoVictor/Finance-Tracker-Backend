package com.financetracker.ia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.assinatura.entity.Assinatura;
import com.financetracker.assinatura.repository.AssinaturaRepository;
import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.repository.CategoriaRepository;
import com.financetracker.ia.domain.*;
import com.financetracker.ia.repository.IaCorrecaoUsuarioRepository;
import com.financetracker.ia.repository.IaDicionarioCategoriaRepository;
import com.financetracker.ia.repository.IaInsightRepository;
import com.financetracker.transacao.entity.Fatura;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.StatusFatura;
import com.financetracker.transacao.enums.TipoTransacao;
import com.financetracker.transacao.repository.FaturaRepository;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.usuario.entity.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class IaService {

    private final IaInsightRepository iaInsightRepository;
    private final IaDicionarioCategoriaRepository iaDicionarioCategoriaRepository;
    private final IaCorrecaoUsuarioRepository iaCorrecaoUsuarioRepository;
    private final TransacaoRepository transacaoRepository;
    private final CartaoRepository cartaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final FaturaRepository faturaRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${OPENAI_BASE_URL:https://openrouter.ai/api/v1}")
    private String openAiBaseUrl;

    @Value("${OPENAI_API_KEY:}")
    private String openAiApiKey;

    @Value("${OPENAI_MODEL:deepseek/deepseek-v4-flash}")
    private String openAiModel;

    // Expressões regulares para anonimização de PII (LGPD - RN-13)
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern CPF_PATTERN = Pattern.compile("\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}");
    private static final Pattern CARD_PATTERN = Pattern.compile("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b");

    // Status de faturas que representam meses realmente gastos (para cálculo de média histórica)
    private static final Set<StatusFatura> STATUS_FECHADOS = Set.of(
            StatusFatura.FECHADA, StatusFatura.PAGA, StatusFatura.PAGA_PARCIAL, StatusFatura.ATRASADA
    );

    public IaService(IaInsightRepository iaInsightRepository,
                     IaDicionarioCategoriaRepository iaDicionarioCategoriaRepository,
                     IaCorrecaoUsuarioRepository iaCorrecaoUsuarioRepository,
                     TransacaoRepository transacaoRepository,
                     CartaoRepository cartaoRepository,
                     CategoriaRepository categoriaRepository,
                     AssinaturaRepository assinaturaRepository,
                     FaturaRepository faturaRepository,
                     ObjectMapper objectMapper) {
        this.iaInsightRepository = iaInsightRepository;
        this.iaDicionarioCategoriaRepository = iaDicionarioCategoriaRepository;
        this.iaCorrecaoUsuarioRepository = iaCorrecaoUsuarioRepository;
        this.transacaoRepository = transacaoRepository;
        this.cartaoRepository = cartaoRepository;
        this.categoriaRepository = categoriaRepository;
        this.assinaturaRepository = assinaturaRepository;
        this.faturaRepository = faturaRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    // ═══════════════════════════════════════════════════════════════════
    // RN-07 / RN-12 / RN-13 / RN-14 — CATEGORIZAÇÃO PLN
    // ═══════════════════════════════════════════════════════════════════

    /**
     * RN-12 & RN-13 & RN-14: Classificação de Categorias por Contexto com Cache, LGPD e Feedback Loop
     */
    public Map<String, Object> categorizarTransacao(String descricaoOriginal, UUID usuarioId) {
        String descricaoLimpa = higienizarDescricao(descricaoOriginal);

        // 1. Verificar Cache local (ia_dicionario_categorias)
        Optional<IaDicionarioCategoria> cacheOpt = iaDicionarioCategoriaRepository.findById(descricaoLimpa);
        if (cacheOpt.isPresent()) {
            IaDicionarioCategoria cache = cacheOpt.get();
            Map<String, Object> result = new HashMap<>();
            result.put("categoriaSugerida", cache.getCategoria().getNome());
            result.put("categoriaId", cache.getCategoria().getId());
            result.put("confianca", 1.00);
            result.put("justificativa", "Recuperado do cache local (dicionário de padrões).");
            return result;
        }

        // 2. Chamar Inteligência Artificial (OpenAI/OpenRouter)
        try {
            return chamarIaParaCategorizacao(descricaoLimpa, usuarioId);
        } catch (Exception e) {
            // Fallback resiliente (RN-07 de IA)
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("categoriaSugerida", "Outros");
            fallback.put("confianca", 0.50);
            fallback.put("justificativa", "Falha na conexão com a IA. Categoria atribuída por fallback.");
            return fallback;
        }
    }

    /**
     * Higieniza e anonimiza a descrição para LGPD e FinOps (RN-12 & RN-13)
     */
    public String higienizarDescricao(String texto) {
        if (texto == null) return "";
        // LGPD: Remover PII
        String anonimizado = EMAIL_PATTERN.matcher(texto).replaceAll("[EMAIL]");
        anonimizado = CPF_PATTERN.matcher(anonimizado).replaceAll("[CPF]");
        anonimizado = CARD_PATTERN.matcher(anonimizado).replaceAll("[CARTAO]");

        // FinOps: Remover IDs numéricos longos, sequências de data/hora no fim
        String limpo = anonimizado.replaceAll("\\d{4,}", "").trim();
        limpo = limpo.replaceAll("\\s*-\\s*\\d+/\\d+$", ""); // ex: "- 06/23"

        return limpo.toUpperCase();
    }

    private Map<String, Object> chamarIaParaCategorizacao(String descricaoLimpa, UUID usuarioId) throws Exception {
        if (openAiApiKey == null || openAiApiKey.trim().isEmpty()) {
            throw new IllegalStateException("OpenAI API Key não configurada no backend.");
        }

        // Recupera correções anteriores do usuário para injetar no contexto Few-Shot (RN-14)
        List<IaCorrecaoUsuario> correcoes = iaCorrecaoUsuarioRepository.findByUsuarioId(usuarioId);
        StringBuilder contextBuilder = new StringBuilder();
        if (!correcoes.isEmpty()) {
            contextBuilder.append("Atenção! Siga rigorosamente as seguintes correções aprendidas com as edições passadas do usuário:\n");
            for (IaCorrecaoUsuario c : correcoes) {
                Optional<Categoria> catNova = categoriaRepository.findById(c.getCategoriaNovaId());
                catNova.ifPresent(categoria -> contextBuilder.append("- Para descrições contendo '")
                        .append(c.getDescricaoLimpa())
                        .append("' o resultado DEVE ser a categoria: '")
                        .append(categoria.getNome())
                        .append("'\n"));
            }
        }

        List<Categoria> categoriasDisponiveis = categoriaRepository.findAllByUsuarioId(usuarioId);
        String listaCategorias = categoriasDisponiveis.stream()
                .map(Categoria::getNome)
                .collect(Collectors.joining(", "));

        String promptSistema = "Você é o assistente inteligente do Finence Tracker. Sua função é classificar compras baseando-se no texto de fatura.\n" +
                "Você DEVE responder UNICAMENTE com um objeto JSON válido contendo:\n" +
                "{\n" +
                "  \"categoriaSugerida\": \"NOME_DA_CATEGORIA\",\n" +
                "  \"confianca\": 0.95,\n" +
                "  \"justificativa\": \"Breve explicação\"\n" +
                "}\n" +
                "As categorias disponíveis são: [" + listaCategorias + "]. Escolha preferencialmente uma delas. " +
                "Caso NENHUMA das categorias seja minimamente adequada (por exemplo, Netflix, Spotify, Disney+ deveriam ser classificados em uma nova categoria 'Streaming' ou 'Assinaturas'), você PODE sugerir um nome de categoria nova mais apropriado no campo \"categoriaSugerida\".\n" +
                contextBuilder.toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + openAiApiKey);

        Map<String, Object> messageSystem = new HashMap<>();
        messageSystem.put("role", "system");
        messageSystem.put("content", promptSistema);

        Map<String, Object> messageUser = new HashMap<>();
        messageUser.put("role", "user");
        messageUser.put("content", "Classifique a transação: " + descricaoLimpa);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openAiModel);
        requestBody.put("messages", Arrays.asList(messageSystem, messageUser));
        requestBody.put("temperature", 0.1);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(openAiBaseUrl + "/chat/completions", entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            Map<?, ?> responseMap = objectMapper.readValue(response.getBody(), Map.class);
            List<?> choices = (List<?>) responseMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                Map<?, ?> message = (Map<?, ?>) choice.get("message");
                String content = (String) message.get("content");

                // Parsear resposta JSON da IA
                Map<String, Object> parsedResponse = objectMapper.readValue(content.replaceAll("```json", "").replaceAll("```", "").trim(), Map.class);
                String categoriaSugerida = (String) parsedResponse.get("categoriaSugerida");

                // Gravar no cache (FinOps - RN-12)
                Optional<Categoria> categoriaEncontrada = categoriasDisponiveis.stream()
                        .filter(c -> c.getNome().equalsIgnoreCase(categoriaSugerida))
                        .findFirst();

                if (categoriaEncontrada.isPresent()) {
                    IaDicionarioCategoria cacheEntry = new IaDicionarioCategoria(descricaoLimpa, categoriaEncontrada.get());
                    iaDicionarioCategoriaRepository.save(cacheEntry);
                    parsedResponse.put("categoriaId", categoriaEncontrada.get().getId());
                }

                return parsedResponse;
            }
        }

        throw new RuntimeException("Resposta inválida da API de IA.");
    }

    /**
     * RN-14: Salva o feedback manual e sobrescreve a cache
     */
    public void registrarCorrecaoManual(UUID usuarioId, String descricaoOriginal, UUID categoriaAntigaId, UUID categoriaNovaId, Usuario usuario) {
        String descricaoLimpa = higienizarDescricao(descricaoOriginal);

        // 1. Atualizar ou Criar registro na tabela de cache
        Optional<Categoria> categoriaNovaOpt = categoriaRepository.findById(categoriaNovaId);
        if (categoriaNovaOpt.isPresent()) {
            IaDicionarioCategoria cacheEntry = new IaDicionarioCategoria(descricaoLimpa, categoriaNovaOpt.get());
            iaDicionarioCategoriaRepository.save(cacheEntry);
        }

        // 2. Gravar o log do loop de aprendizado
        IaCorrecaoUsuario correcao = new IaCorrecaoUsuario(usuario, descricaoLimpa, categoriaAntigaId, categoriaNovaId);
        iaCorrecaoUsuarioRepository.save(correcao);
    }

    // ═══════════════════════════════════════════════════════════════════
    // RN-02 — DETECÇÃO DE COBRANÇA DUPLICADA
    // ═══════════════════════════════════════════════════════════════════

    /**
     * RN-02: Detecção de Cobrança Duplicada (tempo real, disparado na criação de transação).
     *
     * Apenas verifica compras primárias (parcela 1 ou sem parcelamento).
     * Parcelas 2-N são lançamentos retroativos programados, não cobranças novas.
     */
    public void analisarNovaTransacao(Transacao transacao) {
        if (transacao.getCartao() == null) return;

        // RN-02: Parcelas N>1 nunca são cobranças duplicadas — são lançamentos retroativos agendados
        if (transacao.getNumeroParcela() != null && transacao.getNumeroParcela() > 1) return;

        LocalDate dataBusca = transacao.getData();
        List<Transacao> doDia = transacaoRepository.findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                transacao.getUsuario().getId(), dataBusca, dataBusca);

        for (Transacao t : doDia) {
            // Ignorar a própria transação e qualquer parcela N>1 (spec: "ignorar parcelas retroativas")
            if (t.getId().equals(transacao.getId())) continue;
            if (t.getNumeroParcela() != null && t.getNumeroParcela() > 1) continue;

            boolean mesmoCartao = t.getCartao() != null && t.getCartao().getId().equals(transacao.getCartao().getId());
            boolean mesmoValor = t.getValor().compareTo(transacao.getValor()) == 0;
            boolean mesmaDescricao = t.getDescricao().equalsIgnoreCase(transacao.getDescricao());

            // Verificar intervalo de tempo <= 10 minutos (spec: "dentro de 10 minutos")
            LocalDateTime criadoEm1 = t.getCriadoEm() != null ? t.getCriadoEm() : LocalDateTime.now();
            LocalDateTime criadoEm2 = transacao.getCriadoEm() != null ? transacao.getCriadoEm() : LocalDateTime.now();
            boolean intervaloOk = Math.abs(ChronoUnit.MINUTES.between(criadoEm1, criadoEm2)) <= 10;

            if (mesmoCartao && mesmoValor && mesmaDescricao && intervaloOk) {

                String checkMetadados = "{\"transacoesEnvolvidas\":[\"" + t.getId() + "\",\"" + transacao.getId() + "\"],\"valor\":" + transacao.getValor() + "}";
                String altMetadados = "{\"transacoesEnvolvidas\":[\"" + transacao.getId() + "\",\"" + t.getId() + "\"],\"valor\":" + transacao.getValor() + "}";

                // Evitar Unicidade: impede gerar o mesmo alerta não lido repetidas vezes
                List<IaInsight> existentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(transacao.getUsuario().getId());
                boolean jaExiste = existentes.stream().anyMatch(ins ->
                        ins.getTipo() == TipoInsight.COBRANCA_DUPLICADA &&
                        (checkMetadados.equals(ins.getMetadados()) || altMetadados.equals(ins.getMetadados())));

                if (!jaExiste) {
                    IaInsight insight = new IaInsight(
                            transacao.getUsuario(),
                            TipoInsight.COBRANCA_DUPLICADA,
                            "Possível cobrança duplicada detectada",
                            "Você passou o cartão duas vezes no estabelecimento '" + transacao.getDescricao() +
                                    "' no valor de R$ " + transacao.getValor() + " em um curto intervalo de tempo. Foi um engano?",
                            checkMetadados
                    );
                    iaInsightRepository.save(insight);
                }
                break;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROCESSAMENTO EM LOTE (agendado pelo IaInsightScheduler)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Processa em lote as regras de negócio complexas de IA para todos os usuários ativos.
     */
    public void processarInsightsParaTodos() {
        List<Usuario> usuarios = transacaoRepository.findAll().stream()
                .map(Transacao::getUsuario)
                .distinct()
                .collect(Collectors.toList());

        for (Usuario usuario : usuarios) {
            processarInsightsParaUsuario(usuario);
        }
    }

    /**
     * Executa RN-01, RN-09, RN-11 para o usuário.
     */
    public void processarInsightsParaUsuario(Usuario usuario) {
        limparInsightsOrfaos(usuario.getId());
        processarInsightsCartaoParaUsuario(usuario);
        processarInsightsAssinaturaParaUsuario(usuario);
        processarTrialHunter(usuario);
    }

    // ═══════════════════════════════════════════════════════════════════
    // RN-01 / RN-04 / RN-11 — ANÁLISE DE CARTÕES
    // ═══════════════════════════════════════════════════════════════════

    public void processarInsightsCartaoParaUsuario(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);

        // Limpar alertas de duplicata cujas transações foram excluídas
        limparInsightsOrfaos(usuarioId);

        List<Cartao> cartoes = cartaoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        LocalDate inicioMes = hoje.withDayOfMonth(1);

        for (Cartao cartao : cartoes) {

            // ─── RN-01: PREVISÃO DE FECHAMENTO (BURN RATE) ─────────────────────────────────
            //
            // Regras:
            // 1. Só conta COMPRA_CREDITO com numeroParcela == null OU numeroParcela == 1 (compras novas)
            // 2. Só dispara a partir do dia 10 do mês (dados suficientes para projeção)
            // 3. Média histórica calculada sobre faturas FECHADAS/PAGAS dos últimos 6 meses
            // 4. Alerta somente se projeção > média + 15%

            long diasPassados = ChronoUnit.DAYS.between(inicioMes, hoje) + 1;

            if (diasPassados >= 10) {
                // Buscar apenas compras primárias do mês atual para este cartão
                List<Transacao> transacoesMes = transacaoRepository
                        .findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(usuarioId, inicioMes, hoje)
                        .stream()
                        .filter(t -> t.getCartao() != null && t.getCartao().getId().equals(cartao.getId()))
                        .filter(t -> t.getTipo() == TipoTransacao.COMPRA_CREDITO)
                        .filter(t -> t.getNumeroParcela() == null || t.getNumeroParcela() == 1)
                        .collect(Collectors.toList());

                BigDecimal totalGastosMes = transacoesMes.stream()
                        .map(Transacao::getValor)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (totalGastosMes.compareTo(BigDecimal.ZERO) > 0) {
                    // Projetar fechamento com base na taxa diária atual
                    BigDecimal mediaDiaria = totalGastosMes.divide(BigDecimal.valueOf(diasPassados), 4, RoundingMode.HALF_UP);
                    int diasNoMes = hoje.lengthOfMonth();
                    BigDecimal fechamentoProjetado = mediaDiaria.multiply(BigDecimal.valueOf(diasNoMes)).setScale(2, RoundingMode.HALF_UP);

                    // Calcular média histórica usando FATURAS FECHADAS dos últimos 6 meses
                    LocalDate seisMesesAtras = hoje.minusMonths(6).withDayOfMonth(1);
                    List<Fatura> faturasFechadas = faturaRepository
                            .findByCartaoIdAndUsuarioIdOrderByMesReferenciaDesc(cartao.getId(), usuarioId)
                            .stream()
                            .filter(f -> STATUS_FECHADOS.contains(f.getStatus()))
                            .filter(f -> !f.getMesReferencia().isBefore(seisMesesAtras))
                            .filter(f -> f.getMesReferencia().isBefore(inicioMes))
                            .collect(Collectors.toList());

                    BigDecimal mediaMensalHistorica;
                    if (!faturasFechadas.isEmpty()) {
                        BigDecimal totalHistorico = faturasFechadas.stream()
                                .map(Fatura::getValorTotal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        mediaMensalHistorica = totalHistorico
                                .divide(BigDecimal.valueOf(faturasFechadas.size()), 2, RoundingMode.HALF_UP);
                    } else {
                        // Fallback: sem histórico suficiente, usa o valor projetado como base
                        mediaMensalHistorica = BigDecimal.valueOf(300.00);
                    }

                    // Dispara o alerta se a projeção for pelo menos 15% acima da média histórica
                    BigDecimal limiarAlerta = mediaMensalHistorica.multiply(BigDecimal.valueOf(1.15)).setScale(2, RoundingMode.HALF_UP);

                    if (fechamentoProjetado.compareTo(limiarAlerta) > 0) {
                        boolean jaExiste = insightsExistentes.stream()
                                .anyMatch(ins -> ins.getTipo() == TipoInsight.CARTAO_PREVISAO
                                        && ins.getMetadados() != null
                                        && ins.getMetadados().contains(cartao.getId().toString()));

                        if (!jaExiste) {
                            String mensagem = String.format(
                                    "Neste ritmo, a fatura do %s fechará em R$ %.2f — %.0f%% acima da sua média dos últimos %d meses (R$ %.2f). Pise no freio.",
                                    cartao.getNome(),
                                    fechamentoProjetado,
                                    ((fechamentoProjetado.subtract(mediaMensalHistorica))
                                            .divide(mediaMensalHistorica, 4, RoundingMode.HALF_UP)
                                            .multiply(BigDecimal.valueOf(100))).doubleValue(),
                                    faturasFechadas.size(),
                                    mediaMensalHistorica
                            );
                            IaInsight insight = new IaInsight(
                                    usuario,
                                    TipoInsight.CARTAO_PREVISAO,
                                    "Previsão de Fatura Acima do Histórico",
                                    mensagem,
                                    "{\"cartaoId\":\"" + cartao.getId() + "\",\"projetado\":" + fechamentoProjetado + ",\"mediaHistorica\":" + mediaMensalHistorica + ",\"mesesHistorico\":" + faturasFechadas.size() + "}"
                            );
                            iaInsightRepository.save(insight);
                        }
                    }
                }
            }

            // ─── RN-04: MELHOR CARTÃO PARA O MOMENTO ──────────────────────────────────────
            //
            // Se a fatura deste cartão fechou nos últimos 0-2 dias, é o melhor momento para
            // usá-lo (maximiza o prazo de pagamento: até ~40 dias de prazo).
            int diaFechamento = cartao.getDiaFechamento();
            int diaFechamentoEfetivo = Math.min(diaFechamento, hoje.lengthOfMonth());
            LocalDate dataFechamentoNoMes = hoje.withDayOfMonth(diaFechamentoEfetivo);
            long diasDesdeOFechamento = ChronoUnit.DAYS.between(dataFechamentoNoMes, hoje);

            if (diasDesdeOFechamento >= 0 && diasDesdeOFechamento <= 2) {
                boolean jaExiste = insightsExistentes.stream()
                        .anyMatch(ins -> ins.getTipo() == TipoInsight.MELHOR_CARTAO
                                && ins.getMetadados() != null
                                && ins.getMetadados().contains(cartao.getId().toString()));

                if (!jaExiste) {
                    int diaVencimento = cartao.getDiaVencimento();
                    IaInsight insight = new IaInsight(
                            usuario,
                            TipoInsight.MELHOR_CARTAO,
                            "Melhor momento para usar o " + cartao.getNome(),
                            String.format("A fatura do %s fechou %s. Compras feitas agora só vencem no dia %d do mês que vem — você tem até ~30 dias de prazo sem juros.",
                                    cartao.getNome(),
                                    diasDesdeOFechamento == 0 ? "hoje" : "há " + diasDesdeOFechamento + " dia(s)",
                                    diaVencimento),
                            "{\"cartaoId\":\"" + cartao.getId() + "\",\"diaVencimento\":" + diaVencimento + "}"
                    );
                    iaInsightRepository.save(insight);
                }
            }

            // ─── RN-11: ALERTA DE ESTOURO DE FATURA / COMPROMETIMENTO DE LIMITE ──────────
            BigDecimal limite = cartao.getLimite();
            BigDecimal disponivel = cartao.getLimiteDisponivel();
            if (limite.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal comprometido = limite.subtract(disponivel);
                double percentualComprometido = comprometido
                        .divide(limite, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue();

                if (percentualComprometido >= 75.0) {
                    boolean jaExiste = insightsExistentes.stream()
                            .anyMatch(ins -> ins.getTipo() == TipoInsight.ESTOURO_FATURA
                                    && ins.getMetadados() != null
                                    && ins.getMetadados().contains(cartao.getId().toString()));

                    if (!jaExiste) {
                        IaInsight insight = new IaInsight(
                                usuario,
                                TipoInsight.ESTOURO_FATURA,
                                "Alerta de Comprometimento de Limite",
                                String.format("Atenção! %.0f%% do limite do seu cartão %s (R$ %.2f de R$ %.2f) está comprometido com compras parceladas.",
                                        percentualComprometido, cartao.getNome(), comprometido, limite),
                                "{\"cartaoId\":\"" + cartao.getId() + "\",\"comprometidoPercentual\":" + percentualComprometido + "}"
                        );
                        iaInsightRepository.save(insight);
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RN-05 / RN-06 — ANÁLISE DE ASSINATURAS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Processa RN-05 (fadiga de assinatura) e RN-06 (reajuste silencioso) para o usuário.
     *
     * Usa diretamente o repositório de Assinaturas (entidade própria), não transações.
     */
    public void processarInsightsAssinaturaParaUsuario(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);

        List<Assinatura> todasAssinaturas = assinaturaRepository.findByUsuarioId(usuarioId);
        List<Assinatura> assinaturasAtivas = todasAssinaturas.stream()
                .filter(a -> Boolean.TRUE.equals(a.getAtivo()))
                .collect(Collectors.toList());

        if (assinaturasAtivas.isEmpty()) return;

        // ─── RN-05: FADIGA DE ASSINATURA ────────────────────────────────────────────────
        //
        // Agrupa assinaturas ativas pela categoria e dispara alerta se:
        // - 3 ou mais assinaturas na mesma categoria, OU
        // - Total mensal do grupo ultrapassa R$ 100,00

        // Converter valores para mensal equivalente para comparação justa
        Map<String, List<Assinatura>> porCategoria = assinaturasAtivas.stream()
                .collect(Collectors.groupingBy(a -> a.getCategoria().getNome()));

        for (Map.Entry<String, List<Assinatura>> entry : porCategoria.entrySet()) {
            String nomeCategoria = entry.getKey();
            List<Assinatura> grupo = entry.getValue();

            if (grupo.size() < 2) continue; // Precisa de pelo menos 2 para ser relevante

            BigDecimal totalMensalGrupo = grupo.stream()
                    .map(a -> calcularValorMensal(a))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            boolean fadigaPorQuantidade = grupo.size() >= 3;
            boolean fadigaPorValor = totalMensalGrupo.compareTo(BigDecimal.valueOf(100.00)) > 0;

            if (fadigaPorQuantidade || fadigaPorValor) {
                String chaveCategoria = nomeCategoria.toLowerCase().replaceAll("\\s+", "_");
                boolean jaExiste = insightsExistentes.stream()
                        .anyMatch(ins -> ins.getTipo() == TipoInsight.FADIGA_ASSINATURA
                                && ins.getMetadados() != null
                                && ins.getMetadados().contains(chaveCategoria));

                if (!jaExiste) {
                    String nomesDasAssinaturas = grupo.stream()
                            .map(Assinatura::getNome)
                            .collect(Collectors.joining(", "));
                    String motivo = fadigaPorQuantidade
                            ? String.format("%d assinaturas na mesma categoria", grupo.size())
                            : String.format("R$ %.2f/mês gastos em serviços similares", totalMensalGrupo);

                    IaInsight insight = new IaInsight(
                            usuario,
                            TipoInsight.FADIGA_ASSINATURA,
                            "Fadiga de Assinaturas: " + nomeCategoria,
                            String.format("Você possui %s na categoria \"%s\" (%s): %s. Considere consolidar ou cancelar algum serviço redundante.",
                                    motivo, nomeCategoria, formatarValor(totalMensalGrupo) + "/mês", nomesDasAssinaturas),
                            "{\"categoria\":\"" + chaveCategoria + "\",\"totalMensal\":" + totalMensalGrupo + ",\"quantidade\":" + grupo.size() + "}"
                    );
                    iaInsightRepository.save(insight);
                }
            }
        }

        // ─── RN-06: REAJUSTE SILENCIOSO ────────────────────────────────────────────────
        //
        // Para cada assinatura ativa, busca as últimas transações geradas pelo scheduler
        // (descrição "Assinatura: NOME") e compara os valores cobrados.
        // Se houver aumento > 5% entre a penúltima e a última cobrança → alerta.

        LocalDate noventaDiasAtras = LocalDate.now().minusDays(90);

        for (Assinatura assinatura : assinaturasAtivas) {
            String descricaoEsperada = "Assinatura: " + assinatura.getNome();

            // Buscar transações desta assinatura ordenadas por data
            List<Transacao> transacoesAssinatura = transacaoRepository
                    .findTopByDescricaoLike(usuarioId, assinatura.getNome())
                    .stream()
                    .filter(t -> t.getDescricao() != null && t.getDescricao().equalsIgnoreCase(descricaoEsperada))
                    .filter(t -> !t.getData().isBefore(noventaDiasAtras))
                    .sorted(Comparator.comparing(Transacao::getData).reversed())
                    .collect(Collectors.toList());

            if (transacoesAssinatura.size() >= 2) {
                BigDecimal valorMaisRecente = transacoesAssinatura.get(0).getValor();
                BigDecimal valorAnterior = transacoesAssinatura.get(1).getValor();

                if (valorAnterior.compareTo(BigDecimal.ZERO) > 0 && valorMaisRecente.compareTo(valorAnterior) > 0) {
                    BigDecimal diferenca = valorMaisRecente.subtract(valorAnterior);
                    BigDecimal percentualAumento = diferenca
                            .divide(valorAnterior, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));

                    if (percentualAumento.compareTo(BigDecimal.valueOf(5.0)) > 0) {
                        boolean jaExiste = insightsExistentes.stream()
                                .anyMatch(ins -> ins.getTipo() == TipoInsight.REAJUSTE_SILENCIOSO
                                        && ins.getMetadados() != null
                                        && ins.getMetadados().contains(assinatura.getId().toString()));

                        if (!jaExiste) {
                            IaInsight insight = new IaInsight(
                                    usuario,
                                    TipoInsight.REAJUSTE_SILENCIOSO,
                                    "Reajuste silencioso: " + assinatura.getNome(),
                                    String.format("A cobrança de \"%s\" aumentou %.1f%% — de R$ %.2f para R$ %.2f. Nenhuma mudança de plano foi registrada. Vale verificar.",
                                            assinatura.getNome(),
                                            percentualAumento.doubleValue(),
                                            valorAnterior,
                                            valorMaisRecente),
                                    "{\"assinaturaId\":\"" + assinatura.getId() + "\",\"nome\":\"" + assinatura.getNome() + "\",\"valorAnterior\":" + valorAnterior + ",\"valorAtual\":" + valorMaisRecente + "}"
                            );
                            iaInsightRepository.save(insight);
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RN-09 — ASSINATURAS ESQUECIDAS (TRIAL HUNTER)
    // ═══════════════════════════════════════════════════════════════════

    private void processarTrialHunter(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        LocalDate trintaDiasAtras = hoje.minusDays(30);
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);

        List<Transacao> transacoesRecentes = transacaoRepository
                .findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(usuarioId, trintaDiasAtras, hoje);

        for (Transacao t : transacoesRecentes) {
            BigDecimal v = t.getValor();
            // Detectar transação de teste (valor de R$0 a R$2)
            if (v.compareTo(BigDecimal.ZERO) > 0 && v.compareTo(BigDecimal.valueOf(2.00)) <= 0) {
                // Procurar transação do mesmo estabelecimento com valor cheio nos dias seguintes
                List<Transacao> posteriores = transacoesRecentes.stream()
                        .filter(p -> p.getData().isAfter(t.getData()))
                        .filter(p -> p.getDescricao() != null && p.getDescricao().equalsIgnoreCase(t.getDescricao()))
                        .filter(p -> p.getValor().compareTo(BigDecimal.valueOf(10.00)) > 0)
                        .collect(Collectors.toList());

                if (!posteriores.isEmpty()) {
                    Transacao cobrada = posteriores.get(0);
                    boolean jaExiste = insightsExistentes.stream()
                            .anyMatch(ins -> ins.getTipo() == TipoInsight.ASSINATURA_ESQUECIDA
                                    && ins.getMetadados() != null
                                    && ins.getMetadados().contains(cobrada.getDescricao()));

                    if (!jaExiste) {
                        IaInsight insight = new IaInsight(
                                usuario,
                                TipoInsight.ASSINATURA_ESQUECIDA,
                                "Período de testes expirado",
                                String.format("Identificamos a primeira cobrança de valor cheio (R$ %.2f) para \"%s\". O período gratuito (Free Trial) pode ter expirado.",
                                        cobrada.getValor().doubleValue(), cobrada.getDescricao()),
                                "{\"estabelecimento\":\"" + cobrada.getDescricao() + "\",\"valor\":" + cobrada.getValor() + "}"
                        );
                        iaInsightRepository.save(insight);
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RN-03 — SIMULAÇÃO DE COMPRA PARCELADA
    // ═══════════════════════════════════════════════════════════════════

    /**
     * RN-03: Simula o impacto futuro de uma compra parcelada nas faturas futuras do cartão.
     * Retorna projeção mensal e alerta se comprometer mais de 30% do limite.
     */
    public Map<String, Object> simularCompraParcela(UUID cartaoId, BigDecimal valorTotal, int numeroParcelas, UUID usuarioId) {
        Cartao cartao = cartaoRepository.findByIdAndUsuarioIdAndAtivoTrue(cartaoId, usuarioId)
                .orElseThrow(() -> new RuntimeException("Cartão não encontrado."));

        BigDecimal valorParcela = valorTotal.divide(BigDecimal.valueOf(numeroParcelas), 2, RoundingMode.HALF_UP);
        BigDecimal limiteDisponivel = cartao.getLimiteDisponivel();
        BigDecimal limiteTotal = cartao.getLimite();

        LocalDate hoje = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");

        List<Map<String, Object>> projecoesMensais = new ArrayList<>();
        BigDecimal totalComprometidoFuturo = BigDecimal.ZERO;

        for (int i = 0; i < numeroParcelas; i++) {
            LocalDate mesReferencia = hoje.plusMonths(i).withDayOfMonth(1);

            // Buscar faturas já existentes para este mês/cartão
            Optional<Fatura> faturaExistente = faturaRepository
                    .findByCartaoIdAndUsuarioIdAndMesReferencia(cartaoId, usuarioId, mesReferencia);

            BigDecimal valorJaNaFatura = faturaExistente
                    .map(Fatura::getValorTotal)
                    .orElse(BigDecimal.ZERO);

            BigDecimal valorComParcela = valorJaNaFatura.add(valorParcela);
            totalComprometidoFuturo = totalComprometidoFuturo.add(valorParcela);

            Map<String, Object> projecao = new HashMap<>();
            projecao.put("mes", mesReferencia.format(fmt));
            projecao.put("valorJaNaFatura", valorJaNaFatura);
            projecao.put("valorParcela", valorParcela);
            projecao.put("valorComParcela", valorComParcela);
            projecoesMensais.add(projecao);
        }

        // Verificar se compromete mais de 30% do limite total (RN-03)
        double percentualDoLimite = limiteTotal.compareTo(BigDecimal.ZERO) > 0
                ? valorTotal.divide(limiteTotal, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        boolean impactoNegativo = percentualDoLimite >= 30.0 || valorTotal.compareTo(limiteDisponivel) > 0;

        String mensagem;
        if (valorTotal.compareTo(limiteDisponivel) > 0) {
            mensagem = String.format("Atenção! O valor total de R$ %.2f supera seu limite disponível atual de R$ %.2f.",
                    valorTotal, limiteDisponivel);
        } else if (impactoNegativo) {
            mensagem = String.format("Essa compra comprometerá %.0f%% do seu limite total. As parcelas de R$ %.2f/mês impactarão suas próximas %d faturas.",
                    percentualDoLimite, valorParcela, numeroParcelas);
        } else {
            mensagem = String.format("Compra dentro da capacidade. Parcelas de R$ %.2f/mês por %d meses, comprometendo %.0f%% do limite.",
                    valorParcela, numeroParcelas, percentualDoLimite);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("impactoNegativo", impactoNegativo);
        result.put("mensagem", mensagem);
        result.put("valorParcela", valorParcela);
        result.put("percentualDoLimite", Math.round(percentualDoLimite * 10.0) / 10.0);
        result.put("limiteDisponivel", limiteDisponivel);
        result.put("projecoesMensais", projecoesMensais);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS INTERNOS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Remove insights de cobrança duplicada cujas transações associadas foram excluídas/inativadas.
     */
    private void limparInsightsOrfaos(UUID usuarioId) {
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);
        for (IaInsight ins : insightsExistentes) {
            if (ins.getTipo() == TipoInsight.COBRANCA_DUPLICADA && ins.getMetadados() != null) {
                try {
                    Map<?, ?> meta = objectMapper.readValue(ins.getMetadados(), Map.class);
                    List<?> ids = (List<?>) meta.get("transacoesEnvolvidas");
                    if (ids != null) {
                        boolean todasInativas = ids.stream().noneMatch(idStr -> {
                            try {
                                return transacaoRepository
                                        .findByIdAndUsuarioIdAndAtivoTrue(UUID.fromString((String) idStr), usuarioId)
                                        .isPresent();
                            } catch (Exception e) {
                                return false;
                            }
                        });
                        if (todasInativas) {
                            iaInsightRepository.delete(ins);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Converte o valor de uma assinatura para equivalente mensal.
     * - MENSAL: valor direto
     * - ANUAL: valor / 12
     * - TRIMESTRAL: valor / 3
     * - PERSONALIZADO: estimativa baseada na unidade/frequência
     */
    private BigDecimal calcularValorMensal(Assinatura assinatura) {
        BigDecimal valor = assinatura.getValor();
        if (valor == null || valor.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        return switch (assinatura.getTipoRecorrencia()) {
            case ANUAL -> valor.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case TRIMESTRAL -> valor.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            case PERSONALIZADO -> {
                if (assinatura.getFrequencia() == null || assinatura.getUnidadeFrequencia() == null) {
                    yield valor;
                }
                yield switch (assinatura.getUnidadeFrequencia()) {
                    case ANOS -> valor.divide(BigDecimal.valueOf(12L * assinatura.getFrequencia()), 2, RoundingMode.HALF_UP);
                    case MESES -> assinatura.getFrequencia() > 1
                            ? valor.divide(BigDecimal.valueOf(assinatura.getFrequencia()), 2, RoundingMode.HALF_UP)
                            : valor;
                    case SEMANAS -> valor.multiply(BigDecimal.valueOf(4));
                };
            }
            default -> valor; // MENSAL
        };
    }

    private String formatarValor(BigDecimal valor) {
        return String.format("R$ %.2f", valor);
    }
}
