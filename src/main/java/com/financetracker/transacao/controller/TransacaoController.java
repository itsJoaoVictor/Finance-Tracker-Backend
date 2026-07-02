package com.financetracker.transacao.controller;

import com.financetracker.transacao.dto.*;
import com.financetracker.transacao.service.TagService;
import com.financetracker.transacao.service.TransacaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.financetracker.transacao.enums.TipoTransacao;
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

    @PostMapping("/{id}/antecipar-parcelas")
    public ResponseEntity<TransacaoResponse> anteciparParcelas(
            @PathVariable UUID id,
            @Valid @RequestBody AnteciparParcelasRequest request) {
        return ResponseEntity.ok(transacaoService.anteciparParcelas(id, request));
    }

    @GetMapping("/fatura/{faturaId}")
    public ResponseEntity<List<TransacaoResponse>> listarPorFatura(@PathVariable UUID faturaId) {
        return ResponseEntity.ok(transacaoService.buscarPorFatura(faturaId));
    }

    @GetMapping
    public ResponseEntity<Page<TransacaoResponse>> listar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String descricao,
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        TipoTransacao tipoEnum = (tipo != null && !tipo.equals("ALL") && !tipo.isBlank()) ? TipoTransacao.valueOf(tipo) : null;
        java.time.LocalDate inicio = (dataInicio != null && !dataInicio.isBlank()) ? java.time.LocalDate.parse(dataInicio) : null;
        java.time.LocalDate fim = (dataFim != null && !dataFim.isBlank()) ? java.time.LocalDate.parse(dataFim) : null;

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(transacaoService.listarPaginado(tipoEnum, descricao, inicio, fim, pageable));
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