package com.financetracker.transacao.dto;

import com.financetracker.transacao.entity.Fatura;
import com.financetracker.transacao.enums.StatusFatura;
import com.financetracker.transacao.util.FaturaDateUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FaturaResponse(
    UUID id,
    LocalDate mesReferencia,
    LocalDate dataFechamento,
    LocalDate dataVencimento,
    LocalDate dataVencimentoPadrao,
    boolean vencimentoAjustado,
    BigDecimal valorTotal,
    BigDecimal valorPago,
    StatusFatura status,
    boolean rolladoOver
) {
    public FaturaResponse(Fatura fatura) {
        this(
            fatura.getId(),
            fatura.getMesReferencia(),
            fatura.getDataFechamento(),
            fatura.getDataVencimento(),
            calcularPadrao(fatura),
            !fatura.getDataVencimento().equals(calcularPadrao(fatura)),
            fatura.getValorTotal(),
            fatura.getValorPago(),
            fatura.getStatus(),
            fatura.isRolladoOver()
        );
    }

    private static LocalDate calcularPadrao(Fatura fatura) {
        if (fatura.getCartao() == null || fatura.getMesReferencia() == null || fatura.getDataFechamento() == null) {
            return fatura.getDataVencimento();
        }
        return FaturaDateUtils.calcularDataVencimentoPadrao(
            fatura.getMesReferencia(),
            fatura.getDataFechamento(),
            fatura.getCartao().getDiaVencimento()
        );
    }
}

