package com.financetracker.ia.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SimulacaoCompraRequest(
    String nomeItem,
    BigDecimal valorTotal,
    Integer parcelas,
    UUID cartaoId // Opcional: ID do cartão planejado para a compra
) {}
