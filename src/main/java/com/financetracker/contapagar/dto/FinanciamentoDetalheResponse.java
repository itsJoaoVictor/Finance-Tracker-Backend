package com.financetracker.contapagar.dto;

import com.financetracker.contapagar.entity.ContaPagarAmortizacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FinanciamentoDetalheResponse(
        ContaPagarModeloResponse modelo,
        int parcelasPagas,
        int parcelasRestantes,
        int parcelasCanceladas,
        BigDecimal totalPagoParcelas,
        BigDecimal totalPagoAmortizacoes,
        BigDecimal totalGeralPago,
        BigDecimal totalJurosMultaPago,
        BigDecimal totalRestanteEstimado,
        List<ContaPagarOcorrenciaResponse> ocorrencias,
        List<AmortizacaoItem> amortizacoes
) {
    public record AmortizacaoItem(
            UUID id,
            BigDecimal valor,
            LocalDate data,
            String origem,
            String contaNome,
            String modo,
            Integer parcelasEliminadas,
            BigDecimal novoValorParcela,
            String observacao,
            UUID transacaoId
    ) {
        public AmortizacaoItem(ContaPagarAmortizacao a) {
            this(
                    a.getId(),
                    a.getValor(),
                    a.getData(),
                    a.getOrigem().name(),
                    a.getConta() != null ? a.getConta().getNome() : "Externa (FGTS/Outros)",
                    a.getModo().name(),
                    a.getParcelasEliminadas(),
                    a.getNovoValorParcela(),
                    a.getObservacao(),
                    a.getTransacao() != null ? a.getTransacao().getId() : null
            );
        }
    }
}
