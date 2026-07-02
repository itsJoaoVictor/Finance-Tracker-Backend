package com.financetracker.ia.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AnaliseCartaoSimulacaoDTO(
    UUID cartaoId,
    String cartaoNome,
    boolean limiteAprovado, // Trava de limite em tempo real
    BigDecimal limiteDisponivelAtual,
    BigDecimal limiteAposCompra,
    String melhorDiaCompra, // Smart Timing (Melhor dia para realizar a compra)
    int diasGanhoFolego,    // Fôlego extra em dias
    String recomendacaoIa   // Dica e explicação inteligente
) {}
