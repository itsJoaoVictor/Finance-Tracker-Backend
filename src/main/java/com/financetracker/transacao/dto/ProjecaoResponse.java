package com.financetracker.transacao.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjecaoResponse(
    LocalDate data,
    BigDecimal saldoProjetado
) {}