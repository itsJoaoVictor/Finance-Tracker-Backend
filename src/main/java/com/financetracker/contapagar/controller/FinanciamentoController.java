package com.financetracker.contapagar.controller;

import com.financetracker.contapagar.dto.*;
import com.financetracker.contapagar.service.FinanciamentoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/financiamentos")
public class FinanciamentoController {

    private final FinanciamentoService financiamentoService;

    public FinanciamentoController(FinanciamentoService financiamentoService) {
        this.financiamentoService = financiamentoService;
    }

    @GetMapping
    public ResponseEntity<List<FinanciamentoResumoResponse>> listar() {
        return ResponseEntity.ok(financiamentoService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FinanciamentoResumoResponse> obterPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(financiamentoService.obterPorId(id));
    }

    @PostMapping
    public ResponseEntity<FinanciamentoResumoResponse> criar(@Valid @RequestBody FinanciamentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(financiamentoService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FinanciamentoResumoResponse> editar(
            @PathVariable UUID id,
            @Valid @RequestBody FinanciamentoRequest request) {
        return ResponseEntity.ok(financiamentoService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        financiamentoService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/disponiveis")
    public ResponseEntity<ItensDisponiveisVinculoResponse> listarItensDisponiveis() {
        return ResponseEntity.ok(financiamentoService.listarItensDisponiveis());
    }

    @PostMapping("/{id}/vincular")
    public ResponseEntity<FinanciamentoResumoResponse> vincularItens(
            @PathVariable UUID id,
            @RequestBody VincularItensFinanciamentoRequest request) {
        return ResponseEntity.ok(financiamentoService.vincularItens(id, request));
    }

    @DeleteMapping("/{id}/modelos/{modeloId}")
    public ResponseEntity<FinanciamentoResumoResponse> desvincularModelo(
            @PathVariable UUID id,
            @PathVariable UUID modeloId) {
        return ResponseEntity.ok(financiamentoService.desvincularModelo(id, modeloId));
    }

    @DeleteMapping("/{id}/transacoes/{transacaoId}")
    public ResponseEntity<FinanciamentoResumoResponse> desvincularTransacao(
            @PathVariable UUID id,
            @PathVariable UUID transacaoId) {
        return ResponseEntity.ok(financiamentoService.desvincularTransacao(id, transacaoId));
    }
}
