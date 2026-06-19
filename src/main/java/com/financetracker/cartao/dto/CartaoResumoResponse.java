package com.financetracker.cartao.dto;

import java.math.BigDecimal;

public record CartaoResumoResponse(
    BigDecimal totalLimite,
    BigDecimal totalLimiteDisponivel,
    BigDecimal totalFaturaEstimada,
    long quantidadeCartoes
) {}
