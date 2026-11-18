package com.financetracker.contapagar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PagarOcorrenciaRequest(
        @NotBlank(message = "Meio de pagamento é obrigatório (CONTA ou CARTAO)")
        String meio,

        UUID contaId,

        UUID cartaoId,

        @NotNull(message = "Data de pagamento é obrigatória")
        LocalDate dataPagamento,

        @NotNull(message = "Valor pago é obrigatório")
        @Positive(message = "Valor pago deve ser positivo")
        BigDecimal valorPago,

        String linhaDigitavel,

        String observacao
) {}
