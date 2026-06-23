package com.financetracker.transacao.dto;

import com.financetracker.transacao.entity.AgendamentoTransacao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AgendamentoResponse(
    UUID id,
    String descricao,
    BigDecimal valor,
    String tipo,
    UUID contaOrigemId,
    UUID contaDestinoId,
    UUID categoriaId,
    String recorrencia,
    int diaExecucao,
    LocalDate dataInicio,
    LocalDate dataProximaExecucao,
    Boolean ativo,
    LocalDateTime criadoEm
) {
    public AgendamentoResponse(AgendamentoTransacao a) {
        this(a.getId(), a.getDescricao(), a.getValor(), a.getTipo().name(),
             a.getContaOrigem() != null ? a.getContaOrigem().getId() : null,
             a.getContaDestino() != null ? a.getContaDestino().getId() : null,
             a.getCategoria().getId(), a.getRecorrencia().name(),
             a.getDiaExecucao(), a.getDataInicio(), a.getDataProximaExecucao(),
             a.getAtivo(), a.getCriadoEm());
    }
}