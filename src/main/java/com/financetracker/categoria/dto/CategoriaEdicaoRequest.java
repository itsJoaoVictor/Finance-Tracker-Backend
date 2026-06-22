package com.financetracker.categoria.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoriaEdicaoRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(max = 50) String icone,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String corHexadecimal
) {}
