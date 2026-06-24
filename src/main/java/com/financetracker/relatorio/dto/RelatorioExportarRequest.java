package com.financetracker.relatorio.dto;

import java.time.LocalDate;

public record RelatorioExportarRequest(
        LocalDate dataInicio,
        LocalDate dataFim,
        String formato
) {}