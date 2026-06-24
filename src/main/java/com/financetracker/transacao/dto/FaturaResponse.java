package com.financetracker.transacao.dto;

import com.financetracker.transacao.entity.Fatura;
import com.financetracker.transacao.enums.StatusFatura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FaturaResponse(
    UUID id,
    LocalDate mesReferencia,
    LocalDate dataFechamento,
    LocalDate dataVencimento,
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
            fatura.getValorTotal(),
            fatura.getValorPago(),
            fatura.getStatus(),
            fatura.isRolladoOver()
        );
    }
}

