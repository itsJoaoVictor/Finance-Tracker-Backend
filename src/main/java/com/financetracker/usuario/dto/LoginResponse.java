package com.financetracker.usuario.dto;

public record LoginResponse(
    String token,
    String access_token,
    String refresh_token,
    Boolean twoFactorRequired
) {
    public LoginResponse(String token) {
        this(token, token, null, null);
    }
}
