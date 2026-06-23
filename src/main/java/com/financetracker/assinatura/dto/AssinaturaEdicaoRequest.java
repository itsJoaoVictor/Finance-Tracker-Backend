package com.financetracker.assinatura.dto;

import com.financetracker.assinatura.enums.TipoRecorrencia;
import com.financetracker.assinatura.enums.UnidadeFrequencia;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AssinaturaEdicaoRequest(
    @NotBlank @Size(max = 100) String nome,
    @NotNull @DecimalMin("0.01") BigDecimal valor,
    @NotNull UUID cartaoId,
    @NotNull UUID categoriaId,
    @NotNull TipoRecorrencia tipoRecorrencia,
    @Min(1) Integer frequencia,
    UnidadeFrequencia unidadeFrequencia,
    @NotNull @Min(1) @Max(31) Integer diaCobranca,
    @NotNull LocalDate dataInicio,
    Boolean ativo
) {}