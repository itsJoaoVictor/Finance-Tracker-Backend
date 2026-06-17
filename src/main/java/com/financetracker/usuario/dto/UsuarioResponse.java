package com.financetracker.usuario.dto;

import java.util.UUID;
import com.financetracker.usuario.entity.Usuario;

public record UsuarioResponse(
    UUID id,
    String name,
    String email
) {
    public UsuarioResponse(Usuario usuario) {
        this(usuario.getId(), usuario.getNome(), usuario.getEmail());
    }
}
