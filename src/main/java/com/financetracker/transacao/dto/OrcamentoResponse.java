package com.financetracker.transacao.dto;

import com.financetracker.transacao.entity.OrcamentoCategoria;
import java.math.BigDecimal;
import java.util.UUID;

public record OrcamentoResponse(
    UUID id,
    UUID categoriaId,
    BigDecimal limiteMensal
) {
    public OrcamentoResponse(OrcamentoCategoria o) {
        this(o.getId(), o.getCategoria().getId(), o.getLimiteMensal());
    }
}