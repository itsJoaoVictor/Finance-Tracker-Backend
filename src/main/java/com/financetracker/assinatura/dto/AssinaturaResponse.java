package com.financetracker.assinatura.dto;

import com.financetracker.assinatura.entity.Assinatura;
import com.financetracker.assinatura.enums.TipoRecorrencia;
import com.financetracker.assinatura.enums.UnidadeFrequencia;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AssinaturaResponse(
    UUID id,
    String nome,
    BigDecimal valor,
    UUID cartaoId,
    UUID categoriaId,
    TipoRecorrencia tipoRecorrencia,
    Integer frequencia,
    UnidadeFrequencia unidadeFrequencia,
    int diaCobranca,
    LocalDate dataInicio,
    LocalDate dataProximaCobranca,
    Boolean ativo,
    LocalDateTime criadoEm
) {
    public AssinaturaResponse(Assinatura a) {
        this(a.getId(), a.getNome(), a.getValor(), a.getCartao().getId(),
             a.getCategoria().getId(), a.getTipoRecorrencia(),
             a.getFrequencia(), a.getUnidadeFrequencia(),
             a.getDiaCobranca(), a.getDataInicio(), a.getDataProximaCobranca(),
             a.getAtivo(), a.getCriadoEm());
    }
}