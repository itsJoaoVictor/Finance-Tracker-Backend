package com.financetracker.categoria.entity;

import com.financetracker.usuario.entity.Usuario;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "categorias", indexes = {
    @Index(name = "idx_categoria_usuario_id", columnList = "usuario_id")
})
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Usuario usuario;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, length = 50)
    private String icone;

    @Column(name = "cor_hexadecimal", nullable = false, length = 7)
    private String corHexadecimal;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public Categoria() {}

    public Categoria(Usuario usuario, String nome, String icone, String corHexadecimal, Boolean ativo) {
        this.usuario = usuario;
        this.nome = nome;
        this.icone = icone;
        this.corHexadecimal = corHexadecimal;
        this.ativo = ativo != null ? ativo : true;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getIcone() { return icone; }
    public void setIcone(String icone) { this.icone = icone; }

    public String getCorHexadecimal() { return corHexadecimal; }
    public void setCorHexadecimal(String corHexadecimal) { this.corHexadecimal = corHexadecimal; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
