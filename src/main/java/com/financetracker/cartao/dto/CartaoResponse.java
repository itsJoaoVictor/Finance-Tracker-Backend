package com.financetracker.cartao.dto;

import com.financetracker.cartao.entity.Cartao;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CartaoResponse(
    UUID id,
    String nome,
    BigDecimal limite,
    BigDecimal limiteDisponivel,
    int diaFechamento,
    int diaVencimento,
    UUID contaId,
    String corHexadecimal,
    LocalDateTime criadoEm,
    BigDecimal faturaEstimada,
    String faturaStatus,
    String faturaMesReferencia,
    String faturaDataVencimento
) {
    public CartaoResponse(
            UUID id,
            String nome,
            BigDecimal limite,
            BigDecimal limiteDisponivel,
            int diaFechamento,
            int diaVencimento,
            UUID contaId,
            String corHexadecimal,
            LocalDateTime criadoEm,
            BigDecimal faturaEstimada,
            String faturaStatus,
            String faturaMesReferencia
    ) {
        this(
            id, nome, limite, limiteDisponivel, diaFechamento, diaVencimento,
            contaId, corHexadecimal, criadoEm, faturaEstimada, faturaStatus, faturaMesReferencia, null
        );
    }

    public CartaoResponse(Cartao cartao) {
        this(
            cartao.getId(),
            cartao.getNome(),
            cartao.getLimite(),
            cartao.getLimiteDisponivel(),
            cartao.getDiaFechamento(),
            cartao.getDiaVencimento(),
            cartao.getConta().getId(),
            cartao.getCorHexadecimal(),
            cartao.getCriadoEm(),
            BigDecimal.ZERO,
            "ABERTA",
            null,
            null
        );
    }

    public CartaoResponse(Cartao cartao, BigDecimal faturaEstimada, String faturaStatus, String faturaMesReferencia) {
        this(cartao, faturaEstimada, faturaStatus, faturaMesReferencia, null);
    }

    public CartaoResponse(
            Cartao cartao,
            BigDecimal faturaEstimada,
            String faturaStatus,
            String faturaMesReferencia,
            String faturaDataVencimento
    ) {
        this(
            cartao.getId(),
            cartao.getNome(),
            cartao.getLimite(),
            cartao.getLimiteDisponivel(),
            cartao.getDiaFechamento(),
            cartao.getDiaVencimento(),
            cartao.getConta().getId(),
            cartao.getCorHexadecimal(),
            cartao.getCriadoEm(),
            faturaEstimada,
            faturaStatus,
            faturaMesReferencia,
            faturaDataVencimento
        );
    }
}
