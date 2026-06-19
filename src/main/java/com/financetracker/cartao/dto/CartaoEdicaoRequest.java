package com.financetracker.cartao.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record CartaoEdicaoRequest(
    @NotBlank(message = "O nome é obrigatório.")
    @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
    String nome,

    @NotNull(message = "O limite é obrigatório.")
    @DecimalMin(value = "0.0", message = "O limite deve ser maior ou igual a zero.")
    BigDecimal limite,

    @NotNull(message = "O dia de fechamento é obrigatório.")
    @Min(value = 1, message = "O dia de fechamento deve ser entre 1 e 31.")
    @Max(value = 31, message = "O dia de fechamento deve ser entre 1 e 31.")
    Integer diaFechamento,

    @NotNull(message = "O dia de vencimento é obrigatório.")
    @Min(value = 1, message = "O dia de vencimento deve ser entre 1 e 31.")
    @Max(value = 31, message = "O dia de vencimento deve ser entre 1 e 31.")
    Integer diaVencimento,

    @NotNull(message = "A conta vinculada é obrigatória.")
    UUID contaId,

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "A cor deve ser um código hexadecimal válido (ex: #FFFFFF).")
    String corHexadecimal
) {}
