package com.financetracker.conta.controller;

import com.financetracker.conta.dto.*;
import com.financetracker.conta.service.ContaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/contas")
public class ContaController {

    private final ContaService contaService;

    public ContaController(ContaService contaService) {
        this.contaService = contaService;
    }

    @PostMapping
    public ResponseEntity<ContaResponse> criar(@Valid @RequestBody ContaCriacaoRequest request) {
        ContaResponse response = contaService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ContaResponse>> listar() {
        return ResponseEntity.ok(contaService.listar());
    }

    // IMPORTANTE: /resumo ANTES de /{id} para não conflitar
    @GetMapping("/resumo")
    public ResponseEntity<ContaResumoResponse> resumo() {
        return ResponseEntity.ok(contaService.resumo());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContaResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(contaService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContaResponse> editar(
            @PathVariable UUID id,
            @Valid @RequestBody ContaEdicaoRequest request
    ) {
        return ResponseEntity.ok(contaService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        contaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
