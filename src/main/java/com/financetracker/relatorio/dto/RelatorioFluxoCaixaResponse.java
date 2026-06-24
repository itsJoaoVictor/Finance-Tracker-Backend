package com.financetracker.relatorio.dto;

import java.math.BigDecimal;

public record RelatorioFluxoCaixaResponse(
        String mesReferencia,
        BigDecimal totalReceitas,
        BigDecimal totalDespesas,
        BigDecimal saldoLiquido
) {}