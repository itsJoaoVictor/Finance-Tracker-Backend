package com.financetracker.contapagar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AmortizarRequest(
        @NotNull(message = "Valor da amortização é obrigatório")
        @Positive(message = "Valor deve ser positivo")
        BigDecimal valor,

        @NotNull(message = "Data da amortização é obrigatória")
        LocalDate data,

        @NotBlank(message = "Origem é obrigatória (CONTA ou EXTERNA)")
        String origem,

        UUID contaId,

        @NotBlank(message = "Modo é obrigatório (REDUZIR_PRAZO ou REDUZIR_PARCELA)")
        String modo,

        Integer parcelasEliminadas,

        BigDecimal novoValorParcela,

        String observacao
) {}
