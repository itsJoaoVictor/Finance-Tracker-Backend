package com.financetracker.dashboard.controller;

import com.financetracker.dashboard.dto.DashboardResumoResponse;
import com.financetracker.dashboard.dto.LayoutRequest;
import com.financetracker.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    public ResponseEntity<DashboardResumoResponse> obterResumo(
            @RequestParam(name = "periodo", defaultValue = "MES_ATUAL") String periodo) {
        return ResponseEntity.ok(dashboardService.obterResumo(periodo));
    }

    @PutMapping("/layout")
    public ResponseEntity<Void> salvarLayout(@RequestBody LayoutRequest request) {
        dashboardService.salvarLayout(request);
        return ResponseEntity.ok().build();
    }
}
