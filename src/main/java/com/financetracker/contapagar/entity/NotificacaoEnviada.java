package com.financetracker.contapagar.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notificacoes_enviadas")
public class NotificacaoEnviada {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ocorrencia_id", nullable = false)
    private ContaPagarOcorrencia ocorrencia;

    @Column(nullable = false, length = 40)
    private String gatilho;

    @Column(name = "enviado_em", nullable = false, updatable = false)
    private LocalDateTime enviadoEm = LocalDateTime.now();

    public NotificacaoEnviada() {}

    public NotificacaoEnviada(ContaPagarOcorrencia ocorrencia, String gatilho) {
        this.ocorrencia = ocorrencia;
        this.gatilho = gatilho;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ContaPagarOcorrencia getOcorrencia() { return ocorrencia; }
    public void setOcorrencia(ContaPagarOcorrencia ocorrencia) { this.ocorrencia = ocorrencia; }

    public String getGatilho() { return gatilho; }
    public void setGatilho(String gatilho) { this.gatilho = gatilho; }

    public LocalDateTime getEnviadoEm() { return enviadoEm; }
    public void setEnviadoEm(LocalDateTime enviadoEm) { this.enviadoEm = enviadoEm; }
}
