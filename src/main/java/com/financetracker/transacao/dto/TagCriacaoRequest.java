package com.financetracker.transacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TagCriacaoRequest(
    @NotBlank @Size(max = 50) String nome,
    @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String corHexadecimal
) {}