package com.financetracker.contapagar.dto;

import com.financetracker.contapagar.entity.ContaPagarOcorrencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContaPagarOcorrenciaResponse(
        UUID id,
        UUID modeloId,
        String modeloNome,
        String modeloTipo,
        String agrupador,
        UUID categoriaId,
        String categoriaNome,
        String categoriaIcone,
        String categoriaCor,
        Boolean debitoAutomatico,
        UUID contaPadraoId,
        LocalDate mesReferencia,
        Integer numeroParcela,
        Integer totalParcelas,
        LocalDate dataVencimento,
        BigDecimal valorEstimado,
        BigDecimal valorReal,
        BigDecimal valorPago,
        BigDecimal valorEfetivo,
        BigDecimal jurosMulta,
        LocalDate dataPagamento,
        String status,
        String statusEfetivo,
        boolean atrasada,
        String linhaDigitavel,
        String observacao,
        UUID transacaoId
) {
    public ContaPagarOcorrenciaResponse(ContaPagarOcorrencia o) {
        this(
                o.getId(),
                o.getModelo().getId(),
                o.getModelo().getNome(),
                o.getModelo().getTipo().name(),
                o.getModelo().getAgrupador(),
                o.getModelo().getCategoria().getId(),
                o.getModelo().getCategoria().getNome(),
                o.getModelo().getCategoria().getIcone(),
                o.getModelo().getCategoria().getCorHexadecimal(),
                o.getModelo().getDebitoAutomatico(),
                o.getModelo().getContaPadrao() != null ? o.getModelo().getContaPadrao().getId() : null,
                o.getMesReferencia(),
                o.getNumeroParcela(),
                o.getModelo().getTotalParcelas(),
                o.getDataVencimento(),
                o.getValorEstimado(),
                o.getValorReal(),
                o.getValorPago(),
                o.getValorEfetivo(),
                o.getJurosMulta(),
                o.getDataPagamento(),
                o.getStatus().name(),
                o.getStatusEfetivo(),
                o.isAtrasada(),
                o.getLinhaDigitavel(),
                o.getObservacao(),
                o.getTransacao() != null ? o.getTransacao().getId() : null
        );
    }
}
