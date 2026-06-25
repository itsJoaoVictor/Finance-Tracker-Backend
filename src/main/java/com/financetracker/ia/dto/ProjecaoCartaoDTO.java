package com.financetracker.ia.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProjecaoCartaoDTO(
    UUID cartaoId,
    String cartaoNome,
    String corHexadecimal,
    String statusFatura,           // "ABERTA" | "FECHADA" | "SEM_FATURA"
    BigDecimal valorAtualNoMes,    // gasto acumulado no mes (COMPRA_CREDITO primarias)
    BigDecimal projecaoFechamento, // valor projetado de fechamento
    boolean projecaoViaIa,         // se usou IA ou so extrapolation
    BigDecimal valorRealFechado,   // valorTotal da fatura fechada (null se ABERTA)
    BigDecimal mediaHistorica,     // null se < 2 faturas historicas
    Integer mesesHistorico,        // quantas faturas no calculo
    BigDecimal desvioPercentual,   // % vs media (positivo = acima)
    String classificacao,          // "ACIMA" | "ABAIXO" | "DENTRO" | "SEM_DADOS" | "NOVO" | "PRIMEIRO_MES"
    String mensagemResumo,         // mensagem formatada para exibicao
    Integer diasNoMes,
    Integer diasPassados
) {}
