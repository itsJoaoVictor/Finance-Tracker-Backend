package com.financetracker.assinatura.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AssinaturaProximaResponse(
    UUID id,
    String nome,
    BigDecimal valor,
    UUID cartaoId,
    LocalDate dataProximaCobranca,
    long diasRestantes
) {}