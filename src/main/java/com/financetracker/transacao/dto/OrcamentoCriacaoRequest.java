package com.financetracker.transacao.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OrcamentoCriacaoRequest(
    @NotNull UUID categoriaId,
    @DecimalMin("0.01") BigDecimal limiteMensal,
    @NotNull LocalDate mesReferencia
) {}