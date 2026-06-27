package com.financetracker.ia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.repository.CategoriaRepository;
import com.financetracker.ia.domain.*;
import com.financetracker.ia.repository.IaCorrecaoUsuarioRepository;
import com.financetracker.ia.repository.IaDicionarioCategoriaRepository;
import com.financetracker.ia.repository.IaInsightRepository;
import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.model.TipoConta;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.transacao.entity.MetasEconomia;
import com.financetracker.transacao.entity.OrcamentoCategoria;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.repository.MetasEconomiaRepository;
import com.financetracker.transacao.repository.OrcamentoCategoriaRepository;
import com.financetracker.transacao.enums.TipoTransacao;
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
    private final CategoriaRepository categoriaRepository;
    private final ContaRepository contaRepository;
    private final OrcamentoCategoriaRepository orcamentoRepository;
    private final MetasEconomiaRepository metasRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final IaServiceAssinatura iaServiceAssinatura;

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
                     CategoriaRepository categoriaRepository,
                     ContaRepository contaRepository,
                     OrcamentoCategoriaRepository orcamentoRepository,
                     MetasEconomiaRepository metasRepository,
                     ObjectMapper objectMapper,
                     IaServiceAssinatura iaServiceAssinatura) {
        this.iaInsightRepository = iaInsightRepository;
        this.iaDicionarioCategoriaRepository = iaDicionarioCategoriaRepository;
        this.iaCorrecaoUsuarioRepository = iaCorrecaoUsuarioRepository;
        this.transacaoRepository = transacaoRepository;
        this.categoriaRepository = categoriaRepository;
        this.contaRepository = contaRepository;
        this.orcamentoRepository = orcamentoRepository;
        this.metasRepository = metasRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.iaServiceAssinatura = iaServiceAssinatura;
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
                    try {
                iaInsightRepository.save(insight);
            } catch (Exception e) {
                // Race condition: constraint unique impede duplicatas
            }
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
     * Executa RN-02, RN-09 para o usuário (categorização e assinaturas ficam em fluxos separados).
     */
    public void processarInsightsParaUsuario(Usuario usuario) {
        limparInsightsOrfaos(usuario.getId());
        iaServiceAssinatura.processarInsightsAssinaturaParaUsuario(usuario);
        processarTrialHunter(usuario);

        // ── Novos insights comportamentais ──────────────────────────
        processarMicroTransacoes(usuario);
        processarOrcamentoSobraMeta(usuario);
        processarDinheiroDormindo(usuario);
        processarRadarFimSemana(usuario);
        processarQuedaReceita(usuario);
        processarReforcoPositivo(usuario);
        processarAceleradorMetas(usuario);
        processarInflacaoPessoal(usuario);
    }

    // ═══════════════════════════════════════════════════════════════════
    // NOVOS INSIGHTS COMPORTAMENTAIS
    // ═══════════════════════════════════════════════════════════════════

    // ── #1: GASTO FORMIGUINHA ────────────────────────────────────────────────
    //
    // Detecta categorias com alto volume de transações pequenas que somam um
    // valor significativo. O cérebro humano ignora micro-transações, mas elas
    // destroem o orçamento no fim do mês.
    private void processarMicroTransacoes(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);

        List<Categoria> categorias = categoriaRepository.findAllByUsuarioId(usuarioId);
        for (Categoria categoria : categorias) {
            long qtdTransacoes = transacaoRepository.countByCategoriaAndPeriodo(
                    usuarioId, categoria.getId(), inicioMes, hoje);
            if (qtdTransacoes < 8) continue; // Mínimo para ser "formiguinha"

            BigDecimal totalCategoria = transacaoRepository.sumValorByCategoriaAndPeriodoSemFiltroTipo(
                    usuarioId, categoria.getId(), inicioMes, hoje);
            if (totalCategoria.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal valorMedio = totalCategoria.divide(
                    BigDecimal.valueOf(qtdTransacoes), 2, RoundingMode.HALF_UP);

            // Só relevante se o valor médio for pequeno (< R$ 40)
            if (valorMedio.compareTo(BigDecimal.valueOf(40.00)) >= 0) continue;

            boolean jaExiste = insightsExistentes.stream()
                    .anyMatch(ins -> ins.getTipo() == TipoInsight.MICRO_TRANSACOES
                            && ins.getMetadados() != null
                            && ins.getMetadados().contains(categoria.getId().toString()));
            if (jaExiste) continue;

            IaInsight insight = new IaInsight(
                    usuario,
                    TipoInsight.MICRO_TRANSACOES,
                    "Gasto Formiguinha: " + categoria.getNome(),
                    String.format(
                            "Você fez %d compras em \"%s\" este mês, totalizando R$ %.2f. " +
                            "Cada uma parece pequena (média R$ %.2f), mas o acumulado é significativo. " +
                            "Compensaria planejar um limite mensal para essa categoria?",
                            qtdTransacoes, categoria.getNome(), totalCategoria, valorMedio),
                    String.format(
                            "{\"categoriaId\":\"%s\",\"categoria\":\"%s\",\"quantidade\":%d,\"total\":%.2f,\"valorMedio\":%.2f}",
                            categoria.getId(), categoria.getNome(), qtdTransacoes,
                            totalCategoria.doubleValue(), valorMedio.doubleValue())
            );
            try {
                try {
                iaInsightRepository.save(insight);
            } catch (Exception e) {
                // Race condition: constraint unique impede duplicatas
            }
            } catch (Exception e) {
                // Race condition: constraint unique impede duplicatas
            }
        }
    }

    // ── #2: ORÇAMENTO → METAS (Fechando o Ciclo) ─────────────────────────────
    //
    // Quando sobra orçamento no fim do mês, sugere transferir para uma meta de
    // economia ativa. Faz a ponte entre OrcamentoCategoria e MetasEconomia.
    private void processarOrcamentoSobraMeta(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        // Só gera entre dia 25 e último dia do mês (fim de ciclo)
        if (hoje.getDayOfMonth() < 25) return;

        LocalDate inicioMes = hoje.withDayOfMonth(1);
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);
        List<MetasEconomia> metasAtivas = metasRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        if (metasAtivas.isEmpty()) return;

        List<OrcamentoCategoria> orcamentos = orcamentoRepository.findByUsuarioId(usuarioId);
        for (OrcamentoCategoria orcamento : orcamentos) {
            if (orcamento.getLimiteMensal() == null || orcamento.getLimiteMensal().compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal gastoMes = transacaoRepository.sumValorByCategoriaAndPeriodoSemFiltroTipo(
                    usuarioId, orcamento.getCategoria().getId(), inicioMes, hoje);
            if (gastoMes.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal sobra = orcamento.getLimiteMensal().subtract(gastoMes);
            if (sobra.compareTo(BigDecimal.ZERO) <= 0) continue;

            double percentualSobra = sobra.divide(orcamento.getLimiteMensal(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
            if (percentualSobra < 10.0) continue; // Só alerta se sobrou 10%+

            boolean jaExiste = insightsExistentes.stream()
                    .anyMatch(ins -> ins.getTipo() == TipoInsight.ORCAMENTO_SOBRA_META
                            && ins.getMetadados() != null
                            && ins.getMetadados().contains(orcamento.getCategoria().getId().toString()));
            if (jaExiste) continue;

            MetasEconomia meta = metasAtivas.get(0); // Melhor meta para sugerir
            IaInsight insight = new IaInsight(
                    usuario,
                    TipoInsight.ORCAMENTO_SOBRA_META,
                    "Sobra no Orçamento: " + orcamento.getCategoria().getNome(),
                    String.format(
                            "Faltam %d dia(s) para o mês acabar e você economizou R$ %.2f no orçamento " +
                            "de \"%s\" (%.0f%% de sobra). Que tal transferir esse valor para a sua " +
                            "meta \"%s\"? Atualmente você acumulou R$ %.2f de R$ %.2f.",
                            hoje.lengthOfMonth() - hoje.getDayOfMonth(),
                            sobra, orcamento.getCategoria().getNome(), percentualSobra,
                            meta.getNome(), meta.getValorAcumulado(), meta.getValorAlvo()),
                    String.format(
                            "{\"categoriaId\":\"%s\",\"orcamento\":%.2f,\"gasto\":%.2f,\"sobra\":%.2f,\"metaId\":\"%s\",\"metaNome\":\"%s\"}",
                            orcamento.getCategoria().getId(),
                            orcamento.getLimiteMensal().doubleValue(), gastoMes.doubleValue(),
                            sobra.doubleValue(), meta.getId(), meta.getNome())
            );
            try {
                iaInsightRepository.save(insight);
            } catch (Exception e) {
                // Race condition: constraint unique impede duplicatas
            }
        }
    }

    // ── #3: DINHEIRO DORMINDO ────────────────────────────────────────────────
    //
    // Identifica saldos parados em contas correntes sem movimentação, sugerindo
    // otimização de liquidez (investimento).
    private void processarDinheiroDormindo(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);

        List<Conta> contas = contaRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        for (Conta conta : contas) {
            if (conta.getTipo() != TipoConta.CORRENTE) continue;
            if (conta.getSaldo().compareTo(BigDecimal.valueOf(1000)) < 0) continue;

            // Verificar se houve saída nos últimos 15 dias
            LocalDate quinzeDiasAtras = hoje.minusDays(15);
            List<Transacao> transacoesRecentes = transacaoRepository
                    .findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                            usuarioId, quinzeDiasAtras, hoje);
            boolean teveSaida = transacoesRecentes.stream()
                    .anyMatch(t -> t.getContaOrigem() != null
                            && t.getContaOrigem().getId().equals(conta.getId()));

            if (teveSaida) continue;

            boolean jaExiste = insightsExistentes.stream()
                    .anyMatch(ins -> ins.getTipo() == TipoInsight.DINHEIRO_DORMINDO
                            && ins.getMetadados() != null
                            && ins.getMetadados().contains(conta.getId().toString()));
            if (jaExiste) continue;

            IaInsight insight = new IaInsight(
                    usuario,
                    TipoInsight.DINHEIRO_DORMINDO,
                    "Dinheiro Dormindo: " + conta.getNome(),
                    String.format(
                            "Notei que seu saldo de R$ %.2f na \"%s\" está parado há mais de 15 dias " +
                            "sem previsão de uso. Se estivesse em uma conta rendendo 100%% do CDI, " +
                            "você já teria ganho juros. Considere investir o que não vai usar.",
                            conta.getSaldo(), conta.getNome()),
                    String.format(
                            "{\"contaId\":\"%s\",\"conta\":\"%s\",\"saldo\":%.2f,\"diasParado\":15}",
                            conta.getId(), conta.getNome(), conta.getSaldo().doubleValue())
            );
            try {
                iaInsightRepository.save(insight);
            } catch (Exception e) {
                // Race condition: constraint unique impede duplicatas
            }
        }
    }

    // ── #4: RADAR DE FIM DE SEMANA ───────────────────────────────────────────
    //
    // Cruza dias da semana com categorias de gasto para identificar padrões
    // comportamentais (ex: 85% dos gastos com Lazer nos domingos à noite).
    private void processarRadarFimSemana(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);

        List<Categoria> categorias = categoriaRepository.findAllByUsuarioId(usuarioId);
        String[] diasSemana = {"Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado"};

        for (Categoria categoria : categorias) {
            List<Transacao> transacoes = transacaoRepository
                    .findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                            usuarioId, inicioMes, hoje)
                    .stream()
                    .filter(t -> t.getCategoria() != null && t.getCategoria().getId().equals(categoria.getId()))
                    .filter(t -> t.getTipo() == TipoTransacao.COMPRA_CREDITO
                            || t.getTipo() == TipoTransacao.PIX
                            || t.getTipo() == TipoTransacao.SAQUE)
                    .collect(Collectors.toList());

            if (transacoes.size() < 5) continue; // Precisa de dados suficientes

            // Agrupar por dia da semana
            Map<Integer, Long> porDiaSemana = transacoes.stream()
                    .collect(Collectors.groupingBy(
                            t -> t.getData().getDayOfWeek().getValue() % 7, // 0=Dom, 1=Seg...
                            Collectors.counting()));

            long totalTransacoes = transacoes.size();
            for (Map.Entry<Integer, Long> entry : porDiaSemana.entrySet()) {
                double percentual = (entry.getValue() * 100.0) / totalTransacoes;
                if (percentual < 70.0) continue; // Só alerta se 70%+ no mesmo dia

                boolean jaExiste = insightsExistentes.stream()
                        .anyMatch(ins -> ins.getTipo() == TipoInsight.RADAR_FIM_SEMANA
                                && ins.getMetadados() != null
                                && ins.getMetadados().contains(categoria.getId().toString()));
                if (jaExiste) continue;

                String nomeDia = diasSemana[entry.getKey()];
                IaInsight insight = new IaInsight(
                        usuario,
                        TipoInsight.RADAR_FIM_SEMANA,
                        "Radar de Padrão: " + categoria.getNome(),
                        String.format(
                                "Alerta de padrão: %.0f%% dos seus gastos com \"%s\" acontecem nas %ss. " +
                                "Isso pode indicar um comportamento emocional recorrente. " +
                                "Que tal planejar alternativas mais econômicas para os próximos %ss?",
                                percentual, categoria.getNome(), nomeDia, nomeDia.toLowerCase()),
                        String.format(
                                "{\"categoriaId\":\"%s\",\"diaSemana\":\"%s\",\"percentual\":%.0f,\"totalNoDia\":%d}",
                                categoria.getId(), nomeDia, percentual, entry.getValue())
                );
                try {
                iaInsightRepository.save(insight);
            } catch (Exception e) {
                // Race condition: constraint unique impede duplicatas
            }
            }
        }
    }

    // ── #5: QUEDA DE RECEITA ─────────────────────────────────────────────────
    //
    // Monitora entradas (DEPOSITO, PIX, TRANSFERENCIA) e alerta quando a
    // receita do mês atual cai significativamente em relação ao mês anterior.
    private void processarQuedaReceita(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMesAtual = hoje.withDayOfMonth(1);
        LocalDate inicioMesAnterior = inicioMesAtual.minusMonths(1);
        LocalDate fimMesAnterior = inicioMesAtual.minusDays(1);
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);

        List<TipoTransacao> tiposEntrada = List.of(TipoTransacao.DEPOSITO, TipoTransacao.PIX, TipoTransacao.TRANSFERENCIA);

        BigDecimal receitaMesAtual = transacaoRepository.sumValorByCategoriaAndPeriodoSemFiltroTipo(
                usuarioId, null, inicioMesAtual, hoje);
        BigDecimal receitaMesAnterior = transacaoRepository.sumValorByCategoriaAndPeriodoSemFiltroTipo(
                usuarioId, null, inicioMesAnterior, fimMesAnterior);

        // Usar a query geral para receita (sem filtro de categoria)
        // Precisamos de uma query específica — usar a query existente de soma total
        // Mas sumValorByCategoriaAndPeriodoSemFiltroTipo espera categoriaId, então vamos
        // calcular diretamente via transacoes
        List<Transacao> transacoesMesAtual = transacaoRepository
                .findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                        usuarioId, inicioMesAtual, hoje);
        List<Transacao> transacoesMesAnterior = transacaoRepository
                .findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                        usuarioId, inicioMesAnterior, fimMesAnterior);

        BigDecimal receitaAtual = transacoesMesAtual.stream()
                .filter(t -> tiposEntrada.contains(t.getTipo()))
                .map(Transacao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal receitaAnterior = transacoesMesAnterior.stream()
                .filter(t -> tiposEntrada.contains(t.getTipo()))
                .map(Transacao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (receitaAnterior.compareTo(BigDecimal.ZERO) <= 0) return; // Sem referência
        if (receitaAtual.compareTo(BigDecimal.ZERO) >= receitaAnterior.multiply(BigDecimal.valueOf(0.70)).longValue()) {
            // Receita caiu menos de 30% — não alertar
            return;
        }

        double quedaPercentual = receitaAnterior.subtract(receitaAtual)
                .divide(receitaAnterior, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();

        boolean jaExiste = insightsExistentes.stream()
                .anyMatch(ins -> ins.getTipo() == TipoInsight.QUEDA_RECEITA);
        if (jaExiste) return;

        IaInsight insight = new IaInsight(
                usuario,
                TipoInsight.QUEDA_RECEITA,
                "Alerta de Queda de Receita",
                String.format(
                        "Sua receita este mês caiu %.0f%% em relação ao mês anterior " +
                        "(de R$ %.2f para R$ %.2f). Se o custo de vida se manteve igual, " +
                        "atenção ao fluxo de caixa para a próxima quinzena.",
                        quedaPercentual, receitaAnterior, receitaAtual),
                String.format(
                        "{\"receitaAtual\":%.2f,\"receitaAnterior\":%.2f,\"quedaPercentual\":%.1f}",
                        receitaAtual.doubleValue(), receitaAnterior.doubleValue(), quedaPercentual)
        );
        iaInsightRepository.save(insight);
    }

    // ── #6: REFORÇO POSITIVO (Gamificação) ────────────────────────────────────
    //
    // O app não pode só dar bronca — precisa celebrar vitórias. Verifica 3 meses
    // consecutivos de receita > despesa ou redução de gastos em categorias.
    private void processarReforcoPositivo(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);

        // Verificar 3 meses consecutivos: receita > despesa
        boolean todosPositivos = true;
        List<String> mesesStatus = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            LocalDate inicioMes = hoje.minusMonths(i).withDayOfMonth(1);
            LocalDate fimMes = hoje.minusMonths(i).withDayOfMonth(hoje.minusMonths(i).lengthOfMonth());

            List<Transacao> transacoesMes = transacaoRepository
                    .findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                            usuarioId, inicioMes, fimMes);

            List<TipoTransacao> tiposEntrada = List.of(TipoTransacao.DEPOSITO, TipoTransacao.PIX, TipoTransacao.TRANSFERENCIA);
            BigDecimal receita = transacoesMes.stream()
                    .filter(t -> tiposEntrada.contains(t.getTipo()))
                    .map(Transacao::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal despesa = transacoesMes.stream()
                    .filter(t -> !tiposEntrada.contains(t.getTipo()))
                    .map(Transacao::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String nomeMes = inicioMes.getMonth().getDisplayName(
                    java.time.format.TextStyle.FULL, new Locale("pt", "BR"));
            mesesStatus.add(String.format("%s: R$ %.2f vs R$ %.2f", nomeMes, receita, despesa));

            if (receita.compareTo(despesa) <= 0) {
                todosPositivos = false;
                break;
            }
        }

        if (todosPositivos) {
            boolean jaExiste = insightsExistentes.stream()
                    .anyMatch(ins -> ins.getTipo() == TipoInsight.REFORCO_POSITIVO
                            && ins.getMetadados() != null && ins.getMetadados().contains("streak"));
            if (!jaExiste) {
                IaInsight insight = new IaInsight(
                        usuario,
                        TipoInsight.REFORCO_POSITIVO,
                        "Parabéns! Saúde Financeira Excelente!",
                        String.format(
                                "🎉 É o 3º mês consecutivo que você gasta menos do que ganha! " +
                                "Sua saúde financeira está excelente. Continue assim!\n%s",
                                String.join(" | ", mesesStatus)),
                        "{\"tipoReforco\":\"streak\",\"mesesConsecutivos\":3}"
                );
                try {
                iaInsightRepository.save(insight);
            } catch (Exception e) {
                // Race condition: constraint unique impede duplicatas
            }
            }
        }
    }

    // ── #7: ACELERADOR DE METAS ──────────────────────────────────────────────
    //
    // Traduz o gasto em tempo perdido para alcançar uma meta. O utilizador deixa
    // de ver o gasto como "apenas R$ X" e passa a vê-lo como obstáculo ao sonho.
    private void processarAceleradorMetas(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);

        List<MetasEconomia> metasAtivas = metasRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        if (metasAtivas.isEmpty()) return;

        List<Categoria> categorias = categoriaRepository.findAllByUsuarioId(usuarioId);
        for (Categoria categoria : categorias) {
            BigDecimal gastoMes = transacaoRepository.sumValorByCategoriaAndPeriodoSemFiltroTipo(
                    usuarioId, categoria.getId(), inicioMes, hoje);
            if (gastoMes.compareTo(BigDecimal.valueOf(30.00)) < 0) continue; // Mínimo relevante

            for (MetasEconomia meta : metasAtivas) {
                BigDecimal restante = meta.getValorAlvo().subtract(meta.getValorAcumulado());
                if (restante.compareTo(BigDecimal.ZERO) <= 0) continue;

                // Calcular quantos meses esse gasto atrasa a meta (gasto mensal / restante)
                double mesesAtrasados = gastoMes.compareTo(BigDecimal.ZERO) > 0
                        ? restante.divide(gastoMes, 2, RoundingMode.HALF_UP).doubleValue()
                        : 0;
                if (mesesAtrasados < 0.5) continue; // Só se atrasar 0.5+ mês

                boolean jaExiste = insightsExistentes.stream()
                        .anyMatch(ins -> ins.getTipo() == TipoInsight.ACELERADOR_METAS
                                && ins.getMetadados() != null
                                && ins.getMetadados().contains(meta.getId().toString())
                                && ins.getMetadados().contains(categoria.getId().toString()));
                if (jaExiste) continue;

                IaInsight insight = new IaInsight(
                        usuario,
                        TipoInsight.ACELERADOR_METAS,
                        "Custo de Oportunidade: " + categoria.getNome(),
                        String.format(
                                "Sabia que os R$ %.2f gastos em \"%s\" este mês poderiam " +
                                "antecipar a sua meta \"%s\" em %.1f meses? " +
                                "Considere reduzir nessa categoria para alcançar seu objetivo mais rápido.",
                                gastoMes, categoria.getNome(), meta.getNome(), mesesAtrasados),
                        String.format(
                                "{\"metaId\":\"%s\",\"metaNome\":\"%s\",\"gastoCategoria\":%.2f,\"categoria\":\"%s\",\"mesesAtrasados\":%.1f}",
                                meta.getId(), meta.getNome(), gastoMes.doubleValue(),
                                categoria.getNome(), mesesAtrasados)
                );
                try {
                iaInsightRepository.save(insight);
            } catch (Exception e) {
                // Race condition: constraint unique impede duplicatas
            }
            }
        }
    }

    // ── #8: INFLAÇÃO PESSOAL (Detetive de Estilo de Vida) ────────────────────
    //
    // Analisa o custo médio por categoria ao longo dos meses. Se o utilizador
    // continua indo ao mesmo lugar, mas a conta total subiu, alerta.
    private void processarInflacaoPessoal(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);

        // Mês atual vs mês anterior
        LocalDate inicioMesAtual = hoje.withDayOfMonth(1);
        LocalDate fimMesAtual = hoje;
        LocalDate inicioMesAnterior = inicioMesAtual.minusMonths(1);
        LocalDate fimMesAnterior = inicioMesAtual.minusDays(1);

        List<Categoria> categorias = categoriaRepository.findAllByUsuarioId(usuarioId);
        for (Categoria categoria : categorias) {
            // Média mensal dos 2 meses anteriores
            BigDecimal gastoMesAnterior = transacaoRepository.sumValorByCategoriaAndPeriodoSemFiltroTipo(
                    usuarioId, categoria.getId(), inicioMesAnterior, fimMesAnterior);
            if (gastoMesAnterior.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Média do mês atual (projetada para 30 dias se ainda não acabou)
            BigDecimal gastoMesAtual = transacaoRepository.sumValorByCategoriaAndPeriodoSemFiltroTipo(
                    usuarioId, categoria.getId(), inicioMesAtual, fimMesAtual);

            // Projetar para o mês inteiro
            long diasPassados = ChronoUnit.DAYS.between(inicioMesAtual, hoje) + 1;
            if (diasPassados < 5) continue; // Dados insuficientes no início do mês
            BigDecimal projetado = gastoMesAtual
                    .divide(BigDecimal.valueOf(diasPassados), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(hoje.lengthOfMonth()))
                    .setScale(2, RoundingMode.HALF_UP);

            double variacaoPercentual = gastoMesAnterior.compareTo(BigDecimal.ZERO) > 0
                    ? projetado.subtract(gastoMesAnterior)
                            .divide(gastoMesAnterior, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0;

            if (variacaoPercentual < 15.0) continue; // Só alerta se subiu 15%+

            boolean jaExiste = insightsExistentes.stream()
                    .anyMatch(ins -> ins.getTipo() == TipoInsight.INFLACAO_PESSOAL
                            && ins.getMetadados() != null
                            && ins.getMetadados().contains(categoria.getId().toString()));
            if (jaExiste) continue;

            IaInsight insight = new IaInsight(
                    usuario,
                    TipoInsight.INFLACAO_PESSOAL,
                    "Inflação Pessoal: " + categoria.getNome(),
                    String.format(
                            "O seu gasto em \"%s\" aumentou %.0f%% neste mês em relação ao anterior " +
                            "(de R$ %.2f para R$ %.2f projetado). " +
                            "Atenção a preços que possam estar encarecendo o seu carrinho!",
                            categoria.getNome(), variacaoPercentual,
                            gastoMesAnterior, projetado),
                    String.format(
                            "{\"categoriaId\":\"%s\",\"gastoMesAtual\":%.2f,\"gastoMesAnterior\":%.2f,\"variacaoPercentual\":%.1f}",
                            categoria.getId(), projetado.doubleValue(),
                            gastoMesAnterior.doubleValue(), variacaoPercentual)
            );
            try {
                iaInsightRepository.save(insight);
            } catch (Exception e) {
                // Race condition: constraint unique impede duplicatas
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
                        try {
                iaInsightRepository.save(insight);
            } catch (Exception e) {
                // Race condition: constraint unique impede duplicatas
            }
                    }
                }
            }
        }
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

}
