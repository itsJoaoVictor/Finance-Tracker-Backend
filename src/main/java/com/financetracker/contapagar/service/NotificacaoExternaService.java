package com.financetracker.contapagar.service;

import com.financetracker.contapagar.dto.NotificacaoConfigDTO;
import com.financetracker.contapagar.dto.PushSubscriptionRequest;
import com.financetracker.contapagar.entity.ContaPagarOcorrencia;
import com.financetracker.contapagar.entity.NotificacaoConfig;
import com.financetracker.contapagar.entity.NotificacaoEnviada;
import com.financetracker.contapagar.entity.PushSubscriptionEntity;
import com.financetracker.contapagar.repository.NotificacaoConfigRepository;
import com.financetracker.contapagar.repository.NotificacaoEnviadaRepository;
import com.financetracker.contapagar.repository.PushSubscriptionRepository;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@Service
public class NotificacaoExternaService {

    private final NotificacaoConfigRepository configRepository;
    private final PushSubscriptionRepository pushRepository;
    private final NotificacaoEnviadaRepository enviadaRepository;
    private final UsuarioRepository usuarioRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${TELEGRAM_BOT_TOKEN:}")
    private String telegramBotToken;

    @Value("${VAPID_PUBLIC_KEY:}")
    private String vapidPublicKey;

    public NotificacaoExternaService(NotificacaoConfigRepository configRepository,
                                     PushSubscriptionRepository pushRepository,
                                     NotificacaoEnviadaRepository enviadaRepository,
                                     UsuarioRepository usuarioRepository) {
        this.configRepository = configRepository;
        this.pushRepository = pushRepository;
        this.enviadaRepository = enviadaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    private Usuario getAuthenticatedUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado"));
    }

    @Transactional(readOnly = true)
    public NotificacaoConfigDTO obterConfig() {
        Usuario u = getAuthenticatedUsuario();
        NotificacaoConfig cfg = configRepository.findByUsuarioId(u.getId()).orElse(null);
        boolean botOk = telegramBotToken != null && !telegramBotToken.isBlank();
        return new NotificacaoConfigDTO(
                cfg != null ? cfg.getTelegramChatId() : "",
                cfg != null && Boolean.TRUE.equals(cfg.getTelegramAtivo()),
                cfg != null && Boolean.TRUE.equals(cfg.getWebPushAtivo()),
                botOk,
                vapidPublicKey != null ? vapidPublicKey : ""
        );
    }

    @Transactional
    public NotificacaoConfigDTO salvarConfig(String telegramChatId, Boolean telegramAtivo, Boolean webPushAtivo) {
        Usuario u = getAuthenticatedUsuario();
        NotificacaoConfig cfg = configRepository.findByUsuarioId(u.getId()).orElseGet(() -> {
            NotificacaoConfig novo = new NotificacaoConfig();
            novo.setUsuario(u);
            return novo;
        });
        if (telegramChatId != null) cfg.setTelegramChatId(telegramChatId.trim());
        if (telegramAtivo != null) cfg.setTelegramAtivo(telegramAtivo);
        if (webPushAtivo != null) cfg.setWebPushAtivo(webPushAtivo);
        configRepository.save(cfg);
        return obterConfig();
    }

    @Transactional
    public void registrarPushSubscription(PushSubscriptionRequest req) {
        Usuario u = getAuthenticatedUsuario();
        PushSubscriptionEntity sub = pushRepository
                .findByUsuarioIdAndEndpoint(u.getId(), req.endpoint())
                .orElseGet(() -> {
                    PushSubscriptionEntity novo = new PushSubscriptionEntity();
                    novo.setUsuario(u);
                    novo.setEndpoint(req.endpoint());
                    return novo;
                });
        sub.setP256dh(req.p256dh());
        sub.setAuth(req.auth());
        pushRepository.save(sub);

        NotificacaoConfig cfg = configRepository.findByUsuarioId(u.getId()).orElseGet(() -> {
            NotificacaoConfig novo = new NotificacaoConfig();
            novo.setUsuario(u);
            return novo;
        });
        cfg.setWebPushAtivo(true);
        configRepository.save(cfg);
    }

    public boolean testarTelegram() {
        Usuario u = getAuthenticatedUsuario();
        NotificacaoConfig cfg = configRepository.findByUsuarioId(u.getId()).orElse(null);
        if (cfg == null || cfg.getTelegramChatId() == null || cfg.getTelegramChatId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o Chat ID do Telegram primeiro.");
        }
        return enviarTelegram(cfg.getTelegramChatId(),
                "✅ *Finance Tracker*: Notificações de Contas a Pagar conectadas com sucesso!");
    }

    @Transactional
    public void notificarOcorrenciaSeNecessario(ContaPagarOcorrencia ocorrencia, String gatilho, String mensagem) {
        if (enviadaRepository.existsByOcorrenciaIdAndGatilho(ocorrencia.getId(), gatilho)) {
            return;
        }
        Usuario u = ocorrencia.getUsuario();
        NotificacaoConfig cfg = configRepository.findByUsuarioId(u.getId()).orElse(null);
        if (cfg != null && Boolean.TRUE.equals(cfg.getTelegramAtivo())
                && cfg.getTelegramChatId() != null && !cfg.getTelegramChatId().isBlank()) {
            enviarTelegram(cfg.getTelegramChatId(), mensagem);
        }
        try {
            enviadaRepository.save(new NotificacaoEnviada(ocorrencia, gatilho));
        } catch (Exception ignored) {
            // unique constraint race condition
        }
    }

    public boolean enviarTelegram(String chatId, String texto) {
        if (telegramBotToken == null || telegramBotToken.isBlank()) {
            return false;
        }
        try {
            String url = "https://api.telegram.org/bot" + telegramBotToken + "/sendMessage";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", texto);
            body.put("parse_mode", "Markdown");
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            return true;
        } catch (Exception e) {
            System.err.println("Falha ao enviar Telegram: " + e.getMessage());
            return false;
        }
    }
}
