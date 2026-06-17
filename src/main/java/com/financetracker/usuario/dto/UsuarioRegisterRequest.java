package com.financetracker.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioRegisterRequest(
    @NotBlank(message = "O nome é obrigatório")
    @Size(min = 3, message = "O nome deve ter pelo menos 3 caracteres")
    String name,

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "E-mail inválido")
    String email,

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres")
    String password,

    @NotBlank(message = "A confirmação de senha é obrigatória")
    String confirmPassword
) {
    public UsuarioRegisterRequest {
        if (email != null) {
            email = email.trim().toLowerCase();
        }
        if (name != null) {
            name = name.trim();
        }
    }
}