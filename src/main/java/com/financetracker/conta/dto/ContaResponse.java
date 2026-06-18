package com.financetracker.conta.dto;

import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.model.TipoConta;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContaResponse(
        UUID id,
        String nome,
        TipoConta tipo,
        BigDecimal saldo,
        String corHexadecimal,
        Boolean contaPadrao,
        LocalDateTime criadoEm
) {
    public ContaResponse(Conta conta) {
        this(
                conta.getId(),
                conta.getNome(),
                conta.getTipo(),
                conta.getSaldo(),
                conta.getCorHexadecimal(),
                conta.getContaPadrao(),
                conta.getCriadoEm()
        );
    }
}
