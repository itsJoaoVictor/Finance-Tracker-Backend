package com.financetracker.contapagar.dto;

import com.financetracker.contapagar.entity.ContaPagarModelo;

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
        Integer diaVencimento,
        BigDecimal valorPadrao,
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
                m.getDiaVencimento(),
                m.getValorPadrao(),
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
