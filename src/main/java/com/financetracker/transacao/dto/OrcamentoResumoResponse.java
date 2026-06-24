package com.financetracker.transacao.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrcamentoResumoResponse(
    UUID id,
    UUID categoriaId,
    String categoriaNome,
    BigDecimal limiteMensal,
    BigDecimal totalGasto
) {}