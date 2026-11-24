package com.financetracker.contapagar.entity;

import com.financetracker.transacao.entity.Transacao;
import com.financetracker.usuario.entity.Usuario;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "financiamento_transacoes")
public class FinanciamentoTransacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "financiamento_id", nullable = false)
    private Financiamento financiamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transacao_id", nullable = false, unique = true)
    private Transacao transacao;

    @Column(length = 255)
    private String observacao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Financiamento getFinanciamento() { return financiamento; }
    public void setFinanciamento(Financiamento financiamento) { this.financiamento = financiamento; }

    public Transacao getTransacao() { return transacao; }
    public void setTransacao(Transacao transacao) { this.transacao = transacao; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
