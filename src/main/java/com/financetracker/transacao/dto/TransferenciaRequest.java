package com.financetracker.transacao.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferenciaRequest(
    @NotNull UUID contaOrigemId,
    @NotNull UUID contaDestinoId,
    @NotNull @jakarta.validation.constraints.DecimalMin("0.01") BigDecimal valor,
    String descricao,
    @NotNull UUID categoriaId
) {}