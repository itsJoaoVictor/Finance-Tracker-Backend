package com.financetracker.transacao.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TransacaoCriacaoRequest(
    @Size(max = 150) String descricao,
    @NotNull @DecimalMin("0.01") BigDecimal valor,
    @NotNull String tipo,
    UUID contaOrigemId,
    UUID contaDestinoId,
    UUID cartaoId,
    UUID categoriaId,
    @NotNull LocalDate data,
    @Min(1) @Max(96) Integer totalParcelas,
    List<UUID> tagIds,
    UUID metaOrigemId,
    UUID metaDestinoId
) {}