package com.financetracker.contapagar.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FinanciamentoResumoResponse(
        UUID id,
        String nome,
        String descricao,
        String corHexadecimal,
        BigDecimal valorPagoAnteriormente,
        BigDecimal totalPagoModelos,
        BigDecimal totalPagoAmortizacoes,
        BigDecimal totalPagoTransacoesSoltas,
        BigDecimal totalPago,
        BigDecimal totalRestante,
        BigDecimal valorTotal,
        BigDecimal valorContratado,
        BigDecimal jurosEncargosProjetados,
        Double percentualJurosProjetados,
        BigDecimal totalDescontoObtido,
        double percentualPago,
        boolean concluido,
        int qtdModelosVinculados,
        int qtdTransacoesVinculadas,
        List<ModeloVinculadoResumo> modelos,
        List<TransacaoVinculadaItem> transacoesVinculadas
) {
    public record ModeloVinculadoResumo(
            UUID id,
            String nome,
            String tipo,
            String modoParcelamento,
            Integer intervaloMeses,
            Boolean valoresEstimados,
            Integer diaVencimento,
            BigDecimal valorPadrao,
            BigDecimal valorUltimaParcela,
            Integer totalParcelas,
            int parcelasPagas,
            int parcelasRestantes,
            BigDecimal totalPago,
            BigDecimal totalAmortizacoes,
            BigDecimal totalDescontoObtido,
            BigDecimal totalRestante,
            BigDecimal valorTotal,
            boolean concluido
    ) {}

    public record TransacaoVinculadaItem(
            UUID vinculoId,
            UUID transacaoId,
            String descricao,
            BigDecimal valor,
            LocalDate data,
            String tipo,
            String categoriaNome,
            String observacao
    ) {}
}
