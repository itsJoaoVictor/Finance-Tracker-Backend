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
    String categoriaNome,
    UUID metaOrigemId,
    String metaOrigemNome,
    UUID metaDestinoId,
    String metaDestinoNome,
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
             t.getCategoria() != null ? t.getCategoria().getNome() : null,
             t.getMetaOrigem() != null ? t.getMetaOrigem().getId() : null,
             t.getMetaOrigem() != null ? t.getMetaOrigem().getNome() : null,
             t.getMetaDestino() != null ? t.getMetaDestino().getId() : null,
             t.getMetaDestino() != null ? t.getMetaDestino().getNome() : null,
             t.getData(), t.getNumeroParcela(), t.getTotalParcelas(),
             t.getTipoPagamentoFatura(), t.getEstornada(), null, null, t.getCriadoEm());
    }

    private TransacaoResponse withExtras(List<UUID> tagIds, AlertaOrcamento alerta) {
        return new TransacaoResponse(id, descricao, valor, tipo, contaOrigemId, contaDestinoId,
            contaOrigemNome, contaDestinoNome,
            cartaoId, faturaId, categoriaId, categoriaNome,
            metaOrigemId, metaOrigemNome, metaDestinoId, metaDestinoNome,
            data, numeroParcela, totalParcelas,
            tipoPagamentoFatura, estornada, tagIds, alerta, criadoEm);
    }

    public TransacaoResponse withAlerta(AlertaOrcamento alerta) {
        return withExtras(tagIds, alerta);
    }

    public TransacaoResponse withTags(List<UUID> tagIds) {
        return withExtras(tagIds, alertaOrcamento);
    }

    public record AlertaOrcamento(
        boolean atingido,
        double percentual,
        BigDecimal limite,
        BigDecimal consumido
    ) {}
}