package com.financetracker.ia.domain;

import com.financetracker.categoria.entity.Categoria;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ia_dicionario_categorias")
public class IaDicionarioCategoria {

    @Id
    @Column(name = "descricao_limpa", length = 150)
    private String descricaoLimpa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public IaDicionarioCategoria() {}

    public IaDicionarioCategoria(String descricaoLimpa, Categoria categoria) {
        this.descricaoLimpa = descricaoLimpa;
        this.categoria = categoria;
    }

    public String getDescricaoLimpa() { return descricaoLimpa; }
    public void setDescricaoLimpa(String descricaoLimpa) { this.descricaoLimpa = descricaoLimpa; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
