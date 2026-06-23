package com.financetracker.transacao.dto;

import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.TipoPagamentoFatura;
import com.financetracker.transacao.enums.TipoTransacao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TransacaoResponse(
    UUID id,
    String descricao,
    BigDecimal valor,
    String tipo,
    UUID contaOrigemId,
    UUID contaDestinoId,
    String contaOrigemNome,
    String contaDestinoNome,
    UUID cartaoId,
    UUID faturaId,
    UUID categoriaId,
    LocalDate data,
    Integer numeroParcela,
    Integer totalParcelas,
    TipoPagamentoFatura tipoPagamentoFatura,
    Boolean estornada,
    List<UUID> tagIds,
    AlertaOrcamento alertaOrcamento,
    LocalDateTime criadoEm
) {
    public TransacaoResponse(Transacao t) {
        this(t.getId(), t.getDescricao(), t.getValor(), t.getTipo().name(),
             t.getContaOrigem() != null ? t.getContaOrigem().getId() : null,
             t.getContaDestino() != null ? t.getContaDestino().getId() : null,
             t.getContaOrigem() != null ? t.getContaOrigem().getNome() : null,
             t.getContaDestino() != null ? t.getContaDestino().getNome() : null,
             t.getCartao() != null ? t.getCartao().getId() : null,
             t.getFatura() != null ? t.getFatura().getId() : null,
             t.getCategoria() != null ? t.getCategoria().getId() : null,
             t.getData(), t.getNumeroParcela(), t.getTotalParcelas(),
             t.getTipoPagamentoFatura(), t.getEstornada(), null, null, t.getCriadoEm());
    }

    public TransacaoResponse withAlerta(AlertaOrcamento alerta) {
        return new TransacaoResponse(id, descricao, valor, tipo, contaOrigemId, contaDestinoId,
            contaOrigemNome, contaDestinoNome,
            cartaoId, faturaId, categoriaId, data, numeroParcela, totalParcelas,
            tipoPagamentoFatura, estornada, tagIds, alerta, criadoEm);
    }

    public TransacaoResponse withTags(List<UUID> tagIds) {
        return new TransacaoResponse(id, descricao, valor, tipo, contaOrigemId, contaDestinoId,
            contaOrigemNome, contaDestinoNome,
            cartaoId, faturaId, categoriaId, data, numeroParcela, totalParcelas,
            tipoPagamentoFatura, estornada, tagIds, alertaOrcamento, criadoEm);
    }

    public record AlertaOrcamento(
        boolean atingido,
        double percentual,
        BigDecimal limite,
        BigDecimal consumido
    ) {}
}