package com.financetracker.dashboard.controller;

import com.financetracker.dashboard.dto.DashboardResumoResponse;
import com.financetracker.dashboard.dto.LayoutRequest;
import com.financetracker.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/preferencias")
    public ResponseEntity<DashboardResumoResponse.PreferenciasLayout> obterPreferencias() {
        return ResponseEntity.ok(dashboardService.obterPreferencias());
    }

    @GetMapping("/kpis")
    public ResponseEntity<DashboardResumoResponse.Kpis> obterKpis(
            @RequestParam(name = "periodo", defaultValue = "MES_ATUAL") String periodo) {
        return ResponseEntity.ok(dashboardService.obterKpis(periodo));
    }

    @GetMapping("/contas")
    public ResponseEntity<List<DashboardResumoResponse.ContaDashboard>> obterContas(
            @RequestParam(name = "periodo", defaultValue = "MES_ATUAL") String periodo) {
        return ResponseEntity.ok(dashboardService.obterContasDashboard(periodo));
    }

    @GetMapping("/cartoes")
    public ResponseEntity<List<DashboardResumoResponse.CartaoDashboard>> obterCartoes(
            @RequestParam(name = "periodo", defaultValue = "MES_ATUAL") String periodo) {
        return ResponseEntity.ok(dashboardService.obterCartoesDashboard(periodo));
    }

    @GetMapping("/projecao")
    public ResponseEntity<DashboardResumoResponse.Projecao15Dias> obterProjecao() {
        return ResponseEntity.ok(dashboardService.obterProjecao15Dias());
    }

    @GetMapping("/transacoes")
    public ResponseEntity<List<DashboardResumoResponse.TransacaoDashboard>> obterTransacoes(
            @RequestParam(name = "periodo", defaultValue = "MES_ATUAL") String periodo) {
        return ResponseEntity.ok(dashboardService.obterUltimasTransacoes(periodo));
    }

    @GetMapping("/insights")
    public ResponseEntity<List<DashboardResumoResponse.InsightDashboard>> obterInsights() {
        return ResponseEntity.ok(dashboardService.obterInsights());
    }

    @PutMapping("/layout")
    public ResponseEntity<Void> salvarLayout(@RequestBody LayoutRequest request) {
        dashboardService.salvarLayout(request);
        return ResponseEntity.ok().build();
    }
}
