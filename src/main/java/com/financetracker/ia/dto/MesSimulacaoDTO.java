package com.financetracker.ia.dto;

import java.math.BigDecimal;

public record MesSimulacaoDTO(
    String mesAno,
    BigDecimal receitaProjetada,
    BigDecimal despesaFixaProjetada,
    BigDecimal faturasProjetadas,
    BigDecimal faturasProjetadasCartao, // Projeção da fatura apenas para o cartão selecionado
    BigDecimal novaParcela,
    BigDecimal saldoLivre,
    String status, // VERDE, AMARELO, VERMELHO
    BigDecimal limiteRestanteCartao // Projeção do limite livre no cartão mês a mês (Efeito bola de neve)
) {}
