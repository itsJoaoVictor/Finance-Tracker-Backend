package com.financetracker.contapagar.dto;

import com.financetracker.contapagar.entity.ContaPagarModelo;
import com.financetracker.contapagar.enums.ModoParcelamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContaPagarModeloResponse(
        UUID id,
        String nome,
        String tipo,
        UUID categoriaId,
        String categoriaNome,
        String categoriaIcone,
        String categoriaCor,
        String agrupador,
        UUID financiamentoId,
        String financiamentoNome,
        Integer diaVencimento,
        BigDecimal valorPadrao,
        String modoParcelamento,
        BigDecimal valorUltimaParcela,
        Integer intervaloMeses,
        Boolean valoresEstimados,
        Integer totalParcelas,
        Integer parcelaInicial,
        LocalDate dataInicio,
        LocalDate dataFim,
        Boolean debitoAutomatico,
        UUID contaPadraoId,
        String contaPadraoNome,
        Boolean ativo
) {
    public ContaPagarModeloResponse(ContaPagarModelo m) {
        this(
                m.getId(),
                m.getNome(),
                m.getTipo().name(),
                m.getCategoria().getId(),
                m.getCategoria().getNome(),
                m.getCategoria().getIcone(),
                m.getCategoria().getCorHexadecimal(),
                m.getAgrupador(),
                m.getFinanciamento() != null ? m.getFinanciamento().getId() : null,
                m.getFinanciamento() != null ? m.getFinanciamento().getNome() : null,
                m.getDiaVencimento(),
                m.getValorPadrao(),
                m.getModoParcelamento() != null ? m.getModoParcelamento().name() : ModoParcelamento.FIXO.name(),
                m.getValorUltimaParcela(),
                m.getIntervaloMeses() != null ? m.getIntervaloMeses() : 1,
                Boolean.TRUE.equals(m.getValoresEstimados()),
                m.getTotalParcelas(),
                m.getParcelaInicial(),
                m.getDataInicio(),
                m.getDataFim(),
                m.getDebitoAutomatico(),
                m.getContaPadrao() != null ? m.getContaPadrao().getId() : null,
                m.getContaPadrao() != null ? m.getContaPadrao().getNome() : null,
                m.getAtivo()
        );
    }
}
