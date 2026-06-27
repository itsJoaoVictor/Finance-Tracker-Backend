package com.financetracker.ia.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InteligenciaAssinaturaResponse(
    int totalAssinaturas,
    BigDecimal totalMensal,
    String classificacaoGlobal,
    String nivelAlerta,
    List<ReajusteDetectado> reajustes,
    List<ScoreEficiencia> scores,
    String mensagem
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

    public record ScoreEficiencia(
        UUID assinaturaId,
        String nome,
        int score,
        String classificacao,
        Map<String, Integer> breakdown,
        String justificativa
    ) {}
}
