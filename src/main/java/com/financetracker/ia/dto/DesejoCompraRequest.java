package com.financetracker.ia.dto;

import java.math.BigDecimal;

public record DesejoCompraRequest(
    String nome,
    BigDecimal valor
) {}
