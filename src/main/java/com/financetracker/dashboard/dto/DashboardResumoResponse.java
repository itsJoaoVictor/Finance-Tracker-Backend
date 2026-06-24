package com.financetracker.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DashboardResumoResponse(
        PreferenciasLayout preferenciasLayout,
        Kpis kpis,
        Projecao15Dias projetcao15Dias,
        List<ContaDashboard> contas,
        List<CartaoDashboard> cartoes,
        List<TransacaoDashboard> ultimasTransacoes,
        List<InsightDashboard> insightsAtivos
) {
    public record PreferenciasLayout(
            List<String> ordemWidgets,
            List<String> widgetsOcultos
    ) {}

    public record Kpis(
            BigDecimal saldoTotal,
            BigDecimal faturaTotalCartoes,
            BigDecimal limiteTotalDisponivelCartoes
    ) {}

    public record Projecao15Dias(
            BigDecimal saldoProjetado,
            String status,
            String mensagem
    ) {}

    public record ContaDashboard(
            UUID id,
            String nome,
            String tipo,
            BigDecimal saldo,
            String corHexadecimal
    ) {}

    public record CartaoDashboard(
            UUID id,
            String nome,
            BigDecimal faturaAtual,
            BigDecimal limiteDisponivel,
            String corHexadecimal
    ) {}

    public record TransacaoDashboard(
            UUID id,
            String descricao,
            BigDecimal valor,
            String tipo,
            String categoriaNome,
            String categoriaIcone,
            String categoriaCorHexadecimal,
            LocalDateTime data
    ) {}

    public record InsightDashboard(
            UUID id,
            String tipo,
            String titulo,
            String mensagem,
            LocalDateTime criadoEm
    ) {}
}