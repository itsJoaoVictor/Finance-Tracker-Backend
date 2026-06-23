package com.financetracker.transacao.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record EstornoRequest(
    @NotNull UUID transacaoId,
    BigDecimal valor
) {}