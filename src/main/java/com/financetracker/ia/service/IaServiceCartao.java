package com.financetracker.ia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.assinatura.entity.Assinatura;
import com.financetracker.assinatura.repository.AssinaturaRepository;
import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.ia.domain.TipoInsight;
import com.financetracker.ia.domain.IaInsight;
import com.financetracker.ia.dto.ProjecaoCartaoDTO;
import com.financetracker.ia.dto.ProjecaoCartoesResponse;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service dedicado a operações de cartão de crédito:
 * - Projeção de faturas (endpoint /api/ia/projecao-cartoes)
 * - Aviso de fechamento iminente (endpoint /api/ia/aviso-fechamento)
 */
@Service
public class IaServiceCartao {

    private final IaInsightRepository iaInsightRepository;
    private final CartaoRepository cartaoRepository;
    private final FaturaRepository faturaRepository;
    private final TransacaoRepository transacaoRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${OPENAI_BASE_URL:https://openrouter.ai/api/v1}")
    private String openAiBaseUrl;

    @Value("${OPENAI_API_KEY:}")
    private String openAiApiKey;

    @Value("${OPENAI_MODEL:deepseek/deepseek-v4-flash}")
    private String openAiModel;

    private static final Set<StatusFatura> STATUS_FECHADOS = Set.of(
            StatusFatura.FECHADA, StatusFatura.PAGA, StatusFatura.PAGA_PARCIAL, StatusFatura.ATRASADA
    );

    public IaServiceCartao(IaInsightRepository iaInsightRepository,
                           CartaoRepository cartaoRepository,
                           FaturaRepository faturaRepository,
                           TransacaoRepository transacaoRepository,
                           AssinaturaRepository assinaturaRepository,
                           ObjectMapper objectMapper) {
        this.iaInsightRepository = iaInsightRepository;
        this.cartaoRepository = cartaoRepository;
        this.faturaRepository = faturaRepository;
        this.transacaoRepository = transacaoRepository;
        this.assinaturaRepository = assinaturaRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROJEÇÃO DE FATURAS (endpoint dedicado /api/ia/projecao-cartoes)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Endpoint dedicado: retorna projeção/comparação para TODOS os cartões ativos do usuário.
     * - Fatura ABERTA: projeção de fechamento (via IA ou extrapolation linear)
     * - Fatura FECHADA: valor real vs média histórica
     * Chamado: page load /cartoes, nova transação, botão "Analisar com IA"
     */
    @SuppressWarnings("unchecked")
    public ProjecaoCartoesResponse projetarFaturasParaUsuario(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        List<Cartao> cartoes = cartaoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);

        List<ProjecaoCartaoDTO> projecoes = new ArrayList<>();
        List<Map<String, Object>> dadosParaIa = new ArrayList<>();

        for (Cartao cartao : cartoes) {
            // ── 1. HISTÓRICO VARIÁVEL (6 meses): gastos variáveis (exclui parcelas 2/3+ e assinaturas) ──
            LocalDate seisMesesAtras = hoje.minusMonths(6).withDayOfMonth(1);
            BigDecimal somaVariavelHistorica = transacaoRepository.sumGastosVariaveisPorCartao(
                    usuarioId, cartao.getId(), seisMesesAtras, inicioMes.minusDays(1));

            // ── 2. HISTÓRICO TOTAL (6 meses): valorTotal das faturas fechadas ──
            List<Fatura> faturasHistoricas = faturaRepository
                    .findByCartaoIdAndUsuarioIdOrderByMesReferenciaDesc(cartao.getId(), usuarioId)
                    .stream()
                    .filter(f -> STATUS_FECHADOS.contains(f.getStatus()))
                    .filter(f -> !f.getMesReferencia().isBefore(seisMesesAtras))
                    .filter(f -> f.getMesReferencia().isBefore(inicioMes))
                    .collect(Collectors.toList());

            BigDecimal mediaTotalHistorica = null;
            BigDecimal mediaVariavelHistorica = null;
            int mesesHistorico = faturasHistoricas.size();
            if (mesesHistorico >= 2) {
                // Média total (para classificação — é o que o usuário paga)
                BigDecimal totalHistorico = faturasHistoricas.stream()
                        .map(Fatura::getValorTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                mediaTotalHistorica = totalHistorico
                        .divide(BigDecimal.valueOf(mesesHistorico), 2, RoundingMode.HALF_UP);

                // Média variável (para extrapolação — exclui assinaturas e parcelas 2/3+)
                mediaVariavelHistorica = somaVariavelHistorica
                        .divide(BigDecimal.valueOf(mesesHistorico), 2, RoundingMode.HALF_UP);
            }

            // ── 3. GASTO VARIÁVEL ACUMULADO (mês atual até hoje) ──
            BigDecimal valorAtualVariavel = transacaoRepository.sumGastosVariaveisPorCartao(
                    usuarioId, cartao.getId(), inicioMes, hoje);

            // ── 4. ASSINATURAS PENDENTES (dataProximaCobranca no mês atual) ──
            LocalDate fimMes = hoje.withDayOfMonth(hoje.lengthOfMonth());
            List<Assinatura> assinaturasPendentes = assinaturaRepository
                    .findByCartaoIdAndAtivoTrueAndDataProximaCobrancaBetween(
                            cartao.getId(), inicioMes, fimMes);
            BigDecimal totalAssinaturasPendentes = assinaturasPendentes.stream()
                    .map(Assinatura::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // ── 5. ASSINATURAS JÁ COBRADAS (transações "Assinatura: %" no mês) ──
            BigDecimal totalAssinaturasCobradas = transacaoRepository.sumAssinaturasCobradasNoMes(
                    usuarioId, cartao.getId(), inicioMes, hoje);

            // ── 6. Verificar se fatura atual está ABERTA ou FECHADA ──
            Optional<Fatura> faturaAtualOpt = faturaRepository
                    .findByCartaoIdAndUsuarioIdAndMesReferencia(cartao.getId(), usuarioId, inicioMes);

            boolean faturaFechada = faturaAtualOpt
                    .map(f -> STATUS_FECHADOS.contains(f.getStatus()))
                    .orElse(false);

            long diasPassados = ChronoUnit.DAYS.between(inicioMes, hoje) + 1;

            // ── 7. Montar DTO conforme o status ──
            if (faturaFechada && faturaAtualOpt.isPresent()) {
                // ── FATURA FECHADA: usar valor real ──
                BigDecimal valorReal = faturaAtualOpt.get().getValorTotal();
                BigDecimal desvioPercentual = null;
                String classificacao;
                String mensagem;

                if (mediaTotalHistorica != null && mediaTotalHistorica.compareTo(BigDecimal.ZERO) > 0) {
                    desvioPercentual = valorReal.subtract(mediaTotalHistorica)
                            .divide(mediaTotalHistorica, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(1, RoundingMode.HALF_UP);

                    double desvio = desvioPercentual.doubleValue();
                    if (desvio > 10.0) {
                        classificacao = "ACIMA";
                        mensagem = String.format(
                                "A fatura do %s fechou em %s — %d%% acima da sua média dos últimos %d meses (%s).",
                                cartao.getNome(), formatarValor(valorReal),
                                Math.round(desvio), mesesHistorico, formatarValor(mediaTotalHistorica));
                    } else if (desvio < -10.0) {
                        classificacao = "ABAIXO";
                        mensagem = String.format(
                                "A fatura do %s fechou em %s — %d%% abaixo da sua média dos últimos %d meses (%s). Excelente!",
                                cartao.getNome(), formatarValor(valorReal),
                                Math.abs(Math.round(desvio)), mesesHistorico, formatarValor(mediaTotalHistorica));
                    } else {
                        classificacao = "DENTRO";
                        mensagem = String.format(
                                "A fatura do %s fechou em %s, dentro da faixa esperada (média: %s).",
                                cartao.getNome(), formatarValor(valorReal), formatarValor(mediaTotalHistorica));
                    }
                } else {
                    classificacao = "SEM_DADOS";
                    mensagem = String.format(
                            "A fatura do %s fechou em %s. Histórico insuficiente para comparação.",
                            cartao.getNome(), formatarValor(valorReal));
                }

                projecoes.add(new ProjecaoCartaoDTO(
                        cartao.getId(), cartao.getNome(), cartao.getCorHexadecimal(),
                        "FECHADA", valorAtualVariavel, valorReal, false,
                        valorReal, mediaTotalHistorica, mesesHistorico,
                        desvioPercentual, classificacao, mensagem,
                        (int) hoje.lengthOfMonth(), (int) diasPassados
                ));

            } else {
                // ── FATURA ABERTA ou SEM FATURA: Projeção Híbrida ──
                String statusFatura = faturaAtualOpt.isPresent() ? "ABERTA" : "SEM_FATURA";

                if (diasPassados >= 3 && (valorAtualVariavel.compareTo(BigDecimal.ZERO) > 0
                        || totalAssinaturasCobradas.compareTo(BigDecimal.ZERO) > 0
                        || totalAssinaturasPendentes.compareTo(BigDecimal.ZERO) > 0)) {

                    // ── PROJEÇÃO HÍBRIDA ──
                    // Extrapolação: média variável × dias restantes
                    long diasNoMes = hoje.lengthOfMonth();
                    long diasRestantes = diasNoMes - diasPassados;

                    BigDecimal gastoFuturoVariavel = BigDecimal.ZERO;
                    if (mediaVariavelHistorica != null && mediaVariavelHistorica.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal mediaDiariaVariavel = mediaVariavelHistorica
                                .divide(BigDecimal.valueOf(diasNoMes), 4, RoundingMode.HALF_UP);
                        gastoFuturoVariavel = mediaDiariaVariavel
                                .multiply(BigDecimal.valueOf(diasRestantes))
                                .setScale(2, RoundingMode.HALF_UP);
                    }

                    // Projeção = variável acumulado + variável futuro + assinaturas pendentes + assinaturas cobradas
                    BigDecimal projecaoHibrida = valorAtualVariavel
                            .add(gastoFuturoVariavel)
                            .add(totalAssinaturasPendentes)
                            .add(totalAssinaturasCobradas);

                    // ── CLASSIFICAÇÃO (usa média total — é o que o usuário paga) ──
                    BigDecimal desvioPercentual = null;
                    String classificacao = "PRIMEIRO_MES";
                    String mensagem = String.format(
                            "Projeção inicial para %s: %s (apenas %d dias de dados).",
                            cartao.getNome(), formatarValor(projecaoHibrida), diasPassados);

                    if (mediaTotalHistorica != null && mediaTotalHistorica.compareTo(BigDecimal.ZERO) > 0
                            && projecaoHibrida.compareTo(BigDecimal.ZERO) > 0) {
                        desvioPercentual = projecaoHibrida.subtract(mediaTotalHistorica)
                                .divide(mediaTotalHistorica, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(1, RoundingMode.HALF_UP);
                        double desvio = desvioPercentual.doubleValue();
                        if (desvio > 10.0) {
                            classificacao = "ACIMA";
                        } else if (desvio < -10.0) {
                            classificacao = "ABAIXO";
                        } else {
                            classificacao = "DENTRO";
                        }
                        mensagem = String.format(
                                "Neste ritmo, a fatura do %s fechará em %s — %s%d%% vs média de %s.",
                                cartao.getNome(), formatarValor(projecaoHibrida),
                                desvio > 0 ? "" : "",
                                Math.abs(Math.round(desvio)), formatarValor(mediaTotalHistorica));
                    }

                    // Montar dados para IA (extrapolação já calculada, mas IA pode refinar)
                    Map<String, BigDecimal> topCategorias = transacaoRepository
                            .findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(usuarioId, inicioMes, hoje)
                            .stream()
                            .filter(t -> t.getCartao() != null && t.getCartao().getId().equals(cartao.getId()))
                            .filter(t -> t.getTipo() == TipoTransacao.COMPRA_CREDITO)
                            .filter(t -> t.getNumeroParcela() == null || t.getNumeroParcela() == 1)
                            .filter(t -> t.getCategoria() != null)
                            .collect(Collectors.groupingBy(
                                    t -> t.getCategoria().getNome(),
                                    Collectors.reducing(BigDecimal.ZERO, Transacao::getValor, BigDecimal::add)
                            ));

                    String topCategoriasStr = topCategorias.entrySet().stream()
                            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                            .limit(5)
                            .map(e -> e.getKey() + " " + formatarValor(e.getValue()))
                            .collect(Collectors.joining(", "));

                    Map<String, Object> dadoCartao = new HashMap<>();
                    dadoCartao.put("cartaoId", cartao.getId());
                    dadoCartao.put("cartaoNome", cartao.getNome());
                    dadoCartao.put("corHexadecimal", cartao.getCorHexadecimal() != null ? cartao.getCorHexadecimal() : "");
                    dadoCartao.put("mediaHistorica", mediaTotalHistorica != null ? mediaTotalHistorica : BigDecimal.ZERO);
                    dadoCartao.put("gastoMes", valorAtualVariavel.add(totalAssinaturasCobradas));
                    dadoCartao.put("diasPassados", diasPassados);
                    dadoCartao.put("diasNoMes", diasNoMes);
                    dadoCartao.put("projecaoExtrapolada", projecaoHibrida);
                    dadoCartao.put("topCategoriasStr", topCategoriasStr);
                    dadoCartao.put("statusFatura", statusFatura);
                    dadoCartao.put("mesesHistorico", (long) mesesHistorico);
                    dadosParaIa.add(dadoCartao);

                    projecoes.add(new ProjecaoCartaoDTO(
                            cartao.getId(), cartao.getNome(), cartao.getCorHexadecimal(),
                            statusFatura, valorAtualVariavel, projecaoHibrida, false,
                            null, mediaTotalHistorica, mesesHistorico,
                            desvioPercentual, classificacao, mensagem,
                            (int) hoje.lengthOfMonth(), (int) diasPassados
                    ));

                } else {
                    String classificacao = mesesHistorico == 0 ? "NOVO" : "SEM_DADOS";
                    String mensagem = mesesHistorico == 0
                            ? String.format("Cartão %s sem histórico de faturas fechadas.", cartao.getNome())
                            : String.format("Sem gastos registrados este mês para %s.", cartao.getNome());

                    projecoes.add(new ProjecaoCartaoDTO(
                            cartao.getId(), cartao.getNome(), cartao.getCorHexadecimal(),
                            statusFatura, valorAtualVariavel, BigDecimal.ZERO, false,
                            null, mediaTotalHistorica, mesesHistorico,
                            null, classificacao, mensagem,
                            (int) hoje.lengthOfMonth(), (int) diasPassados
                    ));
                }
            }
        }

        // 8. Chamar IA uma única vez para todos os cartões ABERTOS com dados suficientes
        boolean dadosInsuficientes = false;
        if (!dadosParaIa.isEmpty()) {
            try {
                Map<UUID, BigDecimal> projecoesIa = chamarIaParaProjecao(dadosParaIa);

                for (int i = 0; i < projecoes.size(); i++) {
                    ProjecaoCartaoDTO p = projecoes.get(i);
                    if (projecoesIa.containsKey(p.cartaoId())) {
                        BigDecimal valorIa = projecoesIa.get(p.cartaoId());
                        BigDecimal desvio = null;
                        String classificacao = p.classificacao();
                        String mensagem = p.mensagemResumo();

                        // CLASSIFICAÇÃO usa média total (é o que o usuário paga)
                        if (p.mediaHistorica() != null && p.mediaHistorica().compareTo(BigDecimal.ZERO) > 0) {
                            desvio = valorIa.subtract(p.mediaHistorica())
                                    .divide(p.mediaHistorica(), 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(1, RoundingMode.HALF_UP);
                            double desvioDbl = desvio.doubleValue();
                            if (desvioDbl > 10.0) {
                                classificacao = "ACIMA";
                                mensagem = String.format(
                                        "Neste ritmo, a fatura do %s fechará em %s — %d%% acima da sua média dos últimos %d meses (%s).",
                                        p.cartaoNome(), formatarValor(valorIa),
                                        Math.abs(Math.round(desvioDbl)), p.mesesHistorico(), formatarValor(p.mediaHistorica()));
                            } else if (desvioDbl < -10.0) {
                                classificacao = "ABAIXO";
                                mensagem = String.format(
                                        "Neste ritmo, a fatura do %s fechará em %s — %d%% abaixo da sua média dos últimos %d meses (%s).",
                                        p.cartaoNome(), formatarValor(valorIa),
                                        Math.abs(Math.round(desvioDbl)), p.mesesHistorico(), formatarValor(p.mediaHistorica()));
                            } else {
                                classificacao = "DENTRO";
                                mensagem = String.format(
                                        "Neste ritmo, a fatura do %s fechará em %s, dentro da faixa esperada (média: %s).",
                                        p.cartaoNome(), formatarValor(valorIa), formatarValor(p.mediaHistorica()));
                            }
                        }

                        projecoes.set(i, new ProjecaoCartaoDTO(
                                p.cartaoId(), p.cartaoNome(), p.corHexadecimal(),
                                p.statusFatura(), p.valorAtualNoMes(), valorIa, true,
                                p.valorRealFechado(), p.mediaHistorica(), p.mesesHistorico(),
                                desvio, classificacao, mensagem,
                                p.diasNoMes(), p.diasPassados()
                        ));
                    }
                }
            } catch (Exception e) {
                dadosInsuficientes = true;
            }
        }

        return new ProjecaoCartoesResponse(projecoes, cartoes.size(), dadosInsuficientes);
    }

    /**
     * Envia os dados agregados de TODOS os cartões abertos para a IA em uma única chamada.
     * Retorna Map<cartaoId, valorProjetado>.
     */
    @SuppressWarnings("unchecked")
    private Map<UUID, BigDecimal> chamarIaParaProjecao(List<Map<String, Object>> dadosParaIa) throws Exception {
        if (openAiApiKey == null || openAiApiKey.trim().isEmpty()) {
            throw new IllegalStateException("OpenAI API Key não configurada.");
        }

        StringBuilder userContent = new StringBuilder();
        userContent.append("Dados para projeção de faturas:\n\n");

        for (Map<String, Object> dado : dadosParaIa) {
            UUID cartaoId = (UUID) dado.get("cartaoId");
            String cartaoNome = (String) dado.get("cartaoNome");
            BigDecimal mediaHist = (BigDecimal) dado.get("mediaHistorica");
            BigDecimal gastoMes = (BigDecimal) dado.get("gastoMes");
            long diasPassados = (long) dado.get("diasPassados");
            long diasNoMes = (long) dado.get("diasNoMes");
            BigDecimal projExtrapolada = (BigDecimal) dado.get("projecaoExtrapolada");
            String topCategorias = (String) dado.get("topCategoriasStr");

            userContent.append(String.format(
                    "Cartão '%s' (ID: %s):\n" +
                    "- Média histórica 6 meses: %s\n" +
                    "- Gasto acumulado no mês: %s\n" +
                    "- Dias passados: %d de %d\n" +
                    "- Projeção por extrapolation linear: %s\n" +
                    "- Top categorias: %s\n\n",
                    cartaoNome, cartaoId,
                    formatarValor(mediaHist), formatarValor(gastoMes),
                    diasPassados, diasNoMes,
                    formatarValor(projExtrapolada), topCategorias
            ));
        }

        userContent.append("Retorne APENAS um JSON no formato: " +
                "{\"projecoes\":[{\"cartaoId\":\"<uuid>\",\"valor\":<numero>}]}\n" +
                "Use o valor projetado de fechamento realista, considerando padrões de gastos " +
                "e a média histórica. Não apenas extrapolação linear — ajuste se os gastos " +
                "estiverem concentrados em categorias variáveis vs fixas.");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + openAiApiKey);

        Map<String, Object> messageSystem = new HashMap<>();
        messageSystem.put("role", "system");
        messageSystem.put("content",
                "Você é um assistente financeiro previsor de gastos com cartão de crédito. " +
                "Analise os dados de cada cartão fornecidos e retorne APENAS um JSON válido com " +
                "os valores projetados de fechamento de fatura. Considere a média histórica, " +
                "o ritmo atual de gastos, e a natureza das categorias (gastos fixos vs variáveis). " +
                "IMPORTANTE: Retorne APENAS o JSON, sem texto adicional, sem markdown.");

        Map<String, Object> messageUser = new HashMap<>();
        messageUser.put("role", "user");
        messageUser.put("content", userContent.toString());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openAiModel);
        requestBody.put("messages", Arrays.asList(messageSystem, messageUser));
        requestBody.put("temperature", 0.1);
        requestBody.put("max_tokens", 300);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                openAiBaseUrl + "/chat/completions", entity, String.class);

        Map<UUID, BigDecimal> resultado = new HashMap<>();

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<?, ?> responseMap = objectMapper.readValue(response.getBody(), Map.class);
            List<?> choices = (List<?>) responseMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                Map<?, ?> message = (Map<?, ?>) choice.get("message");
                String content = ((String) message.get("content")).trim();
                content = content.replaceAll("```json", "").replaceAll("```", "").trim();

                Map<?, ?> parsed = objectMapper.readValue(content, Map.class);
                List<?> projecoesLista = (List<?>) parsed.get("projecoes");
                if (projecoesLista != null) {
                    for (Object item : projecoesLista) {
                        Map<?, ?> proj = (Map<?, ?>) item;
                        UUID id = UUID.fromString((String) proj.get("cartaoId"));
                        BigDecimal valor = new BigDecimal(proj.get("valor").toString());
                        resultado.put(id, valor);
                    }
                }
            }
        }

        return resultado;
    }

    // ═══════════════════════════════════════════════════════════════════
    // AVISO DE FECHAMENTO IMINENTE (endpoint dedicado)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Endpoint dedicado: verifica todos os cartões ativos e gera alertas
     * de fechamento iminente quando a fatura fecha em 1-5 dias.
     * Chamado ao entrar na tela /cartoes.
     */
    public void processarAvisoFechamentoParaUsuario(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        List<Cartao> cartoes = cartaoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);

        // Carrega todos os AVISO_FECHAMENTO não lidos para upsert eficiente
        List<IaInsight> avisosExistentes = iaInsightRepository
                .findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId)
                .stream()
                .filter(ins -> ins.getTipo() == TipoInsight.AVISO_FECHAMENTO)
                .collect(Collectors.toList());

        for (Cartao cartao : cartoes) {
            int diaFechamento = cartao.getDiaFechamento();
            int diaFechamentoEfetivo = Math.min(diaFechamento, hoje.lengthOfMonth());
            LocalDate dataFechamentoNoMes = hoje.withDayOfMonth(diaFechamentoEfetivo);

            LocalDate dataFechamentoAlerta = dataFechamentoNoMes;
            if (hoje.isAfter(dataFechamentoNoMes)) {
                LocalDate proximoMes = hoje.plusMonths(1);
                int diaFechamentoEfetivoProximo = Math.min(diaFechamento, proximoMes.lengthOfMonth());
                dataFechamentoAlerta = proximoMes.withDayOfMonth(diaFechamentoEfetivoProximo);
            }

            long diasParaFechamento = ChronoUnit.DAYS.between(hoje, dataFechamentoAlerta);

            // Threshold: 1-5 dias para fechamento (dia 0 = já fechou, sem sentido alertar)
            if (diasParaFechamento >= 1 && diasParaFechamento <= 5) {
                String titulo = diasParaFechamento == 1
                        ? "Fatura Fecha Amanhã"
                        : "Fatura Fecha em " + diasParaFechamento + " Dias";
                String mensagem = diasParaFechamento == 1
                        ? String.format("A fatura do %s fecha amanhã. Cuidado com os últimos gastos.", cartao.getNome())
                        : String.format("A fatura do %s fecha em %d dias (%s). Planeje seus gastos.",
                                cartao.getNome(), diasParaFechamento,
                                dataFechamentoAlerta.format(DateTimeFormatter.ofPattern("dd/MM")));
                String novoMetadados = "{\"cartaoId\":\"" + cartao.getId()
                        + "\",\"diasParaFechamento\":" + diasParaFechamento
                        + ",\"dataFechamento\":\"" + dataFechamentoAlerta + "\"}";

                // Upsert: atualiza o aviso existente para este cartão (mantém contagem de dias atualizada)
                // Isso evita duplicatas E garante que o texto reflita os dias restantes atuais
                String cartaoIdStr = cartao.getId().toString();
                Optional<IaInsight> avisoExistenteOpt = avisosExistentes.stream()
                        .filter(ins -> ins.getMetadados() != null && ins.getMetadados().contains(cartaoIdStr))
                        .findFirst();

                if (avisoExistenteOpt.isPresent()) {
                    IaInsight existente = avisoExistenteOpt.get();
                    existente.setTitulo(titulo);
                    existente.setMensagem(mensagem);
                    existente.setMetadados(novoMetadados);
                    iaInsightRepository.save(existente);
                } else {
                    IaInsight insight = new IaInsight(usuario, TipoInsight.AVISO_FECHAMENTO, titulo, mensagem, novoMetadados);
                    try {
                        iaInsightRepository.save(insight);
                    } catch (Exception e) {
                        // Race condition: outra requisição já salvou o mesmo insight
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MELHOR CARTÃO PARA O MOMENTO (endpoint dedicado)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Endpoint dedicado: identifica qual cartão oferece o MAIOR prazo de pagamento
     * (cash-flow intelligence). O melhor cartão para comprar hoje é aquele cuja fatura
     * acabou de fechar — qualquer compra será cobrada apenas na fatura do mês seguinte,
     * maximizando o tempo que o dinheiro fica disponível na conta bancária.
     *
     * Chamado ao entrar na tela /cartoes.
     *
     * Regra matemática:
     *   - Se a fatura fecha dia 5 e hoje é dia 6 → fatura fez 1 dia → próximo vencimento em ~39 dias → MELHOR
     *   - Se a fatura fecha dia 5 e hoje é dia 20 → fatura fez 15 dias → próximo vencimento em ~25 dias → PIOR
     */
    public void processarMelhorCartaoParaUsuario(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        List<Cartao> cartoes = cartaoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);

        if (cartoes.size() < 2) {
            // Insight de "Melhor Cartão" só faz sentido quando há 2+ cartões para comparar
            return;
        }


        // Para cada cartão, calcular a data do PRÓXIMO fechamento
        // e os dias até esse fechamento (quanto MAIS dias → melhor cartão)
        record InfoCartao(Cartao cartao, LocalDate dataFechamento, long diasAteFechamento) {}

        List<InfoCartao> infos = new ArrayList<>();

        for (Cartao cartao : cartoes) {
            int diaFechamento = cartao.getDiaFechamento();

            // Calcular a data do fechamento no mês atual
            int diaFechamentoEfetivo = Math.min(diaFechamento, hoje.lengthOfMonth());
            LocalDate dataFechamentoNoMes = hoje.withDayOfMonth(diaFechamentoEfetivo);

            LocalDate proximoFechamento;
            if (hoje.isBefore(dataFechamentoNoMes) || hoje.equals(dataFechamentoNoMes)) {
                // Ainda não fechou este mês → próximo fechamento é este mês
                proximoFechamento = dataFechamentoNoMes;
            } else {
                // Já fechou este mês → próximo fechamento é no mês seguinte
                LocalDate proximoMes = hoje.plusMonths(1);
                int diaEfetivoProximo = Math.min(diaFechamento, proximoMes.lengthOfMonth());
                proximoFechamento = proximoMes.withDayOfMonth(diaEfetivoProximo);
            }

            long diasAteFechamento = ChronoUnit.DAYS.between(hoje, proximoFechamento);

            infos.add(new InfoCartao(cartao, proximoFechamento, diasAteFechamento));
        }

        // Ordenar por dias até fechamento DESCENDENTE → o primeiro é o melhor
        infos.sort((a, b) -> Long.compare(b.diasAteFechamento(), a.diasAteFechamento()));

        InfoCartao melhor = infos.get(0);
        InfoCartao pior = infos.get(infos.size() - 1);
        long ganhoDias = melhor.diasAteFechamento() - pior.diasAteFechamento();

        // Só gerar insight se houver diferença significativa (>= 5 dias de vantagem)
        // Caso contrário, os cartões estão equivalentes
        if (ganhoDias < 5) {
            return;
        }

        String titulo = "Melhor Cartão para o Momento";
        String mensagem = String.format(
                "O %s é o melhor cartão para usar agora — a fatura ainda não fechou e " +
                "qualquer compra só será cobrada em %d dias (%s). " +
                "Enquanto isso, o %s vence em apenas %d dias. " +
                "Use o cartão com mais prazo e deixe o seu dinheiro rendendo na conta!",
                melhor.cartao().getNome(),
                melhor.diasAteFechamento(),
                melhor.dataFechamento().format(DateTimeFormatter.ofPattern("dd/MM")),
                pior.cartao().getNome(),
                pior.diasAteFechamento()
        );

        // Montar metadados detalhados
        StringBuilder metadados = new StringBuilder("{");
        metadados.append("\"cartaoId\":\"").append(melhor.cartao().getId()).append("\"");
        metadados.append(",\"cartaoNome\":\"").append(melhor.cartao().getNome()).append("\"");
        metadados.append(",\"diasAteFechamento\":").append(melhor.diasAteFechamento());
        metadados.append(",\"dataFechamento\":\"").append(melhor.dataFechamento()).append("\"");

        // Incluir todos os cartões comparados para exibição no frontend
        metadados.append(",\"comparacao\":[");
        for (int i = 0; i < infos.size(); i++) {
            InfoCartao info = infos.get(i);
            if (i > 0) metadados.append(",");
            metadados.append("{");
            metadados.append("\"cartaoId\":\"").append(info.cartao().getId()).append("\"");
            metadados.append(",\"cartaoNome\":\"").append(info.cartao().getNome()).append("\"");
            metadados.append(",\"diasAteFechamento\":").append(info.diasAteFechamento());
            metadados.append(",\"dataFechamento\":\"").append(info.dataFechamento()).append("\"");
            metadados.append(",\"melhor\":").append(i == 0);
            metadados.append("}");
        }
        metadados.append("]");

        // Incluir o ganho de dias em benefício financeiro
        metadados.append(",\"ganhoDias\":").append(ganhoDias);
        metadados.append(",\"beneficioFinanceiro\":\"Comprar no ")
                .append(melhor.cartao().getNome())
                .append(" libera ").append(melhor.diasAteFechamento())
                .append(" dias de prazo — seu dinheiro fica disponível na conta.\"");
        metadados.append("}");

        // Apagar TODOS os insights MELHOR_CARTAO não lidos existentes para evitar duplicatas
        List<IaInsight> melhorCartaoExistentes = iaInsightRepository
                .findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId)
                .stream()
                .filter(ins -> ins.getTipo() == TipoInsight.MELHOR_CARTAO)
                .toList();

        if (!melhorCartaoExistentes.isEmpty()) {
            // Reaproveitamos o primeiro e deletamos os demais (evita criar um novo registro desnecessariamente)
            IaInsight principal = melhorCartaoExistentes.get(0);

            // Deleta duplicatas (índice 1 em diante)
            if (melhorCartaoExistentes.size() > 1) {
                iaInsightRepository.deleteAll(melhorCartaoExistentes.subList(1, melhorCartaoExistentes.size()));
            }

            // Atualiza o insight principal com os dados mais recentes
            principal.setTitulo(titulo);
            principal.setMensagem(mensagem);
            principal.setMetadados(metadados.toString());
            principal.setLido(false);
            iaInsightRepository.save(principal);
        } else {
            IaInsight insight = new IaInsight(
                    usuario,
                    TipoInsight.MELHOR_CARTAO,
                    titulo,
                    mensagem,
                    metadados.toString()
            );
            try {
                iaInsightRepository.save(insight);
            } catch (Exception e) {
                // Race condition: outra requisição já salvou o mesmo insight
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROCESSAMENTO ATÔMICO DE TODOS OS INSIGHTS DE CARTÃO (chamada única)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Processa TODOS os insights dedicados de cartão em uma única chamada.
     * Elimina o problema de React StrictMode executar múltiplas chamadas sequenciais.
     * Chamado ao entrar na tela /cartoes.
     */
    public void processarTodosInsightsCartao(Usuario usuario) {
        processarAvisoFechamentoParaUsuario(usuario);
        processarMelhorCartaoParaUsuario(usuario);
        processarConcentracaoGastosFaturaParaUsuario(usuario);
        processarOtimizacaoParcelamentoParaUsuario(usuario);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONCENTRAÇÃO DE GASTOS NA FATURA (Category Spike)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Endpoint dedicado: analisa a composição da fatura ABERTA por categoria.
     * Se uma categoria representar mais de 50% do total da fatura e não for
     * essencial (Moradia, Saúde, Transporte, Educação, Alimentação), gera
     * um insight de alerta.
     *
     * Insight example:
     *   "Nesta fatura, 60% dos seus gastos (R$ 800) estão concentrados
     *    apenas em 'Lazer'. Reduzir gastos nessa categoria terá o maior
     *    impacto na sua próxima fatura."
     *
     * Chamado ao entrar na tela /cartoes.
     */
    public void processarConcentracaoGastosFaturaParaUsuario(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        List<Cartao> cartoes = cartaoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);

        // Categorias consideradas "essenciais" — concentração nelas NÃO gera alerta
        Set<String> categoriasEssenciais = Set.of(
                "Moradia", "Saúde", "Transporte", "Educação", "Alimentação",
                "Mercado", "Supermercado", "Farmácia", "Gasolina", "Combustível",
                "Conta de Água", "Conta de Luz", "Conta de Gás", "Internet",
                "Telefone", "Freio", "IPVA", "Seguro"
        );

        for (Cartao cartao : cartoes) {
            // 1. Verificar se existe fatura ABERTA para este cartão no mês atual
            Optional<Fatura> faturaAtualOpt = faturaRepository
                    .findByCartaoIdAndUsuarioIdAndMesReferencia(cartao.getId(), usuarioId, inicioMes);

            boolean faturaAberta = faturaAtualOpt
                    .map(f -> !STATUS_FECHADOS.contains(f.getStatus()))
                    .orElse(false);

            if (!faturaAberta) continue;

            // 2. Buscar todas as transações COMPRA_CREDITO primárias (parcela 1 ou sem parcela)
            //    vinculadas ao cartão no período da fatura aberta
            List<Transacao> transacoesFatura = transacaoRepository
                    .findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(usuarioId, inicioMes, hoje)
                    .stream()
                    .filter(t -> t.getCartao() != null && t.getCartao().getId().equals(cartao.getId()))
                    .filter(t -> t.getTipo() == TipoTransacao.COMPRA_CREDITO)
                    .filter(t -> t.getNumeroParcela() == null || t.getNumeroParcela() == 1)
                    .collect(Collectors.toList());

            if (transacoesFatura.isEmpty()) continue;

            // 3. Calcular o total da fatura e agrupar por categoria
            BigDecimal totalFatura = transacoesFatura.stream()
                    .map(Transacao::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalFatura.compareTo(BigDecimal.ZERO) <= 0) continue;

            Map<String, BigDecimal> gastosPorCategoria = transacoesFatura.stream()
                    .filter(t -> t.getCategoria() != null)
                    .collect(Collectors.groupingBy(
                            t -> t.getCategoria().getNome(),
                            Collectors.reducing(BigDecimal.ZERO, Transacao::getValor, BigDecimal::add)
                    ));

            // 4. Para cada categoria, verificar se representa > 50% da fatura
            //    E não é essencial
            for (Map.Entry<String, BigDecimal> entry : gastosPorCategoria.entrySet()) {
                String nomeCategoria = entry.getKey();
                BigDecimal valorCategoria = entry.getValue();

                double percentual = valorCategoria
                        .divide(totalFatura, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();

                if (percentual <= 50.0) continue;

                // Verificar se a categoria é essencial
                boolean ehEssencial = categoriasEssenciais.stream()
                        .anyMatch(e -> e.equalsIgnoreCase(nomeCategoria));
                if (ehEssencial) continue;

                // Verificar unicidade por cartaoId + categoria
                //    Permite: "Lazer" no Nubank E "Lazer" no BB = 2 insights distintos
                //    Bloqueia: "Lazer" no Nubank gerado duas vezes
                boolean jaExiste = iaInsightRepository
                        .existsByUsuarioIdAndTipoAndLidoFalseAndMetadadosContaining(
                                usuarioId, TipoInsight.CONCENTRACAO_GASTOS_FATURA,
                                cartao.getId().toString())
                        && iaInsightRepository
                        .existsByUsuarioIdAndTipoAndLidoFalseAndMetadadosContaining(
                                usuarioId, TipoInsight.CONCENTRACAO_GASTOS_FATURA,
                                nomeCategoria);
                if (jaExiste) continue;

                // Calcular o impacto potencial (quanto o usuário economizaria se
                // cortasse pela metade essa categoria — estimativa conservadora)
                BigDecimal economiaPotencial = valorCategoria
                        .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

                String titulo = "Concentração de Gastos: " + nomeCategoria;
                String mensagem = String.format(
                        "Nesta fatura do %s, %.0f%% dos seus gastos (R$ %.2f) estão concentrados " +
                        "apenas em \"%s\". Reduzir gastos nessa categoria terá o maior impacto " +
                        "na sua próxima fatura. Uma redução de 50%% liberaria R$ %.2f.",
                        cartao.getNome(), percentual, valorCategoria,
                        nomeCategoria, economiaPotencial);

                String metadados = String.format(
                        "{\"cartaoId\":\"%s\",\"cartaoNome\":\"%s\",\"categoria\":\"%s\"," +
                        "\"valorCategoria\":%.2f,\"totalFatura\":%.2f,\"percentual\":%.1f," +
                        "\"economiaPotencial\":%.2f,\"ehEssencial\":false}",
                        cartao.getId(), cartao.getNome(), nomeCategoria,
                        valorCategoria.doubleValue(), totalFatura.doubleValue(),
                        percentual, economiaPotencial.doubleValue());

                IaInsight insight = new IaInsight(
                        usuario,
                        TipoInsight.CONCENTRACAO_GASTOS_FATURA,
                        titulo,
                        mensagem,
                        metadados
                );
                try {
                    iaInsightRepository.save(insight);
                } catch (Exception e) {
                    // Race condition: outram requisição já salvou o mesmo insight
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // OTIMIZAÇÃO DE PARCELAMENTOS FUTUROS (Folga de Limite)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Endpoint dedicado: identifica parcelamentos que terminam no mês atual
     * (ex: parcela 10/10) e informa o usuário que aquele valor ficará "livre"
     * no orçamento a partir do próximo mês.
     *
     * Insight example:
     *   "Boa notícia! O parcelamento da sua TV (R$ 200/mês) termina nesta
     *    fatura. A partir do mês que vem, você terá esse valor 'livre' no
     *    seu orçamento novamente."
     *
     * Chamado ao entrar na tela /cartoes.
     */
    public void processarOtimizacaoParcelamentoParaUsuario(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        List<Cartao> cartoes = cartaoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);

        for (Cartao cartao : cartoes) {
            // 1. Buscar a fatura ABERTA do mês atual para este cartão
            Optional<Fatura> faturaAtualOpt = faturaRepository
                    .findByCartaoIdAndUsuarioIdAndMesReferencia(cartao.getId(), usuarioId, inicioMes);

            if (faturaAtualOpt.isEmpty()) continue;
            Fatura faturaAtual = faturaAtualOpt.get();

            // Só analisa fatura ABERTA (que ainda está sendo construída)
            if (STATUS_FECHADOS.contains(faturaAtual.getStatus())) continue;

            // 2. Buscar transações COMPRA_CREDITO vinculadas a ESTA fatura específica
            List<Transacao> transacoesFatura = transacaoRepository
                    .findByFaturaIdAndAtivoTrue(faturaAtual.getId());

            // Filtrar apenas parcelamentos (numeroParcela != null e totalParcelas > 1)
            List<Transacao> parcelasFatura = transacoesFatura.stream()
                    .filter(t -> t.getTipo() == TipoTransacao.COMPRA_CREDITO)
                    .filter(t -> t.getNumeroParcela() != null && t.getTotalParcelas() != null)
                    .filter(t -> t.getTotalParcelas() > 1)
                    .collect(Collectors.toList());

            if (parcelasFatura.isEmpty()) continue;

            // 3. Agrupar por descrição e verificar se ALGUMA parcela nesta fatura
            //    é a ÚLTIMA do parcelamento (numeroParcela == totalParcelas)
            Map<String, List<Transacao>> porDescricao = parcelasFatura.stream()
                    .collect(Collectors.groupingBy(Transacao::getDescricao));

            for (Map.Entry<String, List<Transacao>> entry : porDescricao.entrySet()) {
                String descricao = entry.getKey();
                List<Transacao> parcelas = entry.getValue();

                // Verificar se existe a última parcela nesta fatura
                boolean temUltimaParcela = parcelas.stream()
                        .anyMatch(t -> t.getNumeroParcela().equals(t.getTotalParcelas()));
                if (!temUltimaParcela) continue;

                // Encontrar a parcela de maior número para dados do insight
                Transacao ultimaParcela = parcelas.stream()
                        .max(Comparator.comparingInt(Transacao::getNumeroParcela))
                        .orElse(null);
                if (ultimaParcela == null) continue;

                int totalParcelas = ultimaParcela.getTotalParcelas();
                int parcelaAtual = ultimaParcela.getNumeroParcela();
                BigDecimal valorParcela = ultimaParcela.getValor();

                // 4. Verificar unicidade por cartaoId + descrição
                //    Permite: "TV" no Nubank E "TV" no BB = 2 insights distintos
                //    Bloqueia: "TV" no Nubank gerado duas vezes
                //    Ex: "Ar Condicionado" e "TV" no mesmo cartão geram insights separados
                boolean jaExiste = iaInsightRepository
                        .existsByUsuarioIdAndTipoAndLidoFalseAndMetadadosContaining(
                                usuarioId, TipoInsight.OTIMIZACAO_PARCELAMENTO,
                                cartao.getId().toString())
                        && iaInsightRepository
                        .existsByUsuarioIdAndTipoAndLidoFalseAndMetadadosContaining(
                                usuarioId, TipoInsight.OTIMIZACAO_PARCELAMENTO,
                                "\"" + descricao + "\"");
                if (jaExiste) continue;

                // 5. Calcular impacto mensal (soma de todas as parcelas nesta fatura)
                BigDecimal impactoMensal = parcelas.stream()
                        .map(Transacao::getValor)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                String titulo = "Folga de Limite: Parcelamento Finalizado";
                String mensagem = String.format(
                        "Boa notícia! O parcelamento de \"%s\" (R$ %.2f/mês, %d parcelas) " +
                        "termina nesta fatura do %s. A partir do mês que vem, você terá esse " +
                        "valor livre no seu orçamento. Considere direcionar essa verba para " +
                        "economizar ou investir.",
                        descricao, valorParcela, totalParcelas, cartao.getNome());

                String metadados = String.format(
                        "{\"cartaoId\":\"%s\",\"cartaoNome\":\"%s\",\"descricao\":\"%s\"," +
                        "\"valorParcela\":%.2f,\"totalParcelas\":%d,\"impactoMensal\":%.2f}",
                        cartao.getId(), cartao.getNome(), descricao,
                        valorParcela.doubleValue(), totalParcelas,
                        impactoMensal.doubleValue());

                IaInsight insight = new IaInsight(
                        usuario,
                        TipoInsight.OTIMIZACAO_PARCELAMENTO,
                        titulo,
                        mensagem,
                        metadados
                );
                try {
                    iaInsightRepository.save(insight);
                } catch (Exception e) {
                    // Race condition: outra requisição já salvou o mesmo insight
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private String formatarValor(BigDecimal valor) {
        return String.format("R$ %.2f", valor);
    }
}
