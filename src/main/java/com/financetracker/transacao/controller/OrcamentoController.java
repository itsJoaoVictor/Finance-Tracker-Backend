package com.financetracker.transacao.controller;

import com.financetracker.transacao.dto.OrcamentoCriacaoRequest;
import com.financetracker.transacao.dto.OrcamentoResponse;
import com.financetracker.transacao.dto.OrcamentoResumoResponse;
import com.financetracker.transacao.service.OrcamentoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orcamentos")
public class OrcamentoController {

    private final OrcamentoService orcamentoService;

    public OrcamentoController(OrcamentoService orcamentoService) {
        this.orcamentoService = orcamentoService;
    }

    @PostMapping
    public ResponseEntity<OrcamentoResponse> criarOuAtualizar(@Valid @RequestBody OrcamentoCriacaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orcamentoService.criarOuAtualizar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrcamentoResponse> editar(@PathVariable UUID id, @Valid @RequestBody OrcamentoCriacaoRequest request) {
        return ResponseEntity.ok(orcamentoService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        orcamentoService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/resumo")
    public ResponseEntity<List<OrcamentoResumoResponse>> resumo() {
        return ResponseEntity.ok(orcamentoService.resumo());
    }
}