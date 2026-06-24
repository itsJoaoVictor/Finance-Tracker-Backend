package com.financetracker.relatorio.controller;

import com.financetracker.relatorio.dto.RelatorioCategoriaResponse;
import com.financetracker.relatorio.dto.RelatorioFluxoCaixaResponse;
import com.financetracker.relatorio.service.RelatorioService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    @GetMapping("/categorias")
    public ResponseEntity<RelatorioCategoriaResponse> gastosPorCategoria(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String tipo) {
        return ResponseEntity.ok(relatorioService.obterGastosPorCategoria(dataInicio, dataFim, tipo));
    }

    @GetMapping("/fluxo-caixa")
    public ResponseEntity<List<RelatorioFluxoCaixaResponse>> fluxoCaixa(
            @RequestParam(required = false) Integer anos) {
        return ResponseEntity.ok(relatorioService.obterFluxoCaixa(anos));
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam String formato,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return relatorioService.exportarRelatorio(formato, dataInicio, dataFim);
    }
}