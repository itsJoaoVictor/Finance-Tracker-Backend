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
    LocalDateTime criadoEm
) {
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
            cartao.getCriadoEm()
        );
    }
}
