package com.financetracker.transacao.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record MetaEconomiaDepositoRequest(
    @NotNull @DecimalMin("0.01") BigDecimal valor,
    @NotNull UUID contaOrigemId
) {}
