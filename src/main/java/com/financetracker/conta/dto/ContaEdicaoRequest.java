package com.financetracker.conta.dto;

import com.financetracker.conta.model.TipoConta;
import jakarta.validation.constraints.*;

public record ContaEdicaoRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotNull TipoConta tipo,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String corHexadecimal,
        Boolean contaPadrao
) {}
