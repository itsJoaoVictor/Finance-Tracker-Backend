package com.financetracker.ia.domain;

import com.financetracker.assinatura.entity.Assinatura;
import com.financetracker.usuario.entity.Usuario;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ia_classificacoes_assinatura", indexes = {
    @Index(name = "idx_ia_classif_assin_usuario", columnList = "usuario_id"),
    @Index(name = "idx_ia_classif_assin_assinatura", columnList = "assinatura_id", unique = true)
})
public class IaClassificacaoAssinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assinatura_id", nullable = false, unique = true)
    private Assinatura assinatura;

    /** ESSENCIAL, IMPORTANTE, DISCRICIONARIA */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NivelEssencialidade essencialidade;

    /** Justificativa da IA para essa classificação */
    @Column(columnDefinition = "TEXT")
    private String justificativa;

    /**
     * Se a IA teve dúvida, fica null. O frontend pergunta ao usuário.
     * Após o usuário responder, salva o valor e recalcula.
     */
    @Column(nullable = false)
    private boolean confirmado = false;

    /** Resposta do usuário quando a IA pediu confirmação (ESSENCIAL/IMPORTANTE/DISCICIONARIA) */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NivelEssencialidade respostaUsuario;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    public IaClassificacaoAssinatura() {}

    public IaClassificacaoAssinatura(Usuario usuario, Assinatura assinatura,
                                     NivelEssencialidade essencialidade, String justificativa,
                                     boolean confirmado) {
        this.usuario = usuario;
        this.assinatura = assinatura;
        this.essencialidade = essencialidade;
        this.justificativa = justificativa;
        this.confirmado = confirmado;
    }

    // ── Getters/Setters ──

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Assinatura getAssinatura() { return assinatura; }
    public void setAssinatura(Assinatura assinatura) { this.assinatura = assinatura; }

    public NivelEssencialidade getEssencialidade() { return essencialidade; }
    public void setEssencialidade(NivelEssencialidade essencialidade) { this.essencialidade = essencialidade; }

    public String getJustificativa() { return justificativa; }
    public void setJustificativa(String justificativa) { this.justificativa = justificativa; }

    public boolean isConfirmado() { return confirmado; }
    public void setConfirmado(boolean confirmado) { this.confirmado = confirmado; }

    public NivelEssencialidade getRespostaUsuario() { return respostaUsuario; }
    public void setRespostaUsuario(NivelEssencialidade respostaUsuario) { this.respostaUsuario = respostaUsuario; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
