package com.financetracker.ia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.repository.CategoriaRepository;
import com.financetracker.ia.domain.*;
import com.financetracker.ia.repository.IaCorrecaoUsuarioRepository;
import com.financetracker.ia.repository.IaDicionarioCategoriaRepository;
import com.financetracker.ia.repository.IaInsightRepository;
import com.financetracker.transacao.entity.Transacao;
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

    public IaService(IaInsightRepository iaInsightRepository,
                     IaDicionarioCategoriaRepository iaDicionarioCategoriaRepository,
                     IaCorrecaoUsuarioRepository iaCorrecaoUsuarioRepository,
                     TransacaoRepository transacaoRepository,
                     CartaoRepository cartaoRepository,
                     CategoriaRepository categoriaRepository,
                     ObjectMapper objectMapper) {
        this.iaInsightRepository = iaInsightRepository;
        this.iaDicionarioCategoriaRepository = iaDicionarioCategoriaRepository;
        this.iaCorrecaoUsuarioRepository = iaCorrecaoUsuarioRepository;
        this.transacaoRepository = transacaoRepository;
        this.cartaoRepository = cartaoRepository;
        this.categoriaRepository = categoriaRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

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

    /**
     * RN-02: Detecção de Cobrança Duplicada (tempo real)
     */
    public void analisarNovaTransacao(Transacao transacao) {
        if (transacao.getCartao() == null) return;

        LocalDate dataBusca = transacao.getData();
        List<Transacao> doDia = transacaoRepository.findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                transacao.getUsuario().getId(), dataBusca, dataBusca);

        for (Transacao t : doDia) {
            if (!t.getId().equals(transacao.getId()) &&
                    t.getCartao() != null && t.getCartao().getId().equals(transacao.getCartao().getId()) &&
                    t.getValor().compareTo(transacao.getValor()) == 0 &&
                    t.getDescricao().equalsIgnoreCase(transacao.getDescricao()) &&
                    ChronoUnit.MINUTES.between(t.getCriadoEm(), transacao.getCriadoEm()) <= 10) {

                String checkMetadados = "{\"transacoesEnvolvidas\":[\"" + t.getId() + "\",\"" + transacao.getId() + "\"],\"valor\":" + transacao.getValor() + "}";
                String altMetadados = "{\"transacoesEnvolvidas\":[\"" + transacao.getId() + "\",\"" + t.getId() + "\"],\"valor\":" + transacao.getValor() + "}";

                // Evitar Unicidade: impede gerar o mesmo alerta não lido repetidas vezes
                boolean jaExiste = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(transacao.getUsuario().getId())
                        .stream().anyMatch(ins -> ins.getTipo() == TipoInsight.COBRANCA_DUPLICADA &&
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

    /**
     * Processa em lote as regras de negócio complexas de IA para todos os usuários ativos (RN-01, RN-09, RN-11)
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

    public void processarInsightsParaUsuario(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();

        // Limpeza de Insights Órfãos (Cobrança Duplicada cujas transações associadas foram deletadas)
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);
        for (IaInsight ins : insightsExistentes) {
            if (ins.getTipo() == TipoInsight.COBRANCA_DUPLICADA && ins.getMetadados() != null) {
                try {
                    Map<?, ?> meta = objectMapper.readValue(ins.getMetadados(), Map.class);
                    List<?> ids = (List<?>) meta.get("transacoesEnvolvidas");
                    if (ids != null) {
                        boolean algumaAtiva = false;
                        for (Object idStr : ids) {
                            Optional<Transacao> tOpt = transacaoRepository.findByIdAndUsuarioIdAndAtivoTrue(
                                    UUID.fromString((String) idStr), usuarioId);
                            if (tOpt.isPresent()) {
                                algumaAtiva = true;
                            }
                        }
                        // Se todas as transações daquela duplicidade foram apagadas/inativadas, deletar o insight
                        if (!algumaAtiva) {
                            iaInsightRepository.delete(ins);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // ─── RN-01: PREVISÃO DE FECHAMENTO (BURN RATE) ───
        List<Cartao> cartoes = cartaoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        for (Cartao cartao : cartoes) {
            LocalDate inicioMes = hoje.withDayOfMonth(1);
            List<Transacao> transacoesMes = transacaoRepository.findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                    usuarioId, inicioMes, hoje);

            BigDecimal totalGastos = transacoesMes.stream()
                    .filter(t -> t.getCartao() != null && t.getCartao().getId().equals(cartao.getId()))
                    .map(Transacao::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long diasPassados = ChronoUnit.DAYS.between(inicioMes, hoje) + 1;
            if (diasPassados >= 3 && totalGastos.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal mediaDiaria = totalGastos.divide(BigDecimal.valueOf(diasPassados), 2, RoundingMode.HALF_UP);
                int diasNoMes = hoje.lengthOfMonth();
                BigDecimal fechamentoProjetado = mediaDiaria.multiply(BigDecimal.valueOf(diasNoMes));

                // Buscar transações dos últimos 6 meses para estabelecer a média histórica dinâmica do cartão
                LocalDate seisMesesAtras = hoje.minusMonths(6).withDayOfMonth(1);
                List<Transacao> transacoesHistoricas = transacaoRepository.findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                        usuarioId, seisMesesAtras, inicioMes.minusDays(1));

                BigDecimal totalHistorico = transacoesHistoricas.stream()
                        .filter(t -> t.getCartao() != null && t.getCartao().getId().equals(cartao.getId()))
                        .map(Transacao::getValor)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Calcular média mensal histórica real do cartão
                BigDecimal mediaMensalHistorica = BigDecimal.valueOf(300.00); // Fallback padrão mínimo
                long totalDiasHistorico = ChronoUnit.DAYS.between(seisMesesAtras, inicioMes.minusDays(1)) + 1;
                if (totalDiasHistorico > 30 && totalHistorico.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal mediaDiariaHistorica = totalHistorico.divide(BigDecimal.valueOf(totalDiasHistorico), 2, RoundingMode.HALF_UP);
                    mediaMensalHistorica = mediaDiariaHistorica.multiply(BigDecimal.valueOf(30));
                }

                // Dispara o alerta se a projeção da fatura atual for pelo menos 15% superior à média dinâmica de 6 meses
                BigDecimal margemAlerta = mediaMensalHistorica.multiply(BigDecimal.valueOf(1.15));
                if (fechamentoProjetado.compareTo(margemAlerta) > 0) {
                    boolean jaExiste = insightsExistentes.stream()
                            .anyMatch(ins -> ins.getTipo() == TipoInsight.CARTAO_PREVISAO &&
                                    ins.getMetadados() != null && ins.getMetadados().contains(cartao.getId().toString()));

                    if (!jaExiste) {
                        IaInsight insight = new IaInsight(
                                usuario,
                                TipoInsight.CARTAO_PREVISAO,
                                "Previsão de Fatura Acima do Histórico",
                                "Neste ritmo, a fatura do seu cartão " + cartao.getNome() + " fechará projetada em R$ " + fechamentoProjetado +
                                        ", o que é superior à sua média mensal dos últimos 6 meses (R$ " + mediaMensalHistorica.setScale(2, RoundingMode.HALF_UP) + "). Pise no freio.",
                                "{\"cartaoId\":\"" + cartao.getId() + "\",\"projetado\":" + fechamentoProjetado + ",\"mediaHistorica\":" + mediaMensalHistorica + "}"
                        );
                        iaInsightRepository.save(insight);
                    }
                }
            }
        }

        // ─── RN-11: ALERTA DE ESTOURO DE FATURA ───
        for (Cartao cartao : cartoes) {
            BigDecimal limite = cartao.getLimite();
            BigDecimal disponivel = cartao.getLimiteDisponivel();
            if (limite.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal comprometido = limite.subtract(disponivel);
                double percentualComprometido = comprometido.divide(limite, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue();

                if (percentualComprometido >= 75.0) {
                    boolean jaExiste = insightsExistentes.stream()
                            .anyMatch(ins -> ins.getTipo() == TipoInsight.ESTOURO_FATURA &&
                                    ins.getMetadados() != null && ins.getMetadados().contains(cartao.getId().toString()));

                    if (!jaExiste) {
                        IaInsight insight = new IaInsight(
                                usuario,
                                TipoInsight.ESTOURO_FATURA,
                                "Alerta de Comprometimento de Limite",
                                "Atenção! Você possui " + percentualComprometido + "% do limite do seu cartão " + cartao.getNome() + " comprometido com compras parceladas.",
                                "{\"cartaoId\":\"" + cartao.getId() + "\",\"comprometidoPercentual\":" + percentualComprometido + "}"
                        );
                        iaInsightRepository.save(insight);
                    }
                }
            }
        }

        // ─── RN-09: ASSINATURAS ESQUECIDAS (TRIAL HUNTER) ───
        LocalDate trintaDiasAtras = hoje.minusDays(30);
        List<Transacao> transacoesRecentes = transacaoRepository.findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                usuarioId, trintaDiasAtras, hoje);

        for (Transacao t : transacoesRecentes) {
            BigDecimal v = t.getValor();
            // Teste ou verificação (valores nulos ou < R$ 2.00)
            if (v.compareTo(BigDecimal.ZERO) > 0 && v.compareTo(BigDecimal.valueOf(2.00)) <= 0) {
                // Procurar transação idêntica (mesmo estabelecimento) com valor cheio nos dias seguintes (7, 14 ou 30)
                List<Transacao> posteriores = transacoesRecentes.stream()
                        .filter(p -> p.getData().isAfter(t.getData()) &&
                                p.getDescricao().equalsIgnoreCase(t.getDescricao()) &&
                                p.getValor().compareTo(BigDecimal.valueOf(10.00)) > 0)
                        .collect(Collectors.toList());

                if (!posteriores.isEmpty()) {
                    Transacao cobrada = posteriores.get(0);
                    boolean jaExiste = insightsExistentes.stream()
                            .anyMatch(ins -> ins.getTipo() == TipoInsight.ASSINATURA_ESQUECIDA &&
                                    ins.getMetadados() != null && ins.getMetadados().contains(cobrada.getDescricao()));

                    if (!jaExiste) {
                        IaInsight insight = new IaInsight(
                                usuario,
                                TipoInsight.ASSINATURA_ESQUECIDA,
                                "Período de testes expirado",
                                "Identificamos a primeira cobrança de valor cheio (R$ " + cobrada.getValor() + ") para " + cobrada.getDescricao() + ". O período gratuito (Free Trial) expirou.",
                                "{\"estabelecimento\":\"" + cobrada.getDescricao() + "\",\"valor\":" + cobrada.getValor() + "}"
                        );
                        iaInsightRepository.save(insight);
                    }
                }
            }
        }
    }

    public void processarInsightsAssinaturaParaUsuario(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);

        // ─── RN-06: ALERTA DE REAJUSTE SILENCIOSO / MENSALIDADES ───
        // Buscar transações dos últimos 90 dias
        LocalDate noventaDiasAtras = hoje.minusDays(90);
        List<Transacao> transacoesRecentes = transacaoRepository.findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                usuarioId, noventaDiasAtras, hoje);

        // Agrupar transações pelo estabelecimento (descrição limpa)
        Map<String, List<Transacao>> porEstabelecimento = transacoesRecentes.stream()
                .filter(t -> t.getValor().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(t -> higienizarDescricao(t.getDescricao())));

        for (Map.Entry<String, List<Transacao>> entry : porEstabelecimento.entrySet()) {
            String est = entry.getKey();
            List<Transacao> txs = entry.getValue();

            // Se tem pelo menos 2 ocorrências, podemos comparar reajustes
            if (txs.size() >= 2) {
                // Ordena por data decrescente (mais recente primeiro)
                txs.sort((t1, t2) -> t2.getData().compareTo(t1.getData()));
                Transacao maisRecente = txs.get(0);
                Transacao anterior = txs.get(1);

                BigDecimal valorAtual = maisRecente.getValor();
                BigDecimal valorAnterior = anterior.getValor();

                if (valorAnterior.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal diferenca = valorAtual.subtract(valorAnterior);
                    BigDecimal percentualAumento = diferenca.divide(valorAnterior, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));

                    // Se houve aumento superior a 5% (RN-06)
                    if (percentualAumento.compareTo(BigDecimal.valueOf(5.0)) > 0) {
                        boolean jaExiste = insightsExistentes.stream()
                                .anyMatch(ins -> ins.getTipo() == TipoInsight.REAJUSTE_SILENCIOSO &&
                                        ins.getMetadados() != null && ins.getMetadados().contains(est));

                        if (!jaExiste) {
                            IaInsight insight = new IaInsight(
                                    usuario,
                                    TipoInsight.REAJUSTE_SILENCIOSO,
                                    "Reajuste silencioso detectado",
                                    "Identificamos um aumento de " + percentualAumento.setScale(1, RoundingMode.HALF_UP) +
                                            "% no valor cobrado por " + maisRecente.getDescricao() +
                                            ". Passou de R$ " + valorAnterior + " para R$ " + valorAtual + ".",
                                    "{\"estabelecimento\":\"" + est + "\",\"valorAnterior\":" + valorAnterior + ",\"valorAtual\":" + valorAtual + "}"
                            );
                            iaInsightRepository.save(insight);
                        }
                    }
                }
            }
        }
    }

    public void processarInsightsCartaoParaUsuario(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);

        // Limpeza de duplicados órfãos
        for (IaInsight ins : insightsExistentes) {
            if (ins.getTipo() == TipoInsight.COBRANCA_DUPLICADA && ins.getMetadados() != null) {
                try {
                    Map<?, ?> meta = objectMapper.readValue(ins.getMetadados(), Map.class);
                    List<?> ids = (List<?>) meta.get("transacoesEnvolvidas");
                    if (ids != null) {
                        boolean algumaAtiva = false;
                        for (Object idStr : ids) {
                            Optional<Transacao> tOpt = transacaoRepository.findByIdAndUsuarioIdAndAtivoTrue(
                                    UUID.fromString((String) idStr), usuarioId);
                            if (tOpt.isPresent()) {
                                algumaAtiva = true;
                            }
                        }
                        if (!algumaAtiva) {
                            iaInsightRepository.delete(ins);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        List<Cartao> cartoes = cartaoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        for (Cartao cartao : cartoes) {
            // ─── RN-01: PREVISÃO DE FECHAMENTO (BURN RATE / MÉDIA HISTÓRICA DE 6 MESES) ───
            LocalDate inicioMes = hoje.withDayOfMonth(1);
            List<Transacao> transacoesMes = transacaoRepository.findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                    usuarioId, inicioMes, hoje);

            BigDecimal totalGastos = transacoesMes.stream()
                    .filter(t -> t.getCartao() != null && t.getCartao().getId().equals(cartao.getId()))
                    .map(Transacao::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long diasPassados = ChronoUnit.DAYS.between(inicioMes, hoje) + 1;
            if (diasPassados >= 3 && totalGastos.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal mediaDiaria = totalGastos.divide(BigDecimal.valueOf(diasPassados), 2, RoundingMode.HALF_UP);
                int diasNoMes = hoje.lengthOfMonth();
                BigDecimal fechamentoProjetado = mediaDiaria.multiply(BigDecimal.valueOf(diasNoMes));

                // Buscar transações dos últimos 6 meses para estabelecer a média histórica dinâmica do cartão
                LocalDate seisMesesAtras = hoje.minusMonths(6).withDayOfMonth(1);
                List<Transacao> transacoesHistoricas = transacaoRepository.findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                        usuarioId, seisMesesAtras, inicioMes.minusDays(1));

                BigDecimal totalHistorico = transacoesHistoricas.stream()
                        .filter(t -> t.getCartao() != null && t.getCartao().getId().equals(cartao.getId()))
                        .map(Transacao::getValor)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Calcular média mensal histórica real do cartão
                BigDecimal mediaMensalHistorica = BigDecimal.valueOf(300.00); // Fallback padrão mínimo
                long totalDiasHistorico = ChronoUnit.DAYS.between(seisMesesAtras, inicioMes.minusDays(1)) + 1;
                if (totalDiasHistorico > 30 && totalHistorico.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal mediaDiariaHistorica = totalHistorico.divide(BigDecimal.valueOf(totalDiasHistorico), 2, RoundingMode.HALF_UP);
                    mediaMensalHistorica = mediaDiariaHistorica.multiply(BigDecimal.valueOf(30));
                }

                // Dispara o alerta se a projeção da fatura atual for pelo menos 15% superior à média dinâmica de 6 meses
                BigDecimal margemAlerta = mediaMensalHistorica.multiply(BigDecimal.valueOf(1.15));
                if (fechamentoProjetado.compareTo(margemAlerta) > 0) {
                    boolean jaExiste = insightsExistentes.stream()
                            .anyMatch(ins -> ins.getTipo() == TipoInsight.CARTAO_PREVISAO &&
                                    ins.getMetadados() != null && ins.getMetadados().contains(cartao.getId().toString()));

                    if (!jaExiste) {
                        IaInsight insight = new IaInsight(
                                usuario,
                                TipoInsight.CARTAO_PREVISAO,
                                "Previsão de Fatura Acima do Histórico",
                                "Neste ritmo, a fatura do seu cartão " + cartao.getNome() + " fechará projetada em R$ " + fechamentoProjetado +
                                        ", o que é superior à sua média mensal dos últimos 6 meses (R$ " + mediaMensalHistorica.setScale(2, RoundingMode.HALF_UP) + "). Pise no freio.",
                                "{\"cartaoId\":\"" + cartao.getId() + "\",\"projetado\":" + fechamentoProjetado + ",\"mediaHistorica\":" + mediaMensalHistorica + "}"
                        );
                        iaInsightRepository.save(insight);
                    }
                }
            }

            // ─── RN-11: ALERTA DE ESTOURO DE FATURA / COMPROMETIMENTO DE LIMITE ───
            BigDecimal limite = cartao.getLimite();
            BigDecimal disponivel = cartao.getLimiteDisponivel();
            if (limite.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal comprometido = limite.subtract(disponivel);
                double percentualComprometido = comprometido.divide(limite, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue();

                if (percentualComprometido >= 75.0) {
                    boolean jaExiste = insightsExistentes.stream()
                            .anyMatch(ins -> ins.getTipo() == TipoInsight.ESTOURO_FATURA &&
                                    ins.getMetadados() != null && ins.getMetadados().contains(cartao.getId().toString()));

                    if (!jaExiste) {
                        IaInsight insight = new IaInsight(
                                usuario,
                                TipoInsight.ESTOURO_FATURA,
                                "Alerta de Comprometimento de Limite",
                                "Atenção! Você possui " + percentualComprometido + "% do limite do seu cartão " + cartao.getNome() + " comprometido com compras parceladas.",
                                "{\"cartaoId\":\"" + cartao.getId() + "\",\"comprometidoPercentual\":" + percentualComprometido + "}"
                        );
                        iaInsightRepository.save(insight);
                    }
                }
            }
        }
    }
}
