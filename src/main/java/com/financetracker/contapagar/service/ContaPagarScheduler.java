package com.financetracker.contapagar.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ContaPagarScheduler {

    private final ContaPagarService contaPagarService;

    public ContaPagarScheduler(ContaPagarService contaPagarService) {
        this.contaPagarService = contaPagarService;
    }

    @Scheduled(cron = "0 30 5 * * ?", zone = "America/Sao_Paulo")
    public void executarRotinaDiaria() {
        try {
            contaPagarService.processarRotinaDiaria();
        } catch (Exception e) {
            System.err.println("Erro ao processar rotina de Contas a Pagar: " + e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            contaPagarService.processarRotinaDiaria();
        } catch (Exception e) {
            System.err.println("Erro no startup de Contas a Pagar: " + e.getMessage());
        }
    }
}
