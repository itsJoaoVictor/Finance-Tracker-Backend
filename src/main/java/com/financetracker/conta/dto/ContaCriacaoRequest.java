package com.financetracker.conta.dto;

import com.financetracker.conta.model.TipoConta;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ContaCriacaoRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotNull TipoConta tipo,
        @NotNull @DecimalMin(value = "0.0") BigDecimal saldo,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String corHexadecimal,
        Boolean contaPadrao
) {}
