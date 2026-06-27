package com.financetracker.ia.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record InteligenciaAssinaturaResponse(
    List<ReajusteDetectado> reajustes
) {
    public record ReajusteDetectado(
        UUID assinaturaId,
        String nome,
        String categoria,
        BigDecimal valorAnterior,
        BigDecimal valorAtual,
        BigDecimal percentualAumento,
        BigDecimal impactoAnual,
        boolean alteracaoVoluntaria
    ) {}
}
