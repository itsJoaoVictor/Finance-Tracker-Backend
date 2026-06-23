package com.financetracker.transacao.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record PagamentoFaturaRequest(
    @NotNull UUID faturaId,
    @NotNull UUID contaOrigemId,
    @NotNull @jakarta.validation.constraints.DecimalMin("0.01") BigDecimal valor,
    @NotNull String tipoPagamento
) {}