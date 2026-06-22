package com.financetracker.usuario.dto;

import jakarta.validation.constraints.NotBlank;

public record ExtendSessionRequest(
    @NotBlank(message = "A senha é obrigatória")
    String password
) {}
