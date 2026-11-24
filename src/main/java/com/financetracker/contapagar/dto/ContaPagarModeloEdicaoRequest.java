package com.financetracker.contapagar.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContaPagarModeloEdicaoRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 100)
        String nome,

        @NotNull(message = "Categoria é obrigatória")
        UUID categoriaId,

        @Size(max = 50)
        String agrupador,

        @NotNull(message = "Dia de vencimento é obrigatório")
        @Min(1) @Max(31)
        Integer diaVencimento,

        BigDecimal valorPadrao,

        LocalDate dataFim,

        Boolean debitoAutomatico,

        UUID contaPadraoId,

        Boolean aplicarValorOcorrenciasFuturas,

        String modoParcelamento,

        BigDecimal valorUltimaParcela,

        Boolean valoresEstimados,

        UUID financiamentoId
) {}
