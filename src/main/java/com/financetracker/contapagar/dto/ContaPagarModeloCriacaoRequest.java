package com.financetracker.contapagar.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ContaPagarModeloCriacaoRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 100)
        String nome,

        @NotBlank(message = "Tipo é obrigatório (VARIAVEL, FIXO, PARCELADO, AVULSO)")
        String tipo,

        @NotNull(message = "Categoria é obrigatória")
        UUID categoriaId,

        @Size(max = 50)
        String agrupador,

        @NotNull(message = "Dia de vencimento é obrigatório")
        @Min(1) @Max(31)
        Integer diaVencimento,

        BigDecimal valorPadrao,

        Integer totalParcelas,

        Integer parcelaInicial,

        @NotNull(message = "Data de início é obrigatória")
        LocalDate dataInicio,

        LocalDate dataFim,

        Boolean debitoAutomatico,

        UUID contaPadraoId,

        @Size(max = 100)
        String linhaDigitavel,

        @Size(max = 255)
        String observacao,

        String modoParcelamento,

        BigDecimal valorUltimaParcela,

        @Min(1) @Max(60)
        Integer intervaloMeses,

        Boolean valoresEstimados,

        List<BigDecimal> valoresParcelas,

        UUID financiamentoId
) {}
