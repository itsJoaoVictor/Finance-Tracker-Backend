package com.financetracker.contapagar.entity;

import com.financetracker.conta.entity.Conta;
import com.financetracker.contapagar.enums.ModoAmortizacao;
import com.financetracker.contapagar.enums.OrigemAmortizacao;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.usuario.entity.Usuario;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "contas_pagar_amortizacao")
public class ContaPagarAmortizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modelo_id", nullable = false)
    private ContaPagarModelo modelo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemAmortizacao origem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id")
    private Conta conta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModoAmortizacao modo;

    @Column(name = "parcelas_eliminadas")
    private Integer parcelasEliminadas;

    @Column(name = "novo_valor_parcela", precision = 15, scale = 2)
    private BigDecimal novoValorParcela;

    @Column(length = 255)
    private String observacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transacao_id")
    private Transacao transacao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public ContaPagarModelo getModelo() { return modelo; }
    public void setModelo(ContaPagarModelo modelo) { this.modelo = modelo; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }

    public OrigemAmortizacao getOrigem() { return origem; }
    public void setOrigem(OrigemAmortizacao origem) { this.origem = origem; }

    public Conta getConta() { return conta; }
    public void setConta(Conta conta) { this.conta = conta; }

    public ModoAmortizacao getModo() { return modo; }
    public void setModo(ModoAmortizacao modo) { this.modo = modo; }

    public Integer getParcelasEliminadas() { return parcelasEliminadas; }
    public void setParcelasEliminadas(Integer parcelasEliminadas) { this.parcelasEliminadas = parcelasEliminadas; }

    public BigDecimal getNovoValorParcela() { return novoValorParcela; }
    public void setNovoValorParcela(BigDecimal novoValorParcela) { this.novoValorParcela = novoValorParcela; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public Transacao getTransacao() { return transacao; }
    public void setTransacao(Transacao transacao) { this.transacao = transacao; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
