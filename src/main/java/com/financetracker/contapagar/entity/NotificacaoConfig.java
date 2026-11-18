package com.financetracker.contapagar.entity;

import com.financetracker.usuario.entity.Usuario;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notificacao_config")
public class NotificacaoConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(name = "telegram_chat_id", length = 60)
    private String telegramChatId;

    @Column(name = "telegram_ativo", nullable = false)
    private Boolean telegramAtivo = false;

    @Column(name = "web_push_ativo", nullable = false)
    private Boolean webPushAtivo = false;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getTelegramChatId() { return telegramChatId; }
    public void setTelegramChatId(String telegramChatId) { this.telegramChatId = telegramChatId; }

    public Boolean getTelegramAtivo() { return telegramAtivo; }
    public void setTelegramAtivo(Boolean telegramAtivo) { this.telegramAtivo = telegramAtivo; }

    public Boolean getWebPushAtivo() { return webPushAtivo; }
    public void setWebPushAtivo(Boolean webPushAtivo) { this.webPushAtivo = webPushAtivo; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
