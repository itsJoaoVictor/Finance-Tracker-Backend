package com.financetracker.ia.controller;

import com.financetracker.ia.domain.IaInsight;
import com.financetracker.ia.repository.IaInsightRepository;
import com.financetracker.ia.service.IaService;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/categorizar")
    public ResponseEntity<?> categorizar(@RequestBody Map<String, String> request,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        String descricao = request.get("descricaoFatura");
        if (descricao == null || descricao.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "O campo 'descricaoFatura' é obrigatório."));
        }

        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(userDetails.getUsername());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        Map<String, Object> resultado = iaService.categorizarTransacao(descricao, usuarioOpt.get().getId());
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/insights")
    public ResponseEntity<?> getInsights(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(userDetails.getUsername());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        List<IaInsight> insights = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioOpt.get().getId());
        return ResponseEntity.ok(insights);
    }

    @PutMapping("/insights/{id}/ler")
    public ResponseEntity<?> marcarComoLido(@PathVariable UUID id,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(userDetails.getUsername());
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

        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(userDetails.getUsername());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        Optional<IaInsight> insightOpt = iaInsightRepository.findById(id);
        if (insightOpt.isEmpty() || !insightOpt.get().getUsuario().getId().equals(usuarioOpt.get().getId())) {
            return ResponseEntity.status(404).body(Map.of("error", "Insight não encontrado."));
        }

        IaInsight insight = insightOpt.get();
        insight.setRelevante(relevante);
        iaInsightRepository.save(insight);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/insights/processar")
    public ResponseEntity<?> processarInsights(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(userDetails.getUsername());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        iaService.processarInsightsParaUsuario(usuarioOpt.get());
        return ResponseEntity.ok(Map.of("message", "Insights de IA reprocessados com sucesso!"));
    }

    @PostMapping("/insights/processar/cartao")
    public ResponseEntity<?> processarInsightsCartao(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(userDetails.getUsername());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        // Processa as regras específicas de cartão (RN-01, RN-03, RN-11)
        iaService.processarInsightsCartaoParaUsuario(usuarioOpt.get());
        return ResponseEntity.ok(Map.of("message", "Insights de Cartões reprocessados com sucesso!"));
    }

    @PostMapping("/insights/processar/assinatura")
    public ResponseEntity<?> processarInsightsAssinatura(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(userDetails.getUsername());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuário não autenticado."));
        }

        // Processa as regras específicas de assinatura (RN-05, RN-06)
        iaService.processarInsightsAssinaturaParaUsuario(usuarioOpt.get());
        return ResponseEntity.ok(Map.of("message", "Insights de Assinaturas reprocessados com sucesso!"));
    }
}
