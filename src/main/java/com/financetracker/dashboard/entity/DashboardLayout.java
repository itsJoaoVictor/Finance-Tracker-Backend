package com.financetracker.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dashboard_layout")
public class DashboardLayout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, unique = true)
    private UUID usuarioId;

    @Column(columnDefinition = "TEXT", name = "ordem_widgets")
    private String ordemWidgets;

    @Column(columnDefinition = "TEXT", name = "widgets_ocultos")
    private String widgetsOcultos;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    public DashboardLayout() {}

    public DashboardLayout(UUID usuarioId) {
        this.usuarioId = usuarioId;
        this.ordemWidgets = "[\"kpis\",\"fluxoCaixaProjetado\",\"cartoes\",\"insights\",\"graficoDespesas\",\"ultimasTransacoes\"]";
        this.widgetsOcultos = "[]";
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }

    public String getOrdemWidgets() { return ordemWidgets; }
    public void setOrdemWidgets(String ordemWidgets) { this.ordemWidgets = ordemWidgets; }

    public String getWidgetsOcultos() { return widgetsOcultos; }
    public void setWidgetsOcultos(String widgetsOcultos) { this.widgetsOcultos = widgetsOcultos; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }

    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}