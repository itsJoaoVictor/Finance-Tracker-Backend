package com.financetracker.contapagar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AjustarPrazoRequest(
        @NotBlank(message = "Ação é obrigatória (ENCERRAR, REDUZIR ou ESTENDER)")
        String acao,

        Integer quantidadeParcelas,

        BigDecimal valorParcelasAdicionadas,

        @Size(max = 255)
        String motivo
) {}
