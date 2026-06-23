package com.financetracker.transacao.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record MetaEconomiaCriacaoRequest(
    @NotBlank @Size(max = 100) String nome,
    @NotNull @DecimalMin("0.01") BigDecimal valorAlvo,
    @NotNull UUID contaVinculadaId
) {}