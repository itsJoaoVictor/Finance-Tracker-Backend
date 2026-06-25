package com.financetracker.ia.controller;

import com.financetracker.ia.domain.IaInsight;
import com.financetracker.ia.dto.ProjecaoCartoesResponse;
import com.financetracker.ia.repository.IaInsightRepository;
import com.financetracker.ia.service.IaService;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/ia")
public class IaController {

    private final IaService iaService;
    private final IaInsightRepository iaInsightRepository;
    private final UsuarioRepository usuarioRepository;

    public IaController(IaService iaService,
                        IaInsightRepository iaInsightRepository,
                        UsuarioRepository usuarioRepository) {
        this.iaService = iaService;
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

    @PostMapping("/insights/processar/cartao")
    public ResponseEntity<?> processarInsightsCartao(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        // Processa RN-01, RN-04, RN-11
        iaService.processarInsightsCartaoParaUsuario(usuarioOpt.get());
        return ResponseEntity.ok(Map.of("message", "Insights de Cartões reprocessados com sucesso!"));
    }

    @PostMapping("/insights/processar/assinatura")
    public ResponseEntity<?> processarInsightsAssinatura(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        // Processa RN-05, RN-06
        iaService.processarInsightsAssinaturaParaUsuario(usuarioOpt.get());
        return ResponseEntity.ok(Map.of("message", "Insights de Assinaturas reprocessados com sucesso!"));
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

        ProjecaoCartoesResponse response = iaService.projetarFaturasParaUsuario(usuarioOpt.get());
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

        iaService.processarAvisoFechamentoParaUsuario(usuarioOpt.get());
        return ResponseEntity.ok(Map.of("message", "Avisos de fechamento processados com sucesso!"));
    }

    // ─── RN-03: Simulação de Compra Parcelada ─────────────────────────

    /**
     * Simula o impacto de uma compra parcelada nas próximas faturas.
     * Body: { "valorTotal": 1200.00, "parcelas": 10, "cartaoId": "uuid" }
     */
    @PostMapping("/simular-parcela")
    public ResponseEntity<?> simularParcela(@RequestBody Map<String, Object> request,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = getUsuario(userDetails);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        try {
            BigDecimal valorTotal = new BigDecimal(request.get("valorTotal").toString());
            int parcelas = Integer.parseInt(request.get("parcelas").toString());
            UUID cartaoId = UUID.fromString(request.get("cartaoId").toString());

            if (parcelas < 1 || parcelas > 48) {
                return ResponseEntity.badRequest().body(Map.of("error", "Número de parcelas deve ser entre 1 e 48."));
            }
            if (valorTotal.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Valor total deve ser positivo."));
            }

            Map<String, Object> resultado = iaService.simularCompraParcela(
                    cartaoId, valorTotal, parcelas, usuarioOpt.get().getId());
            return ResponseEntity.ok(resultado);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Parâmetros inválidos: " + e.getMessage()));
        }
    }
}
