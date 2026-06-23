package com.financetracker.transacao.entity;

import com.financetracker.cartao.entity.Cartao;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.conta.entity.Conta;
import com.financetracker.transacao.enums.TipoPagamentoFatura;
import com.financetracker.transacao.enums.TipoTransacao;
import com.financetracker.assinatura.entity.Assinatura;
import com.financetracker.usuario.entity.Usuario;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transacoes")
public class Transacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 150)
    private String descricao;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "varchar(30) default 'DEPOSITO'")
    private TipoTransacao tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_origem_id")
    private Conta contaOrigem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_destino_id")
    private Conta contaDestino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_id")
    private Cartao cartao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fatura_id")
    private Fatura fatura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meta_origem_id")
    private MetasEconomia metaOrigem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meta_destino_id")
    private MetasEconomia metaDestino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "numero_parcela")
    private Integer numeroParcela;

    @Column(name = "total_parcelas")
    private Integer totalParcelas;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pagamento_fatura", length = 30)
    private TipoPagamentoFatura tipoPagamentoFatura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agendamento_id")
    private AgendamentoTransacao agendamento;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean estornada = false;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean ativo = true;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public Transacao() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public TipoTransacao getTipo() { return tipo; }
    public void setTipo(TipoTransacao tipo) { this.tipo = tipo; }
    public Conta getContaOrigem() { return contaOrigem; }
    public void setContaOrigem(Conta contaOrigem) { this.contaOrigem = contaOrigem; }
    public Conta getContaDestino() { return contaDestino; }
    public void setContaDestino(Conta contaDestino) { this.contaDestino = contaDestino; }
    public Cartao getCartao() { return cartao; }
    public void setCartao(Cartao cartao) { this.cartao = cartao; }
    public Fatura getFatura() { return fatura; }
    public void setFatura(Fatura fatura) { this.fatura = fatura; }
    public MetasEconomia getMetaOrigem() { return metaOrigem; }
    public void setMetaOrigem(MetasEconomia metaOrigem) { this.metaOrigem = metaOrigem; }
    public MetasEconomia getMetaDestino() { return metaDestino; }
    public void setMetaDestino(MetasEconomia metaDestino) { this.metaDestino = metaDestino; }
    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public Integer getNumeroParcela() { return numeroParcela; }
    public void setNumeroParcela(Integer numeroParcela) { this.numeroParcela = numeroParcela; }
    public Integer getTotalParcelas() { return totalParcelas; }
    public void setTotalParcelas(Integer totalParcelas) { this.totalParcelas = totalParcelas; }
    public TipoPagamentoFatura getTipoPagamentoFatura() { return tipoPagamentoFatura; }
    public void setTipoPagamentoFatura(TipoPagamentoFatura tipoPagamentoFatura) { this.tipoPagamentoFatura = tipoPagamentoFatura; }
    public AgendamentoTransacao getAgendamento() { return agendamento; }
    public void setAgendamento(AgendamentoTransacao agendamento) { this.agendamento = agendamento; }
    public Boolean getEstornada() { return estornada; }
    public void setEstornada(Boolean estornada) { this.estornada = estornada; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}