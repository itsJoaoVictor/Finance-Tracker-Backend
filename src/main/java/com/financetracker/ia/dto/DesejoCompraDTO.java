package com.financetracker.ia.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DesejoCompraDTO(
    UUID id,
    String nome,
    BigDecimal valor
) {}
