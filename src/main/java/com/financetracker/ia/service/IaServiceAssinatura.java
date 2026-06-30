package com.financetracker.ia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.assinatura.entity.Assinatura;
import com.financetracker.assinatura.enums.TipoRecorrencia;
import com.financetracker.assinatura.repository.AssinaturaRepository;
import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.ia.dto.DominioEfeitoDominoResponse;
import com.financetracker.ia.dto.DominioEfeitoDominoResponse.AlertaCartao;
import com.financetracker.ia.dto.DominioEfeitoDominoResponse.ItemRanking;
import com.financetracker.ia.domain.IaClassificacaoAssinatura;
import com.financetracker.ia.domain.IaInsight;
import com.financetracker.ia.domain.NivelEssencialidade;
import com.financetracker.ia.domain.TipoInsight;
import com.financetracker.ia.dto.FadigaAssinaturaResponse;
import com.financetracker.ia.dto.FadigaAssinaturaResponse.ItemEssencialidade;
import com.financetracker.ia.dto.InteligenciaAssinaturaResponse;
import com.financetracker.ia.dto.InteligenciaAssinaturaResponse.ReajusteDetectado;
import com.financetracker.ia.repository.IaClassificacaoAssinaturaRepository;
import com.financetracker.ia.repository.IaInsightRepository;
import com.financetracker.transacao.entity.Fatura;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.repository.FaturaRepository;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.usuario.entity.Usuario;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IaServiceAssinatura {

    private final IaInsightRepository iaInsightRepository;
    private final IaClassificacaoAssinaturaRepository classificacaoRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final TransacaoRepository transacaoRepository;
    private final FaturaRepository faturaRepository;
    private final CartaoRepository cartaoRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Value("${OPENAI_BASE_URL:https://openrouter.ai/api/v1}")
    private String openAiBaseUrl;

    @Value("${OPENAI_API_KEY:}")
    private String openAiApiKey;

    @Value("${OPENAI_MODEL:deepseek/deepseek-v4-flash}")
    private String openAiModel;

    // ── Categorias cuja duplicação é preocupante ─────────────────────────
    private static final Set<String> CATEGORIAS_DUPLICACAO_PREOCUPANTE = Set.of(
            "Streaming", "Armazenamento", "Música", "Musica",
            "Clube de Benefícios", "Clube de Beneficios"
    );

    public IaServiceAssinatura(IaInsightRepository iaInsightRepository,
                               IaClassificacaoAssinaturaRepository classificacaoRepository,
                               AssinaturaRepository assinaturaRepository,
                               TransacaoRepository transacaoRepository,
                               FaturaRepository faturaRepository,
                               CartaoRepository cartaoRepository,
                               ObjectMapper objectMapper,
                               JdbcTemplate jdbcTemplate) {
        this.iaInsightRepository = iaInsightRepository;
        this.classificacaoRepository = classificacaoRepository;
        this.assinaturaRepository = assinaturaRepository;
        this.transacaoRepository = transacaoRepository;
        this.faturaRepository = faturaRepository;
        this.cartaoRepository = cartaoRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.jdbcTemplate = jdbcTemplate;
    }

    // ponytail: inline migration, migrar para Flyway/Liquibase quando houver mais de 1 migration
    @PostConstruct
    public void migrarConstraintsEnum() {
        try {
            // 1. Dropar constraints antigas PRIMEIRO — senão o UPDATE falha
            jdbcTemplate.execute("""
                DO $$ DECLARE r RECORD;
                BEGIN
                    FOR r IN SELECT conname FROM pg_constraint
                             WHERE conrelid = 'ia_classificacoes_assinatura'::regclass
                             AND contype = 'c' LOOP
                        EXECUTE 'ALTER TABLE ia_classificacoes_assinatura DROP CONSTRAINT ' || r.conname;
                    END LOOP;
                END $$""");

            // 2. Agora sim — dados antigos: DISCRICIONARIA → OPCIONAL
            jdbcTemplate.execute(
                "UPDATE ia_classificacoes_assinatura SET essencialidade = 'OPCIONAL' WHERE essencialidade = 'DISCRICIONARIA'");
            jdbcTemplate.execute(
                "UPDATE ia_classificacoes_assinatura SET resposta_usuario = 'OPCIONAL' WHERE resposta_usuario = 'DISCRICIONARIA'");

            // 3. Recriar constraints com todos os valores aceitos
            jdbcTemplate.execute(
                "ALTER TABLE ia_classificacoes_assinatura ADD CONSTRAINT ia_classificacoes_assinatura_essencialidade_check " +
                "CHECK (essencialidade IN ('ESSENCIAL', 'IMPORTANTE', 'OPCIONAL', 'DISCRICIONARIA'))");
            jdbcTemplate.execute(
                "ALTER TABLE ia_classificacoes_assinatura ADD CONSTRAINT ia_classificacoes_assinatura_resposta_usuario_check " +
                "CHECK (resposta_usuario IS NULL OR resposta_usuario IN ('ESSENCIAL', 'IMPORTANTE', 'OPCIONAL', 'DISCRICIONARIA'))");
        } catch (Exception e) {
            // Tabela pode não existir ainda (primeira execução)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ENDPOINT DEDICADO: ANÁLISE DE FADIGA DE ASSINATURA
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Endpoint dedicado: analisa todas as assinaturas ativas do usuário,
     * usa IA para classificar essencialidade, e retorna o panorama completo.
     * Chamado ao entrar na tela /assinaturas.
     */
    public FadigaAssinaturaResponse analisarFadiga(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        List<Assinatura> assinaturasAtivas = assinaturaRepository.findByUsuarioId(usuarioId)
                .stream()
                .filter(a -> Boolean.TRUE.equals(a.getAtivo()))
                .collect(Collectors.toList());

        if (assinaturasAtivas.isEmpty()) {
            return new FadigaAssinaturaResponse(
                    0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, "SEM_DADOS", "➖",
                    List.of(), Map.of(), List.of(),
                    "Nenhuma assinatura ativa encontrada."
            );
        }

        // 1. Para cada assinatura, pegar classificação do banco ou chamar IA
        List<ItemEssencialidade> itens = new ArrayList<>();
        for (Assinatura assinatura : assinaturasAtivas) {
            itens.add(obterOuClassificar(assinatura, usuario));
        }

        // 2. Calcular totais por nível
        BigDecimal totalEssenciais = itens.stream()
                .filter(i -> "ESSENCIAL".equals(i.essencialidade()))
                .map(ItemEssencialidade::valorMensal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalImportantes = itens.stream()
                .filter(i -> "IMPORTANTE".equals(i.essencialidade()))
                .map(ItemEssencialidade::valorMensal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiscricionarias = itens.stream()
                .filter(i -> "OPCIONAL".equals(i.essencialidade()))
                .map(ItemEssencialidade::valorMensal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalGeral = totalEssenciais.add(totalImportantes).add(totalDiscricionarias);

        // 3. Fatura total mensal
        BigDecimal faturaTotal = calcularFaturaTotalMensal(usuarioId);

        // 4. Índices
        BigDecimal indiceAssinaturas = faturaTotal.compareTo(BigDecimal.ZERO) > 0
                ? totalGeral.divide(faturaTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal totalNaoEssencial = totalImportantes.add(totalDiscricionarias);
        BigDecimal indiceNaoEssencial = totalGeral.compareTo(BigDecimal.ZERO) > 0
                ? totalNaoEssencial.divide(totalGeral, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // 5. Classificação global
        double idx = indiceAssinaturas.doubleValue();
        double idxNaoEss = indiceNaoEssencial.doubleValue();
        String classificacaoGlobal;
        String nivelAlerta;

        boolean fadigaPorPercentual = idx > 15.0;
        boolean fadigaPorNaoEssencial = idxNaoEss > 50.0 && totalGeral.compareTo(BigDecimal.ZERO) > 0;

        if (fadigaPorPercentual || fadigaPorNaoEssencial) {
            classificacaoGlobal = "FADIGA";
            nivelAlerta = "🔴";
        } else if (idx > 8.0) {
            classificacaoGlobal = "ATENCAO";
            nivelAlerta = "🟡";
        } else {
            classificacaoGlobal = "SAUDAVEL";
            nivelAlerta = "🟢";
        }

        // 6. Duplicadas por categoria
        Map<String, Long> duplicadas = assinaturasAtivas.stream()
                .filter(a -> a.getCategoria() != null)
                .collect(Collectors.groupingBy(
                        a -> a.getCategoria().getNome(),
                        Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // 7. Serviços semelhantes
        List<String> servicosSemelhantes = assinaturasAtivas.stream()
                .filter(a -> a.getCategoria() != null)
                .collect(Collectors.groupingBy(a -> a.getCategoria().getNome()))
                .entrySet().stream()
                .filter(e -> e.getValue().size() > 1 && CATEGORIAS_DUPLICACAO_PREOCUPANTE.contains(e.getKey()))
                .map(e -> {
                    String cat = e.getKey();
                    String nomes = e.getValue().stream().map(Assinatura::getNome).collect(Collectors.joining(", "));
                    return cat + ": " + nomes;
                })
                .toList();

        // 8. Montar mensagem
        String mensagem = montarMensagemFadiga(itens.size(), totalGeral, faturaTotal,
                totalEssenciais, totalImportantes, totalDiscricionarias,
                duplicadas, servicosSemelhantes, idx);

        return new FadigaAssinaturaResponse(
                itens.size(), totalEssenciais, totalImportantes, totalDiscricionarias,
                totalGeral, indiceAssinaturas, indiceNaoEssencial,
                classificacaoGlobal, nivelAlerta,
                itens, duplicadas, servicosSemelhantes, mensagem
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // ENDPOINT DEDICADO: REAJUSTES DE ASSINATURAS
    // ═══════════════════════════════════════════════════════════════════

    public InteligenciaAssinaturaResponse analisarInteligencia(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        List<Assinatura> assinaturasAtivas = assinaturaRepository.findByUsuarioId(usuarioId)
                .stream()
                .filter(a -> Boolean.TRUE.equals(a.getAtivo()))
                .collect(Collectors.toList());

        List<ReajusteDetectado> reajustes = new ArrayList<>();
        for (Assinatura assinatura : assinaturasAtivas) {
            ReajusteDetectado reajuste = detectarReajuste(assinatura, usuarioId);
            if (reajuste != null && !reajuste.alteracaoVoluntaria()
                    && reajuste.percentualAumento().compareTo(BigDecimal.valueOf(5)) > 0) {
                reajustes.add(reajuste);
            }
        }

        return new InteligenciaAssinaturaResponse(reajustes);
    }

    private ReajusteDetectado detectarReajuste(Assinatura assinatura, UUID usuarioId) {
        LocalDate dozeMesesAtras = LocalDate.now().minusDays(365);
        String descricaoEsperada = "Assinatura: " + assinatura.getNome();

        List<Transacao> transacoes = transacaoRepository
                .findTopByDescricaoLike(usuarioId, assinatura.getNome())
                .stream()
                .filter(t -> t.getDescricao() != null && t.getDescricao().equalsIgnoreCase(descricaoEsperada))
                .filter(t -> !t.getData().isBefore(dozeMesesAtras))
                .sorted(Comparator.comparing(Transacao::getData).reversed())
                .limit(3)
                .toList();

        if (transacoes.size() < 2) {
            return null;
        }

        BigDecimal valorAtual = transacoes.get(0).getValor();
        BigDecimal valorAnterior = transacoes.get(1).getValor();

        if (valorAnterior.compareTo(BigDecimal.ZERO) <= 0 || valorAtual.compareTo(valorAnterior) <= 0) {
            return null;
        }

        BigDecimal percentualAumento = valorAtual.subtract(valorAnterior)
                .divide(valorAnterior, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal impactoAnual = valorAtual.subtract(valorAnterior).multiply(BigDecimal.valueOf(12));

        boolean alteracaoVoluntaria = assinatura.getValor().compareTo(valorAtual) == 0;

        String categoria = assinatura.getCategoria() != null ? assinatura.getCategoria().getNome() : "Outros";

        return new ReajusteDetectado(
                assinatura.getId(),
                assinatura.getNome(),
                categoria,
                valorAnterior,
                valorAtual,
                percentualAumento,
                impactoAnual,
                alteracaoVoluntaria
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLASSIFICAÇÃO POR IA (sem hardcoded)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Obtém classificação do banco ou chama a IA para classificar.
     * Se a IA não tem confiança suficiente, salva sem confirmação
     * e retorna com flag para o frontend perguntar ao usuário.
     */
    @SuppressWarnings("unchecked")
    private ItemEssencialidade obterOuClassificar(Assinatura assinatura, Usuario usuario) {
        BigDecimal valorMensal = calcularValorMensal(assinatura);
        String categoria = assinatura.getCategoria() != null ? assinatura.getCategoria().getNome() : "Outros";
        String nome = assinatura.getNome();

        // 1. Verificar cache no banco
        Optional<IaClassificacaoAssinatura> cacheOpt = classificacaoRepository.findByAssinaturaId(assinatura.getId());
        if (cacheOpt.isPresent()) {
            IaClassificacaoAssinatura cache = cacheOpt.get();
            NivelEssencialidade ess = cache.isConfirmado()
                    ? (cache.getRespostaUsuario() != null ? cache.getRespostaUsuario() : cache.getEssencialidade())
                    : cache.getEssencialidade();
            return toItem(nome, categoria, valorMensal, ess, cache.isConfirmado());
        }

        // 2. Chamar IA
        try {
            Map<String, Object> resultado = chamarIaParaClassificar(nome, categoria, "");

            String essencialidadeStr = (String) resultado.get("essencialidade");
            String justificativa = (String) resultado.get("justificativa");
            Number confianca = (Number) resultado.get("confianca");
            boolean confiavel = confianca != null && confianca.doubleValue() >= 0.7;

            NivelEssencialidade ess = parseEssencialidade(essencialidadeStr);

            IaClassificacaoAssinatura classificacao = new IaClassificacaoAssinatura(
                    usuario, assinatura, ess, justificativa, confiavel);
            try {
                classificacaoRepository.save(classificacao);
            } catch (Exception e) {
                // Race condition
            }

            return toItem(nome, categoria, valorMensal, ess, confiavel);

        } catch (Exception e) {
            // IA indisponível → classificação provisória como OPCIONAL (conservador)
            return toItem(nome, categoria, valorMensal, NivelEssencialidade.OPCIONAL, false);
        }
    }

    /**
     * Chama a IA para classificar uma assinatura em ESSENCIAL/IMPORTANTE/DISCICIONARIA.
     * A IA decide com base no nome, categoria e descrição — sem listas hardcoded.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> chamarIaParaClassificar(String nome, String categoria, String descricao) throws Exception {
        if (openAiApiKey == null || openAiApiKey.trim().isEmpty()) {
            throw new IllegalStateException("OpenAI API Key não configurada.");
        }

        String promptSistema = """
                Você é um consultor financeiro especializado em análise de assinaturas recorrentes.
                Sua função é classificar cada assinatura do usuário em um nível de essencialidade.

                Classifique cada assinatura em UMA das categorias:
                - ESSENCIAL: serviço necessário para o dia a dia (internet, celular, moradia, seguro, saúde)
                - IMPORTANTE: agrega valor real mas pode ser revisto (academia, software de trabalho, educação, armazenamento cloud)
                - OPCIONAL: entretenimento ou conveniência que pode ser cortado (streamings, clube de benefícios, apps de delivery premium)

                Regras:
                - Considere o contexto do serviço, não apenas o nome.
                - Se for um serviço de streaming (Netflix, Disney+, etc) → OPCIONAL
                - Se for internet/celular/seguro/saúde → ESSENCIAL
                - Se for academia/software de trabalho/educação → IMPORTANTE
                - Se não tiver certeza, classifique como IMPORTANTE (nível do meio) e indique confiança baixa.
                - Retorne UM objeto JSON para a assinatura fornecida.
                """;

        String userContent = String.format(
                "Classifique esta assinatura:\n- Nome: %s\n- Categoria cadastrada: %s\n- Descrição: %s",
                nome, categoria, descricao.isEmpty() ? "Não informada" : descricao);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + openAiApiKey);

        Map<String, Object> msgSystem = Map.of("role", "system", "content", promptSistema);
        Map<String, Object> msgUser = Map.of("role", "user", "content", userContent);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openAiModel);
        requestBody.put("messages", List.of(msgSystem, msgUser));
        requestBody.put("temperature", 0.1);
        requestBody.put("max_tokens", 200);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                openAiBaseUrl + "/chat/completions", entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<?, ?> responseMap = objectMapper.readValue(response.getBody(), Map.class);
            List<?> choices = (List<?>) responseMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                Map<?, ?> message = (Map<?, ?>) choice.get("message");
                String content = ((String) message.get("content")).trim()
                        .replaceAll("```json", "").replaceAll("```", "").trim();

                return objectMapper.readValue(content, Map.class);
            }
        }

        throw new RuntimeException("Resposta inválida da API de IA.");
    }

    private NivelEssencialidade parseEssencialidade(String valor) {
        if (valor == null) return NivelEssencialidade.OPCIONAL;
        return switch (valor.trim().toUpperCase()) {
            case "ESSENCIAL" -> NivelEssencialidade.ESSENCIAL;
            case "IMPORTANTE" -> NivelEssencialidade.IMPORTANTE;
            default -> NivelEssencialidade.OPCIONAL;
        };
    }

    private ItemEssencialidade toItem(String nome, String categoria, BigDecimal valorMensal,
                                       NivelEssencialidade ess, boolean confirmado) {
        String emoji = switch (ess) {
            case ESSENCIAL -> "🟢";
            case IMPORTANTE -> "🟡";
            case OPCIONAL, DISCRICIONARIA -> "🔴";
        };
        String sufixo = confirmado ? "" : " ⚠️";
        return new ItemEssencialidade(nome, categoria, valorMensal, ess.name(), emoji + sufixo);
    }

    // ═══════════════════════════════════════════════════════════════════
    // RESPOSTA DO USUÁRIO À CLASSIFICAÇÃO (comportamental)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Salva a resposta do usuário para uma assinatura que a IA não tinha confiança.
     */
    public void salvarRespostaClassificacao(UUID assinaturaId, NivelEssencialidade resposta) {
        Optional<IaClassificacaoAssinatura> classifOpt = classificacaoRepository.findByAssinaturaId(assinaturaId);
        if (classifOpt.isPresent()) {
            IaClassificacaoAssinatura classif = classifOpt.get();
            classif.setRespostaUsuario(resposta);
            classif.setConfirmado(true);
            classif.setAtualizadoEm(LocalDateTime.now());
            classificacaoRepository.save(classif);
        }
    }

    /**
     * Processa resposta comportamental do usuário e mapeia para o nível correto.
     *
     * Perfis comportamentais:
     *   "uso_diario"     → ESSENCIAL  (uso constante, faz parte do dia a dia)
     *   "uso_regulares"  → IMPORTANTE  (usa com frequência, agrega valor)
     *   "pouco_uso"      → OPCIONAL    (usa pouco, poderia viver sem)
     *   "esqueci"        → OPCIONAL    (já nem lembra que tem, candidato a cancelar)
     */
    public void classificarComportamento(UUID assinaturaId, String perfil) {
        NivelEssencialidade essencialidade = switch (perfil.toLowerCase()) {
            case "uso_diario" -> NivelEssencialidade.ESSENCIAL;
            case "uso_regulares" -> NivelEssencialidade.IMPORTANTE;
            case "pouco_uso" -> NivelEssencialidade.OPCIONAL;
            case "esqueci" -> NivelEssencialidade.OPCIONAL;
            default -> NivelEssencialidade.IMPORTANTE;
        };

        Optional<IaClassificacaoAssinatura> classifOpt = classificacaoRepository.findByAssinaturaId(assinaturaId);
        if (classifOpt.isPresent()) {
            IaClassificacaoAssinatura classif = classifOpt.get();
            classif.setRespostaUsuario(essencialidade);
            classif.setConfirmado(true);
            classif.setAtualizadoEm(LocalDateTime.now());
            classificacaoRepository.save(classif);
        }
    }

    /**
     * Retorna todas as assinaturas do usuário que precisam de confirmação.
     * Intervalo adaptativo: OPCIONAL=15d, IMPORTANTE=45d, ESSENCIAL=90d.
     */
    public List<Map<String, Object>> obterPendentesConfirmacao(Usuario usuario) {
        UUID usuarioId = usuario.getId();

        // 1. Classificações nunca confirmadas pela IA
        List<IaClassificacaoAssinatura> nuncaConfirmadas = classificacaoRepository
                .findByUsuarioIdAndConfirmadoFalse(usuarioId);

        // 2. Classificações confirmadas — revisão adaptativa por essencialidade
        List<IaClassificacaoAssinatura> todasConfirmadas = classificacaoRepository
                .findByUsuarioIdAndConfirmadoTrue(usuarioId);
        LocalDateTime agora = LocalDateTime.now();
        List<IaClassificacaoAssinatura> expiradas = todasConfirmadas.stream()
                .filter(c -> {
                    long diasRevisao = switch (c.getEssencialidade()) {
                        case ESSENCIAL -> 90;
                        case IMPORTANTE -> 45;
                        case OPCIONAL, DISCRICIONARIA -> 15;
                    };
                    return c.getAtualizadoEm().isBefore(agora.minusDays(diasRevisao));
                })
                .toList();

        // Merge e deduplica por assinaturaId
        Map<UUID, IaClassificacaoAssinatura> porAssinatura = new LinkedHashMap<>();
        nuncaConfirmadas.forEach(c -> porAssinatura.putIfAbsent(c.getAssinatura().getId(), c));
        expiradas.forEach(c -> porAssinatura.putIfAbsent(c.getAssinatura().getId(), c));

        return porAssinatura.values().stream().map(c -> {
            Map<String, Object> item = new HashMap<>();
            item.put("assinaturaId", c.getAssinatura().getId().toString());
            item.put("nome", c.getAssinatura().getNome());
            item.put("categoria", c.getAssinatura().getCategoria() != null
                    ? c.getAssinatura().getCategoria().getNome() : "Outros");
            item.put("valorMensal", calcularValorMensal(c.getAssinatura()));
            item.put("essencialidade", c.getEssencialidade().name());
            item.put("justificativa", c.getJustificativa());
            return item;
        }).toList();
    }

    // ═══════════════════════════════════════════════════════════════════
    // RN-05 / RN-06 — GERAÇÃO DE INSIGHTS (persistência)
    // ═══════════════════════════════════════════════════════════════════

    public void processarInsightsAssinaturaParaUsuario(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        List<IaInsight> insightsExistentes = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);

        List<Assinatura> assinaturasAtivas = assinaturaRepository.findByUsuarioId(usuarioId)
                .stream()
                .filter(a -> Boolean.TRUE.equals(a.getAtivo()))
                .collect(Collectors.toList());

        if (assinaturasAtivas.isEmpty()) return;

        // ─── RN-05: FADIGA DE ASSINATURA ────────────────────────────
        FadigaAssinaturaResponse fadiga = analisarFadiga(usuario);

        if (!"SAUDAVEL".equals(fadiga.classificacaoGlobal()) && !"SEM_DADOS".equals(fadiga.classificacaoGlobal())) {
            String chave = "fadiga_global";
            boolean jaExiste = insightsExistentes.stream()
                    .anyMatch(ins -> ins.getTipo() == TipoInsight.FADIGA_ASSINATURA
                            && ins.getMetadados() != null
                            && ins.getMetadados().contains(chave));

            if (!jaExiste) {
                String metadados = String.format(
                        "{\"chave\":\"%s\",\"totalAssinaturas\":%d,\"totalGeral\":%s," +
                        "\"faturaTotal\":%s,\"indiceAssinaturas\":%s,\"classificacao\":\"%s\"}",
                        chave, fadiga.totalAssinaturas(), fadiga.totalGeral(),
                        fadiga.indiceAssinaturas(), fadiga.classificacaoGlobal());

                IaInsight insight = new IaInsight(usuario, TipoInsight.FADIGA_ASSINATURA,
                        "Fadiga de Assinaturas", fadiga.mensagem(), metadados);
                try {
                    iaInsightRepository.save(insight);
                } catch (Exception e) {
                    // Race condition
                }
            }
        }

        // ─── RN-06: REAJUSTE SILENCIOSO ─────────────────────────────
        processarReajusteSilencioso(usuario, assinaturasAtivas, insightsExistentes);

        // ─── AUDITORIA ZUMBI ────────────────────────────────────────
        processarAuditoriaZumbi(usuario, assinaturasAtivas, insightsExistentes);
    }

    // ═══════════════════════════════════════════════════════════════════
    // RN-06 — REAJUSTE SILENCIOSO
    // ═══════════════════════════════════════════════════════════════════

    private void processarReajusteSilencioso(Usuario usuario, List<Assinatura> assinaturasAtivas,
                                               List<IaInsight> insightsExistentes) {
        UUID usuarioId = usuario.getId();
        LocalDate noventaDiasAtras = LocalDate.now().minusDays(90);

        for (Assinatura assinatura : assinaturasAtivas) {
            String descricaoEsperada = "Assinatura: " + assinatura.getNome();

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
                            try {
                                iaInsightRepository.save(insight);
                            } catch (Exception e) {
                                // Race condition
                            }
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // AUDITORIA ZUMBI — revisão periódica por marco temporal
    // ═══════════════════════════════════════════════════════════════════

    private void processarAuditoriaZumbi(Usuario usuario, List<Assinatura> assinaturasAtivas,
                                          List<IaInsight> insightsExistentes) {
        UUID usuarioId = usuario.getId();

        for (Assinatura assinatura : assinaturasAtivas) {
            long mesesAtivos = ChronoUnit.MONTHS.between(assinatura.getDataInicio(), LocalDate.now());
            if (mesesAtivos <= 0 || mesesAtivos % 6 != 0) continue;

            BigDecimal valorMensal = calcularValorMensal(assinatura);
            BigDecimal custoAcumulado = calcularCustoAcumulado(assinatura, mesesAtivos);

            String assinaturaId = assinatura.getId().toString();
            boolean jaExiste = iaInsightRepository.existsByUsuarioIdAndTipoAndLidoFalseAndMetadadosContaining(
                    usuarioId, TipoInsight.ASSINATURA_ESQUECIDA, assinaturaId);
            if (jaExiste) continue;

            // Classificar essencialidade (pega cache ou usa provisória)
            ItemEssencialidade item = obterOuClassificar(assinatura, usuario);
            String essencialidade = item.essencialidade();
            boolean naoEssencial = "IMPORTANTE".equals(essencialidade) || "OPCIONAL".equals(essencialidade);

            String mensagem = montarMensagemAuditoriaZumbi(
                    assinatura.getNome(), assinatura.getTipoRecorrencia(),
                    assinatura.getValor(), valorMensal, mesesAtivos, custoAcumulado, essencialidade, naoEssencial);

            String metadados = String.format(
                    "{\"assinaturaId\":\"%s\",\"nome\":\"%s\",\"mesesAtivos\":%d," +
                    "\"custoAcumulado\":\"%s\",\"essencialidade\":\"%s\"}",
                    assinaturaId, assinatura.getNome(), mesesAtivos, custoAcumulado, essencialidade);

            IaInsight insight = new IaInsight(
                    usuario, TipoInsight.ASSINATURA_ESQUECIDA,
                    "Auditoria de Assinatura", mensagem, metadados);
            try {
                iaInsightRepository.save(insight);
            } catch (Exception e) {
                // Race condition
            }
        }
    }

    private String montarMensagemAuditoriaZumbi(String nome, TipoRecorrencia recorrencia,
                                                 BigDecimal valorUnitario, BigDecimal valorMensal,
                                                 long mesesAtivos, BigDecimal custoAcumulado,
                                                 String essencialidade, boolean naoEssencial) {
        StringBuilder msg = new StringBuilder();

        String unidadeCobranca = switch (recorrencia) {
            case ANUAL -> "ano";
            case TRIMESTRAL -> "trimestre";
            case MENSAL -> "mês";
            default -> "período";
        };

        msg.append(String.format("A assinatura \"%s\" está ativa há %d meses, com custo de %s por %s.",
                nome, mesesAtivos, formatarValor(valorUnitario), unidadeCobranca));
        msg.append(String.format("\n\nAté o momento, você já investiu %s nesse serviço.", formatarValor(custoAcumulado)));
        msg.append("\n\nComo esta é uma assinatura de uso recorrente, vale uma revisão rápida:");
        msg.append("\n• Você ainda utiliza esse serviço com a frequência esperada?");
        msg.append("\n• O plano contratado continua fazendo sentido para sua rotina?");
        msg.append("\n• Existe uma alternativa mais econômica que atenda às mesmas necessidades?");

        if (naoEssencial) {
            BigDecimal proximos6 = valorMensal.multiply(BigDecimal.valueOf(6));
            BigDecimal proximos12 = valorMensal.multiply(BigDecimal.valueOf(12));
            msg.append(String.format(
                    "\n\nCaso conclua que a assinatura não está entregando o valor esperado, cancelá-la agora pode liberar %s nos próximos 6 meses (ou %s em um ano) para fortalecer sua reserva de emergência ou acelerar outras metas financeiras.",
                    formatarValor(proximos6), formatarValor(proximos12)));
        } else {
            msg.append(
                    "\n\nComo é uma assinatura essencial, não se trata de cancelar, mas de verificar se o plano contratado ainda é o melhor custo-benefício para suas necessidades.");
        }

        return msg.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // CÁLCULOS

    /**
     * Calcula o custo real acumulado com base no tipo de recorrência.
     * Para anuais/trimestrais, usa o valor unitário × número de cobranças efetivas.
     */
    private BigDecimal calcularCustoAcumulado(Assinatura assinatura, long mesesAtivos) {
        BigDecimal valor = assinatura.getValor();
        if (valor == null || valor.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        return switch (assinatura.getTipoRecorrencia()) {
            case ANUAL -> {
                long cobrancas = mesesAtivos / 12;
                if (mesesAtivos % 12 > 0) cobrancas++;
                yield valor.multiply(BigDecimal.valueOf(cobrancas));
            }
            case TRIMESTRAL -> {
                long cobrancas = mesesAtivos / 3;
                if (mesesAtivos % 3 > 0) cobrancas++;
                yield valor.multiply(BigDecimal.valueOf(cobrancas));
            }
            case PERSONALIZADO -> {
                if (assinatura.getFrequencia() == null || assinatura.getUnidadeFrequencia() == null) {
                    yield valor.multiply(BigDecimal.valueOf(mesesAtivos));
                }
                long mesesPorCobranca = switch (assinatura.getUnidadeFrequencia()) {
                    case ANOS -> assinatura.getFrequencia() * 12L;
                    case MESES -> assinatura.getFrequencia();
                    case SEMANAS -> Math.max(1, assinatura.getFrequencia() / 4);
                };
                long cobrancas = mesesAtivos / mesesPorCobranca;
                if (mesesAtivos % mesesPorCobranca > 0) cobrancas++;
                yield valor.multiply(BigDecimal.valueOf(cobrancas));
            }
            default -> valor.multiply(BigDecimal.valueOf(mesesAtivos));
        };
    }
    // ═══════════════════════════════════════════════════════════════════

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
            default -> valor;
        };
    }

    private BigDecimal calcularFaturaTotalMensal(UUID usuarioId) {
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        List<Fatura> faturasMes = faturaRepository.findByUsuarioId(usuarioId)
                .stream()
                .filter(f -> !f.getMesReferencia().isBefore(inicioMes))
                .toList();

        return faturasMes.stream()
                .map(Fatura::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ═══════════════════════════════════════════════════════════════════
    // MENSAGEM
    // ═══════════════════════════════════════════════════════════════════

    private String montarMensagemFadiga(int total, BigDecimal totalGeral, BigDecimal faturaTotal,
                                         BigDecimal essenciais, BigDecimal importantes, BigDecimal discricionarias,
                                         Map<String, Long> duplicadas, List<String> servicosSemelhantes,
                                         double indice) {
        StringBuilder msg = new StringBuilder();
        msg.append(String.format("Você possui %d assinaturas ativas, totalizando %s por mês",
                total, formatarValor(totalGeral)));

        if (faturaTotal.compareTo(BigDecimal.ZERO) > 0) {
            msg.append(String.format(", o equivalente a %.0f%% da sua Fatura Total mensal.", indice));
        } else {
            msg.append(".");
        }

        BigDecimal totalNaoZero = essenciais.add(importantes).add(discricionarias);
        if (totalNaoZero.compareTo(BigDecimal.ZERO) > 0) {
            msg.append("\n\nDistribuição:");
            if (essenciais.compareTo(BigDecimal.ZERO) > 0) {
                double pctE = essenciais.divide(totalNaoZero, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
                msg.append(String.format("\n🟢 Essenciais: %s (%.0f%%)", formatarValor(essenciais), pctE));
            }
            if (importantes.compareTo(BigDecimal.ZERO) > 0) {
                double pctI = importantes.divide(totalNaoZero, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
                msg.append(String.format("\n🟡 Importantes: %s (%.0f%%)", formatarValor(importantes), pctI));
            }
            if (discricionarias.compareTo(BigDecimal.ZERO) > 0) {
                double pctD = discricionarias.divide(totalNaoZero, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
                msg.append(String.format("\n🔴 Discricionárias: %s (%.0f%%)", formatarValor(discricionarias), pctD));
            }
        }

        if (!duplicadas.isEmpty() || !servicosSemelhantes.isEmpty()) {
            msg.append("\n\nIdentificamos:");
            duplicadas.forEach((cat, qtd) ->
                    msg.append(String.format("\n• %d serviços na categoria \"%s\"", qtd, cat)));
            servicosSemelhantes.forEach(s ->
                    msg.append(String.format("\n• Serviços semelhantes: %s", s)));
        }

        if (discricionarias.compareTo(BigDecimal.ZERO) > 0) {
            msg.append(String.format("\n\nUma revisão das assinaturas discricionárias pode liberar aproximadamente %s por mês, sem comprometer serviços considerados essenciais.",
                    formatarValor(discricionarias)));
        }

        return msg.toString();
    }

    private String formatarValor(BigDecimal valor) {
        return String.format("R$ %.2f", valor);
    }

    // ═══════════════════════════════════════════════════════════════════
    // EFEITO DOMINO — PREVENÇÃO DE FALHA DE COBRANÇA
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Detecta assinaturas que compartilham o mesmo cartão e cujas cobranças
     * combinadas excedem o limite disponível. Gera um insight preventivo
     * classificando o impacto (ALTO/MEDIO/BAIXO).
     */
    public void processarEfeitoDominio(Usuario usuario) {
        UUID usuarioId = usuario.getId();
        LocalDate hoje = LocalDate.now();
        LocalDate fimJanela = hoje.plusDays(5);

        List<Assinatura> proximas = assinaturaRepository
                .findProximasCobrançasPorUsuario(usuarioId, hoje, fimJanela);

        if (proximas.isEmpty()) {
            limparInsightsDominioExistentes(usuarioId);
            return;
        }

        // Agrupar por cartão
        Map<UUID, List<Assinatura>> porCartao = proximas.stream()
                .collect(Collectors.groupingBy(a -> a.getCartao().getId()));

        List<IaInsight> insightsExistentes = iaInsightRepository
                .findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId)
                .stream()
                .filter(ins -> ins.getTipo() == TipoInsight.EFEITO_DOMINO)
                .collect(Collectors.toList());

        List<AlertaCartao> alertas = new ArrayList<>();

        for (Map.Entry<UUID, List<Assinatura>> entry : porCartao.entrySet()) {
            UUID cartaoId = entry.getKey();
            List<Assinatura> assinaturasCartao = entry.getValue();

            Optional<Cartao> cartaoOpt = cartaoRepository.findById(cartaoId);
            if (cartaoOpt.isEmpty()) continue;
            Cartao cartao = cartaoOpt.get();

            BigDecimal limiteDisponivel = cartao.getLimiteDisponivel();

            // Ordenar por essencialidade (ESSENCIAL -> IMPORTANTE -> ORDEM -> data)
            assinaturasCartao.sort(Comparator
                    .comparingInt((Assinatura a) -> ordinalEssencialidade(a, usuarioId))
                    .thenComparing(Assinatura::getDataProximaCobranca));

            // Simular consumo sequencial
            BigDecimal acumulado = BigDecimal.ZERO;
            int essenciaisAfetadas = 0;
            int importantesAfetadas = 0;
            int opcionaisAfetadas = 0;
            List<ItemRanking> ranking = new ArrayList<>();

            for (Assinatura a : assinaturasCartao) {
                boolean falha = acumulado.add(a.getValor()).compareTo(limiteDisponivel) > 0;
                acumulado = acumulado.add(a.getValor());

                NivelEssencialidade ess = obterEssencialidade(a, usuarioId);
                String essStr = ess != null ? ess.name() : "OPCIONAL";

                if (falha) {
                    switch (essStr) {
                        case "ESSENCIAL" -> essenciaisAfetadas++;
                        case "IMPORTANTE" -> importantesAfetadas++;
                        default -> opcionaisAfetadas++;
                    }
                }

                ranking.add(new ItemRanking(
                        a.getId(), a.getNome(), a.getValor(),
                        essStr, a.getDataProximaCobranca(), falha));
            }

            // Só gerar alerta se ESSENCIAL ou IMPORTANTE afetada
            if (essenciaisAfetadas == 0 && importantesAfetadas == 0) continue;

            String nivelAlerta;
            if (essenciaisAfetadas > 0) nivelAlerta = "ALTO";
            else if (importantesAfetadas > 0) nivelAlerta = "MEDIO";
            else nivelAlerta = "BAIXO";

            // Dias até a primeira cobrança
            int diasRestantes = (int) ChronoUnit.DAYS.between(hoje,
                    assinaturasCartao.get(0).getDataProximaCobranca());

            List<String> recomendacoes = gerarRecomendacoes(ranking, cartao.getNome());

            alertas.add(new AlertaCartao(
                    cartaoId, cartao.getNome(), limiteDisponivel,
                    acumulado, diasRestantes,
                    essenciaisAfetadas, importantesAfetadas, opcionaisAfetadas,
                    nivelAlerta, ranking, recomendacoes));
        }

        // Upsert: um insight por cartão em risco
        for (AlertaCartao alerta : alertas) {
            String cartaoIdStr = alerta.cartaoId().toString();
            Optional<IaInsight> existente = insightsExistentes.stream()
                    .filter(ins -> ins.getMetadados() != null
                            && ins.getMetadados().contains(cartaoIdStr))
                    .findFirst();

            String titulo = String.format("Risco de falha: %s", alerta.cartaoNome());
            String mensagem = montarMensagemDominio(alerta);

            // Serializar alerta como metadados
            String metadadosStr;
            try {
                metadadosStr = objectMapper.writeValueAsString(alerta);
            } catch (Exception e) {
                metadadosStr = String.format(
                        "{\"cartaoId\":\"%s\",\"cartaoNome\":\"%s\",\"nivelAlerta\":\"%s\"}",
                        alerta.cartaoId(), alerta.cartaoNome(), alerta.nivelAlerta());
            }

            if (existente.isPresent()) {
                IaInsight ins = existente.get();
                ins.setTitulo(titulo);
                ins.setMensagem(mensagem);
                ins.setMetadados(metadadosStr);
                ins.setLido(false);
                iaInsightRepository.save(ins);
            } else {
                IaInsight insight = new IaInsight(usuario, TipoInsight.EFEITO_DOMINO,
                        titulo, mensagem, metadadosStr);
                try {
                    iaInsightRepository.save(insight);
                } catch (Exception e) {
                    // Race condition
                }
            }
        }

        // Limpar insights de cartões que não estão mais em risco
        Set<UUID> cartoesAlerta = alertas.stream()
                .map(AlertaCartao::cartaoId).collect(Collectors.toSet());
        for (IaInsight ins : insightsExistentes) {
            if (ins.getMetadados() != null) {
                try {
                    com.fasterxml.jackson.databind.JsonNode node =
                            objectMapper.readTree(ins.getMetadados());
                    UUID cartaoIdInsight = UUID.fromString(node.get("cartaoId").asText());
                    if (!cartoesAlerta.contains(cartaoIdInsight)) {
                        ins.setLido(true);
                        iaInsightRepository.save(ins);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private int ordinalEssencialidade(Assinatura a, UUID usuarioId) {
        NivelEssencialidade ess = obterEssencialidade(a, usuarioId);
        return switch (ess != null ? ess : NivelEssencialidade.OPCIONAL) {
            case ESSENCIAL -> 0;
            case IMPORTANTE -> 1;
            case OPCIONAL, DISCRICIONARIA -> 2;
        };
    }

    private NivelEssencialidade obterEssencialidade(Assinatura a, UUID usuarioId) {
        Optional<IaClassificacaoAssinatura> classifOpt =
                classificacaoRepository.findByAssinaturaId(a.getId());
        if (classifOpt.isEmpty()) return NivelEssencialidade.OPCIONAL;
        IaClassificacaoAssinatura classif = classifOpt.get();
        return classif.isConfirmado()
                ? (classif.getRespostaUsuario() != null
                        ? classif.getRespostaUsuario() : classif.getEssencialidade())
                : classif.getEssencialidade();
    }

    private List<String> gerarRecomendacoes(List<ItemRanking> ranking, String cartaoNome) {
        List<String> recs = new ArrayList<>();
        boolean temEssencial = ranking.stream()
                .anyMatch(r -> r.falha() && "ESSENCIAL".equals(r.essencialidade()));
        boolean temImportante = ranking.stream()
                .anyMatch(r -> r.falha() && "IMPORTANTE".equals(r.essencialidade()));

        if (temEssencial) {
            ranking.stream().filter(r -> r.falha() && "ESSENCIAL".equals(r.essencialidade()))
                    .findFirst().ifPresent(r ->
                            recs.add(String.format("Priorize o pagamento do %s (ESSENCIAL).", r.nome())));
        }
        if (temImportante) {
            ranking.stream().filter(r -> r.falha() && "IMPORTANTE".equals(r.essencialidade()))
                    .findFirst().ifPresent(r ->
                            recs.add(String.format("Considere alterar o cartão do %s para outro com limite disponível.", r.nome())));
        }
        long totalAfetadas = ranking.stream().filter(ItemRanking::falha).count();
        if (totalAfetadas > 1) {
            recs.add("Reorganizar o método de pagamento agora evita a interrupção de serviços essenciais.");
        }
        recs.add(String.format("Libere limite no cartão %s ou antecipe o pagamento da fatura.", cartaoNome));
        return recs;
    }

    private String montarMensagemDominio(AlertaCartao alerta) {
        long afetadas = alerta.ranking().stream().filter(ItemRanking::falha).count();
        return String.format(
                "Nos próximos %d dias, %d assinatura(s) utilizarão o cartão %s, " +
                "totalizando %s. O limite disponível é de %s. " +
                "Se nenhuma ação for tomada, %d assinatura(s) poderão falhar.",
                alerta.diasRestantes(),
                alerta.ranking().size(), alerta.cartaoNome(),
                formatarValor(alerta.totalCobranca()),
                formatarValor(alerta.limiteDisponivel()),
                afetadas);
    }

    private void limparInsightsDominioExistentes(UUID usuarioId) {
        List<IaInsight> existentes = iaInsightRepository
                .findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId)
                .stream()
                .filter(ins -> ins.getTipo() == TipoInsight.EFEITO_DOMINO)
                .toList();
        for (IaInsight ins : existentes) {
            ins.setLido(true);
            iaInsightRepository.save(ins);
        }
    }
}
