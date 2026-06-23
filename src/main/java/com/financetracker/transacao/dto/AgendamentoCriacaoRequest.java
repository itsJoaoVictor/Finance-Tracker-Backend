package com.financetracker.transacao.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AgendamentoCriacaoRequest(
    @NotBlank @Size(max = 150) String descricao,
    @NotNull @DecimalMin("0.01") BigDecimal valor,
    @NotNull String tipo,
    UUID contaOrigemId,
    UUID contaDestinoId,
    @NotNull UUID categoriaId,
    @NotNull String recorrencia,
    @Min(1) @Max(31) int diaExecucao,
    @NotNull LocalDate dataInicio
) {}