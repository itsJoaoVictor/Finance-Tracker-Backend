package com.financetracker.contapagar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FinanciamentoRequest(
        @NotBlank(message = "Nome do financiamento é obrigatório")
        @Size(max = 120)
        String nome,

        @Size(max = 255)
        String descricao,

        @Size(max = 7)
        String corHexadecimal,

        BigDecimal valorPagoAnteriormente,

        BigDecimal valorContratado
) {}
