package com.financetracker.ia.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record FadigaAssinaturaResponse(
    int totalAssinaturas,
    BigDecimal totalEssenciais,
    BigDecimal totalImportantes,
    BigDecimal totalDiscricionarias,
    BigDecimal totalGeral,
    BigDecimal indiceAssinaturas,       // totalAssinaturas / faturaTotal (%)
    BigDecimal indiceNaoEssencial,      // (importantes + discricionarias) / totalAssinaturas (%)
    String classificacaoGlobal,         // SAUDAVEL, ATENCAO, FADIGA
    String nivelAlerta,                 // 🟢, 🟡, 🔴
    List<ItemEssencialidade> itens,
    Map<String, Long> duplicadasPorCategoria,
    List<String> servicosSemelhantes,
    String mensagem
) {
    public record ItemEssencialidade(
        String nome,
        String categoria,
        BigDecimal valorMensal,
        String essencialidade,   // ESSENCIAL, IMPORTANTE, DISCRICIONARIA
        String nivelEmoji        // 🟢, 🟡, 🔴
    ) {}
}
