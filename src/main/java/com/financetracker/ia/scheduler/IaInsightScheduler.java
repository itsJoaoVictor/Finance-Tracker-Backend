package com.financetracker.ia.scheduler;

import com.financetracker.ia.service.IaService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IaInsightScheduler {

    private final IaService iaService;

    public IaInsightScheduler(IaService iaService) {
        this.iaService = iaService;
    }

    /**
     * Agenda a rotina de análise de IA para rodar a cada 1 hora.
     * Analisa o comportamento dos usuários, detecta estouro de limite (RN-11), 
     * burn rate de cartões (RN-01) e assinaturas esquecidas (RN-09).
     */
    @Scheduled(cron = "0 0 * * * *")
    public void executarAnaliseDeIA() {
        try {
            iaService.processarInsightsParaTodos();
        } catch (Exception ignored) {
            // Silencia falhas no scheduler automático para manter a resiliência
        }
    }
}
