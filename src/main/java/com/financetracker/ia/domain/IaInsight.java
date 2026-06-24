package com.financetracker.ia.domain;

import com.financetracker.usuario.entity.Usuario;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ia_insights", indexes = {
    @Index(name = "idx_ia_insights_usuario_id", columnList = "usuario_id")
})
public class IaInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TipoInsight tipo;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensagem;

    @Column(columnDefinition = "TEXT") // Armazena metadados estruturados em formato JSON/String
    private String metadados;

    @Column(nullable = false)
    private Boolean lido = false;

    @Column
    private Boolean relevante;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public IaInsight() {}

    public IaInsight(Usuario usuario, TipoInsight tipo, String titulo, String mensagem, String metadados) {
        this.usuario = usuario;
        this.tipo = tipo;
        this.titulo = titulo;
        this.mensagem = mensagem;
        this.metadados = metadados;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public TipoInsight getTipo() { return tipo; }
    public void setTipo(TipoInsight tipo) { this.tipo = tipo; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }

    public String getMetadados() { return metadados; }
    public void setMetadados(String metadados) { this.metadados = metadados; }

    public Boolean getLido() { return lido; }
    public void setLido(Boolean lido) { this.lido = lido; }

    public Boolean getRelevante() { return relevante; }
    public void setRelevante(Boolean relevante) { this.relevante = relevante; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
