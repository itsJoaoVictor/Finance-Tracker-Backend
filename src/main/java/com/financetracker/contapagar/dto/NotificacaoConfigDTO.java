package com.financetracker.contapagar.dto;

public record NotificacaoConfigDTO(
        String telegramChatId,
        Boolean telegramAtivo,
        Boolean webPushAtivo,
        boolean telegramBotConfiguradoNoServidor,
        String vapidPublicKey
) {}
