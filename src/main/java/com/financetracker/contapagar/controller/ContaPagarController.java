package com.financetracker.contapagar.controller;

import com.financetracker.contapagar.dto.*;
import com.financetracker.contapagar.service.ContaPagarService;
import com.financetracker.contapagar.service.NotificacaoExternaService;
import com.financetracker.contapagar.util.DiaUtilUtils;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/contas-pagar")
public class ContaPagarController {

    private final ContaPagarService contaPagarService;
    private final NotificacaoExternaService notificacaoExternaService;

    public ContaPagarController(ContaPagarService contaPagarService,
                                NotificacaoExternaService notificacaoExternaService) {
        this.contaPagarService = contaPagarService;
        this.notificacaoExternaService = notificacaoExternaService;
    }

    // ── Modelos ──────────────────────────────────────────────────────────────

    @PostMapping("/modelos")
    public ResponseEntity<ContaPagarModeloResponse> criarModelo(
            @Valid @RequestBody ContaPagarModeloCriacaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contaPagarService.criarModelo(request));
    }

    @GetMapping("/modelos")
    public ResponseEntity<List<ContaPagarModeloResponse>> listarModelos() {
        return ResponseEntity.ok(contaPagarService.listarModelos());
    }

    @PutMapping("/modelos/{id}")
    public ResponseEntity<ContaPagarModeloResponse> editarModelo(
            @PathVariable UUID id,
            @Valid @RequestBody ContaPagarModeloEdicaoRequest request) {
        return ResponseEntity.ok(contaPagarService.editarModelo(id, request));
    }

    @DeleteMapping("/modelos/{id}")
    public ResponseEntity<Void> excluirModelo(@PathVariable UUID id) {
        contaPagarService.excluirModelo(id);
        return ResponseEntity.noContent().build();
    }

    // ── Financiamento / Parcelado ────────────────────────────────────────────

    @GetMapping("/modelos/{id}/financiamento")
    public ResponseEntity<FinanciamentoDetalheResponse> obterDetalheFinanciamento(@PathVariable UUID id) {
        return ResponseEntity.ok(contaPagarService.obterDetalheFinanciamento(id));
    }

    @PostMapping("/modelos/{id}/amortizar")
    public ResponseEntity<FinanciamentoDetalheResponse> amortizar(
            @PathVariable UUID id,
            @Valid @RequestBody AmortizarRequest request) {
        return ResponseEntity.ok(contaPagarService.amortizarFinanciamento(id, request));
    }

    @PostMapping("/modelos/{id}/ajustar-prazo")
    public ResponseEntity<FinanciamentoDetalheResponse> ajustarPrazo(
            @PathVariable UUID id,
            @Valid @RequestBody AjustarPrazoRequest request) {
        return ResponseEntity.ok(contaPagarService.ajustarPrazoParcelamento(id, request));
    }

    // ── Grade Mensal & Ocorrências ───────────────────────────────────────────

    @GetMapping("/grade")
    public ResponseEntity<GradeMensalResponse> obterGrade(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mesInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mesFim,
            @RequestParam(required = false) String agrupador) {
        LocalDate hoje = DiaUtilUtils.hoje().withDayOfMonth(1);
        LocalDate inicio = mesInicio != null ? mesInicio : hoje.minusMonths(2);
        LocalDate fim = mesFim != null ? mesFim : hoje.plusMonths(3);
        return ResponseEntity.ok(contaPagarService.obterGradeMensal(inicio, fim, agrupador));
    }

    @GetMapping("/ocorrencias")
    public ResponseEntity<List<ContaPagarOcorrenciaResponse>> listarOcorrenciasMes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mes,
            @RequestParam(required = false) String agrupador) {
        LocalDate mesRef = mes != null ? mes : DiaUtilUtils.hoje().withDayOfMonth(1);
        return ResponseEntity.ok(contaPagarService.listarOcorrenciasMes(mesRef, agrupador));
    }

    @PutMapping("/ocorrencias/{id}")
    public ResponseEntity<ContaPagarOcorrenciaResponse> atualizarOcorrencia(
            @PathVariable UUID id,
            @RequestBody AtualizarOcorrenciaRequest request) {
        return ResponseEntity.ok(contaPagarService.atualizarOcorrencia(id, request));
    }

    @PostMapping("/ocorrencias/{id}/pagar")
    public ResponseEntity<ContaPagarOcorrenciaResponse> pagarOcorrencia(
            @PathVariable UUID id,
            @Valid @RequestBody PagarOcorrenciaRequest request) {
        return ResponseEntity.ok(contaPagarService.pagarOcorrencia(id, request));
    }

    @PostMapping("/ocorrencias/{id}/cancelar")
    public ResponseEntity<ContaPagarOcorrenciaResponse> cancelarOcorrencia(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean cancelar) {
        return ResponseEntity.ok(contaPagarService.alterarStatusCancelamento(id, cancelar));
    }

    // ── Notificações Externas (Telegram & Web Push) ──────────────────────────

    @GetMapping("/notificacoes/config")
    public ResponseEntity<NotificacaoConfigDTO> obterConfigNotificacao() {
        return ResponseEntity.ok(notificacaoExternaService.obterConfig());
    }

    @PutMapping("/notificacoes/config")
    public ResponseEntity<NotificacaoConfigDTO> salvarConfigNotificacao(@RequestBody Map<String, Object> body) {
        String chatId = body.get("telegramChatId") != null ? body.get("telegramChatId").toString() : null;
        Boolean tgAtivo = body.get("telegramAtivo") instanceof Boolean b ? b : null;
        Boolean wpAtivo = body.get("webPushAtivo") instanceof Boolean b ? b : null;
        return ResponseEntity.ok(notificacaoExternaService.salvarConfig(chatId, tgAtivo, wpAtivo));
    }

    @PostMapping("/notificacoes/telegram/testar")
    public ResponseEntity<Map<String, Object>> testarTelegram() {
        boolean enviado = notificacaoExternaService.testarTelegram();
        return ResponseEntity.ok(Map.of("enviado", enviado));
    }

    @PostMapping("/notificacoes/push/subscribe")
    public ResponseEntity<Void> registrarPush(@Valid @RequestBody PushSubscriptionRequest request) {
        notificacaoExternaService.registrarPushSubscription(request);
        return ResponseEntity.ok().build();
    }
}
