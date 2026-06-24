package com.financetracker.ia.domain;

import com.financetracker.usuario.entity.Usuario;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ia_correcoes_usuario")
public class IaCorrecaoUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "descricao_limpa", nullable = false, length = 150)
    private String descricaoLimpa;

    @Column(name = "categoria_antiga_id", nullable = false)
    private UUID categoriaAntigaId;

    @Column(name = "categoria_nova_id", nullable = false)
    private UUID categoriaNovaId;

    @Column(name = "data_correcao", nullable = false)
    private LocalDateTime dataCorrecao = LocalDateTime.now();

    public IaCorrecaoUsuario() {}

    public IaCorrecaoUsuario(Usuario usuario, String descricaoLimpa, UUID categoriaAntigaId, UUID categoriaNovaId) {
        this.usuario = usuario;
        this.descricaoLimpa = descricaoLimpa;
        this.categoriaAntigaId = categoriaAntigaId;
        this.categoriaNovaId = categoriaNovaId;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getDescricaoLimpa() { return descricaoLimpa; }
    public void setDescricaoLimpa(String descricaoLimpa) { this.descricaoLimpa = descricaoLimpa; }

    public UUID getCategoriaAntigaId() { return categoriaAntigaId; }
    public void setCategoriaAntigaId(UUID categoriaAntigaId) { this.categoriaAntigaId = categoriaAntigaId; }

    public UUID getCategoriaNovaId() { return categoriaNovaId; }
    public void setCategoriaNovaId(UUID categoriaNovaId) { this.categoriaNovaId = categoriaNovaId; }

    public LocalDateTime getDataCorrecao() { return dataCorrecao; }
    public void setDataCorrecao(LocalDateTime dataCorrecao) { this.dataCorrecao = dataCorrecao; }
}
