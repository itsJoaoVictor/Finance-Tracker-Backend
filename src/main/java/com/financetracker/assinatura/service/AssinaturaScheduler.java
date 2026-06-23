package com.financetracker.assinatura.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AssinaturaScheduler {

    private final AssinaturaService assinaturaService;

    public AssinaturaScheduler(AssinaturaService assinaturaService) {
        this.assinaturaService = assinaturaService;
    }

    @Scheduled(cron = "0 0 6 * * ?") // Runs daily at 6 AM
    public void processarCobrancas() {
        assinaturaService.processarCobrancasPendentes();
    }
}