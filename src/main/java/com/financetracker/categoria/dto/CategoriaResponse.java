package com.financetracker.categoria.dto;

import com.financetracker.categoria.entity.Categoria;
import java.time.LocalDateTime;
import java.util.UUID;

public record CategoriaResponse(
        UUID id,
        UUID usuarioId,
        String nome,
        String icone,
        String corHexadecimal,
        Boolean ativo,
        LocalDateTime criadoEm
) {
    public CategoriaResponse(Categoria categoria) {
        this(
                categoria.getId(),
                categoria.getUsuario() != null ? categoria.getUsuario().getId() : null,
                categoria.getNome(),
                categoria.getIcone(),
                categoria.getCorHexadecimal(),
                categoria.getAtivo(),
                categoria.getCriadoEm()
        );
    }
}
