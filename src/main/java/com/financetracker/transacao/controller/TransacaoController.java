package com.financetracker.transacao.controller;

import com.financetracker.transacao.dto.*;
import com.financetracker.transacao.service.TagService;
import com.financetracker.transacao.service.TransacaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transacoes")
public class TransacaoController {

    private final TransacaoService transacaoService;
    private final TagService tagService;

    public TransacaoController(TransacaoService transacaoService, TagService tagService) {
        this.transacaoService = transacaoService;
        this.tagService = tagService;
    }

    @PostMapping
    public ResponseEntity<TransacaoResponse> criar(@Valid @RequestBody TransacaoCriacaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transacaoService.criar(request));
    }

    @PostMapping("/transferir")
    public ResponseEntity<TransacaoResponse> transferir(@Valid @RequestBody TransferenciaRequest request) {
        return ResponseEntity.ok(transacaoService.transferir(request));
    }

    @PostMapping("/pagar-fatura")
    public ResponseEntity<TransacaoResponse> pagarFatura(@Valid @RequestBody PagamentoFaturaRequest request) {
        return ResponseEntity.ok(transacaoService.pagarFatura(request));
    }

    @PostMapping("/{id}/estornar")
    public ResponseEntity<TransacaoResponse> estornar(
            @PathVariable UUID id,
            @Valid @RequestBody EstornoRequest request) {
        return ResponseEntity.ok(transacaoService.estornar(id, request));
    }

    @GetMapping
    public ResponseEntity<List<TransacaoResponse>> listar() {
        return ResponseEntity.ok(transacaoService.listar());
    }

    @GetMapping("/sugestao")
    public ResponseEntity<SugestaoResponse> sugerir(@RequestParam String descricao) {
        SugestaoResponse sugestao = tagService.sugerir(descricao);
        if (sugestao == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(sugestao);
    }

    @GetMapping("/projecao")
    public ResponseEntity<List<ProjecaoResponse>> projetar(@RequestParam(defaultValue = "30") int dias) {
        return ResponseEntity.ok(transacaoService.projetar(dias));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        transacaoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}