package com.financetracker.transacao.controller;

import com.financetracker.transacao.dto.MetaEconomiaCriacaoRequest;
import com.financetracker.transacao.dto.MetaEconomiaDepositoRequest;
import com.financetracker.transacao.dto.MetaEconomiaResgateRequest;
import com.financetracker.transacao.dto.MetaEconomiaResponse;
import com.financetracker.transacao.service.MetasEconomiaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/metas")
public class MetasEconomiaController {

    private final MetasEconomiaService metasService;

    public MetasEconomiaController(MetasEconomiaService metasService) {
        this.metasService = metasService;
    }

    @PostMapping
    public ResponseEntity<MetaEconomiaResponse> criar(@Valid @RequestBody MetaEconomiaCriacaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(metasService.criar(request));
    }

    @PostMapping("/{id}/deposito")
    public ResponseEntity<MetaEconomiaResponse> depositar(
            @PathVariable UUID id,
            @Valid @RequestBody MetaEconomiaDepositoRequest request) {
        return ResponseEntity.ok(metasService.depositar(id, request));
    }

    @PostMapping("/{id}/resgate")
    public ResponseEntity<MetaEconomiaResponse> resgatar(
            @PathVariable UUID id,
            @Valid @RequestBody MetaEconomiaResgateRequest request) {
        return ResponseEntity.ok(metasService.resgatar(id, request));
    }

    @GetMapping
    public ResponseEntity<List<MetaEconomiaResponse>> listar() {
        return ResponseEntity.ok(metasService.listar());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        metasService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}