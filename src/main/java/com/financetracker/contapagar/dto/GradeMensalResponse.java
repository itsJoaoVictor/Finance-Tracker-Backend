package com.financetracker.contapagar.dto;

import java.math.BigDecimal;
import java.util.List;

public record GradeMensalResponse(
        List<String> meses, // YYYY-MM-01
        List<String> agrupadores,
        List<LinhaGrade> linhas,
        List<TotaisMes> totaisPorMes,
        BigDecimal totalJurosAno
) {
    public record LinhaGrade(
            ContaPagarModeloResponse modelo,
            List<ContaPagarOcorrenciaResponse> celulas // alinhadas com 'meses', null quando não existe no mês
    ) {}

    public record TotaisMes(
            String mesReferencia,
            BigDecimal totalPago,
            BigDecimal totalPendente,
            BigDecimal totalEstimado,
            BigDecimal totalGeral
    ) {}
}
