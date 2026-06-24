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

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onApplicationReady() {
        System.out.println("Starting startup processing of pending subscription charges...");
        try {
            assinaturaService.processarCobrancasPendentes();
            System.out.println("Finished startup processing of pending subscription charges.");
        } catch (Exception e) {
            System.err.println("Error during startup processing of pending subscription charges: " + e.getMessage());
        }
    }
}