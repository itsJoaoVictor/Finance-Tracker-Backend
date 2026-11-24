package com.financetracker.contapagar.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ItensDisponiveisVinculoResponse(
        List<ModeloDisponivelItem> modelosDisponiveis,
        List<TransacaoDisponivelItem> transacoesDisponiveis
) {
    public record ModeloDisponivelItem(
            UUID id,
            String nome,
            String tipo,
            String agrupador,
            Integer totalParcelas,
            int parcelasPagas,
            int parcelasRestantes,
            BigDecimal totalPago,
            BigDecimal totalRestante
    ) {}

    public record TransacaoDisponivelItem(
            UUID id,
            String descricao,
            BigDecimal valor,
            LocalDate data,
            String tipo,
            String categoriaNome
    ) {}
}
