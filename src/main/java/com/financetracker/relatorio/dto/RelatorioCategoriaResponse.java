package com.financetracker.relatorio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RelatorioCategoriaResponse(
        Periodo periodo,
        BigDecimal totalConsolidado,
        List<CategoriaRelatorio> categorias
) {
    public record Periodo(
            LocalDate dataInicio,
            LocalDate dataFim
    ) {}

    public record CategoriaRelatorio(
            String categoriaId,
            String categoriaNome,
            String corHexadecimal,
            BigDecimal valorTotal,
            BigDecimal percentual
    ) {}
}