package com.financetracker.ia.controller;

import com.financetracker.ia.domain.IaInsight;
import com.financetracker.ia.dto.ProjecaoCartoesResponse;
import com.financetracker.ia.repository.IaInsightRepository;
import com.financetracker.ia.service.IaService;
import com.financetracker.ia.service.IaServiceCartao;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/ia")
public class IaController {

    private final IaService iaService;
    private final IaServiceCartao iaServiceCartao;
    private final IaInsightRepository iaInsightRepository;
    private final UsuarioRepository usuarioRepository;

    public IaController(IaService iaService,
                        IaServiceCartao iaServiceCartao,
                        IaInsightRepository iaInsightRepository,
                        UsuarioRepository usuarioRepository) {
        this.iaService = iaService;
        this.iaServiceCartao = iaServiceCartao;
        this.iaInsightRepository = iaInsightRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private Optional<Usuario> getUsuario(UserDetails userDetails) {
        return usuarioRepository.findByEmail(userDetails.getUsername());
    }

    // ─── RN-07 / RN-12: Categorização PLN ────────────────────────────

    @PostMapping("/categorizar")
    public ResponseEntity<?> categorizar(@RequestBody Map<String, String> request,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        String descricao = request.get("descricaoFatura");
        if (descricao == null || descricao.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "O campo 'descricaoFatura' é obrigatório."));
        }

        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        Map<String, Object> resultado = iaService.categorizarTransacao(descricao, usuarioOpt.get().getId());
        return ResponseEntity.ok(resultado);
    }

    // ─── RN-14: Feedback Loop / Correção de Categoria ─────────────────

    /**
     * Registra correção manual do usuário para alimentar o loop de aprendizado (RN-14).
     * Deve ser chamado pelo frontend quando o usuário edita a categoria de uma transação.
     *
     * Body: { "descricaoFatura": "UBER *EATS", "categoriaAntigaId": "uuid", "categoriaNovaId": "uuid" }
     */
    @PostMapping("/correcao")
    public ResponseEntity<?> registrarCorrecao(@RequestBody Map<String, String> request,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        String descricao = request.get("descricaoFatura");
        String categoriaAntigaIdStr = request.get("categoriaAntigaId");
        String categoriaNovaIdStr = request.get("categoriaNovaId");

        if (descricao == null || categoriaNovaIdStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "'descricaoFatura' e 'categoriaNovaId' são obrigatórios."));
        }

        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        try {
            UUID categoriaAntigaId = categoriaAntigaIdStr != null ? UUID.fromString(categoriaAntigaIdStr) : null;
            UUID categoriaNovaId = UUID.fromString(categoriaNovaIdStr);
            iaService.registrarCorrecaoManual(usuarioOpt.get().getId(), descricao, categoriaAntigaId, categoriaNovaId, usuarioOpt.get());
            return ResponseEntity.ok(Map.of("success", true, "message", "Correção registrada e cache atualizado."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "IDs de categoria inválidos."));
        }
    }

    // ─── Insights: listar, marcar como lido, feedback ─────────────────

    @GetMapping("/insights")
    public ResponseEntity<?> getInsights(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        List<IaInsight> insights = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioOpt.get().getId());
        return ResponseEntity.ok(insights);
    }

    @PutMapping("/insights/{id}/ler")
    public ResponseEntity<?> marcarComoLido(@PathVariable UUID id,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        Optional<IaInsight> insightOpt = iaInsightRepository.findById(id);
        if (insightOpt.isEmpty() || !insightOpt.get().getUsuario().getId().equals(usuarioOpt.get().getId())) {
            return ResponseEntity.status(404).body(Map.of("error", "Insight não encontrado."));
        }

        IaInsight insight = insightOpt.get();
        insight.setLido(true);
        iaInsightRepository.save(insight);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/insights/{id}/feedback")
    public ResponseEntity<?> darFeedback(@PathVariable UUID id,
                                         @RequestBody Map<String, Boolean> request,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        Boolean relevante = request.get("relevante");
        if (relevante == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "O campo 'relevante' é obrigatório."));
        }

        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        Optional<IaInsight> insightOpt = iaInsightRepository.findById(id);
        if (insightOpt.isEmpty() || !insightOpt.get().getUsuario().getId().equals(usuarioOpt.get().getId())) {
            return ResponseEntity.status(404).body(Map.of("error", "Insight não encontrado."));
        }

        IaInsight insight = insightOpt.get();
        insight.setRelevante(relevante);
        // Feedback negativo: marcar como lido automaticamente para não poluir o feed
        if (!relevante) {
            insight.setLido(true);
        }
        iaInsightRepository.save(insight);

        return ResponseEntity.ok(Map.of("success", true));
    }

    // ─── Disparo manual de processamento ──────────────────────────────

    @PostMapping("/insights/processar")
    public ResponseEntity<?> processarInsights(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        iaService.processarInsightsParaUsuario(usuarioOpt.get());
        return ResponseEntity.ok(Map.of("message", "Insights de IA reprocessados com sucesso!"));
    }

    @PostMapping("/insights/processar/assinatura")
    public ResponseEntity<?> processarInsightsAssinatura(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        iaService.processarInsightsAssinaturaParaUsuario(usuarioOpt.get());
        return ResponseEntity.ok(Map.of("message", "Insights de Assinaturas reprocessados com sucesso!"));
    }

    // ─── Todos os Insights de Cartão (chamada atômica única) ─────────────

    /**
     * Endpoint dedicado: processa TODOS os insights de cartão em uma única chamada.
     * Substitui as 4 chamadas sequenciais (aviso-fechamento, melhor-cartao, etc.)
     * Eliminando o problema de React StrictMode executar múltiplas vezes.
     * Chamado ao entrar na tela /cartoes.
     */
    @PostMapping("/insights/cartao")
    public ResponseEntity<?> processarTodosInsightsCartao(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        // Processa insights dedicados de cartão (AVISO_FECHAMENTO, MELHOR_CARTAO, etc.)
        iaServiceCartao.processarTodosInsightsCartao(usuarioOpt.get());
        return ResponseEntity.ok(Map.of("message", "Insights de Cartões processados com sucesso!"));
    }

    // ─── Projeção de Faturas (dedicada) ────────────────────────────────

    /**
     * Endpoint dedicado para projeção/comparação de faturas de TODOS os cartões.
     * Chamado ao entrar em /cartoes, ao cadastrar transação, e ao clicar "Analisar com IA".
     */
    @PostMapping("/projecao-cartoes")
    public ResponseEntity<?> projetarCartoes(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        ProjecaoCartoesResponse response = iaServiceCartao.projetarFaturasParaUsuario(usuarioOpt.get());
        return ResponseEntity.ok(response);
    }

    // ─── Aviso de Fechamento Iminente (dedicado) ──────────────────────

    /**
     * Endpoint dedicado para alertar sobre fechamento de fatura em 0-5 dias.
     * Chamado ao entrar na tela /cartoes.
     */
    @PostMapping("/aviso-fechamento")
    public ResponseEntity<?> verificarAvisosFechamento(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        iaServiceCartao.processarAvisoFechamentoParaUsuario(usuarioOpt.get());
        return ResponseEntity.ok(Map.of("message", "Avisos de fechamento processados com sucesso!"));
    }

    // ─── Melhor Cartão para o Momento (dedicado) ────────────────────

    /**
     * Endpoint dedicado para identificar o melhor cartão para usar agora
     * com base no fluxo de caixa (dias até o próximo fechamento).
     * Chamado ao entrar na tela /cartoes.
     */
    @PostMapping("/melhor-cartao")
    public ResponseEntity<?> verificarMelhorCartao(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        iaServiceCartao.processarMelhorCartaoParaUsuario(usuarioOpt.get());
        return ResponseEntity.ok(Map.of("message", "Melhor cartão analisado com sucesso!"));
    }

    // ─── Concentração de Gastos na Fatura (Category Spike) ────────────

    /**
     * Endpoint dedicado: analisa a composição da fatura ABERTA por categoria.
     * Se uma categoria representar mais de 50% do total da fatura e não for
     * essencial, gera um insight de alerta.
     * Chamado ao entrar na tela /cartoes.
     */
    @PostMapping("/concentracao-gastos-fatura")
    public ResponseEntity<?> verificarConcentracaoGastos(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        iaServiceCartao.processarConcentracaoGastosFaturaParaUsuario(usuarioOpt.get());
        return ResponseEntity.ok(Map.of("message", "Análise de concentração de gastos processada com sucesso!"));
    }

    // ─── Otimização de Parcelamentos Futuros (Folga de Limite) ────────

    /**
     * Endpoint dedicado: identifica parcelamentos que terminam no mês atual
     * e informa o usuário que aquele valor ficará "livre" no orçamento.
     * Chamado ao entrar na tela /cartoes.
     */
    @PostMapping("/otimizacao-parcelamento")
    public ResponseEntity<?> verificarOtimizacaoParcelamento(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        iaServiceCartao.processarOtimizacaoParcelamentoParaUsuario(usuarioOpt.get());
        return ResponseEntity.ok(Map.of("message", "Análise de otimização de parcelamentos processada com sucesso!"));
    }

}
