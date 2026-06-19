package com.financetracker.categoria.dto;

import jakarta.validation.constraints.*;

public record CategoriaRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(max = 50) String icone,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String corHexadecimal
) {}
