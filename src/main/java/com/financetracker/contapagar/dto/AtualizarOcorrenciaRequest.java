package com.financetracker.contapagar.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AtualizarOcorrenciaRequest(
        BigDecimal valorReal,
        BigDecimal valorEstimado,
        LocalDate dataVencimento,
        String linhaDigitavel,
        String observacao
) {}
