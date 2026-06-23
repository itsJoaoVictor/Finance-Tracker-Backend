package com.financetracker.transacao.entity;

import com.financetracker.categoria.entity.Categoria;
import com.financetracker.usuario.entity.Usuario;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "orcamentos_categorias", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"usuario_id", "categoria_id", "mes_referencia"})
})
public class OrcamentoCategoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(name = "limite_mensal", precision = 15, scale = 2)
    private BigDecimal limiteMensal;

    @Column(name = "mes_referencia", nullable = false)
    private LocalDate mesReferencia;

    public OrcamentoCategoria() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }
    public BigDecimal getLimiteMensal() { return limiteMensal; }
    public void setLimiteMensal(BigDecimal limiteMensal) { this.limiteMensal = limiteMensal; }
    public LocalDate getMesReferencia() { return mesReferencia; }
    public void setMesReferencia(LocalDate mesReferencia) { this.mesReferencia = mesReferencia; }
}