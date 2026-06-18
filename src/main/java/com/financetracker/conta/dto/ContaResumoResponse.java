package com.financetracker.conta.dto;

import java.math.BigDecimal;

public record ContaResumoResponse(
        BigDecimal totalSaldo,
        int quantidadeContas
) {}
