package com.financetracker.transacao.dto;

import com.financetracker.transacao.entity.MetasEconomia;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MetaEconomiaResponse(
    UUID id,
    String nome,
    BigDecimal valorAlvo,
    BigDecimal valorAcumulado,
    UUID contaVinculadaId,
    LocalDateTime criadoEm
) {
    public MetaEconomiaResponse(MetasEconomia m) {
        this(m.getId(), m.getNome(), m.getValorAlvo(), m.getValorAcumulado(),
             m.getContaVinculada().getId(), m.getCriadoEm());
    }
}