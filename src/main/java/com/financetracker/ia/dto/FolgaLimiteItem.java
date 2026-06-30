package com.financetracker.ia.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FolgaLimiteItem(
        String id,
        UUID cartaoId,
        String cartaoNome,
        String descricao,
        BigDecimal valorParcela,
        Integer totalParcelas,
        BigDecimal impactoMensal,
        String titulo,
        String mensagem
) {}
