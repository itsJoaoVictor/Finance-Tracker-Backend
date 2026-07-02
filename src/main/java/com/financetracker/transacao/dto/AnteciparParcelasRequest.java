package com.financetracker.transacao.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AnteciparParcelasRequest(
    @NotNull(message = "A quantidade de parcelas é obrigatória")
    @Min(value = 1, message = "A quantidade deve ser de no mínimo 1 parcela")
    Integer quantidade
) {}
