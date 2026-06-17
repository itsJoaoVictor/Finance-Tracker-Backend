package com.financetracker.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioUpdateRequest(
    @NotBlank(message = "O nome é obrigatório")
    @Size(min = 3, message = "O nome deve ter pelo menos 3 caracteres")
    String name,

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "E-mail inválido")
    String email
) {
    public UsuarioUpdateRequest {
        if (email != null) {
            email = email.trim().toLowerCase();
        }
        if (name != null) {
            name = name.trim();
        }
    }
}
