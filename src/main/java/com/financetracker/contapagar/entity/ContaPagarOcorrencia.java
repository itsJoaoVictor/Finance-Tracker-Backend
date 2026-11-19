package com.financetracker.contapagar.entity;

import com.financetracker.contapagar.enums.StatusOcorrencia;
import com.financetracker.contapagar.util.DiaUtilUtils;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.usuario.entity.Usuario;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "contas_pagar_ocorrencia")
public class ContaPagarOcorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modelo_id", nullable = false)
    private ContaPagarModelo modelo;

    @Column(name = "mes_referencia", nullable = false)
    private LocalDate mesReferencia;

    @Column(name = "numero_parcela")
    private Integer numeroParcela;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Column(name = "valor_estimado", precision = 15, scale = 2)
    private BigDecimal valorEstimado;

    @Column(name = "valor_real", precision = 15, scale = 2)
    private BigDecimal valorReal;

    @Column(name = "valor_pago", precision = 15, scale = 2)
    private BigDecimal valorPago;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusOcorrencia status;

    @Column(name = "linha_digitavel", columnDefinition = "TEXT")
    private String linhaDigitavel;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transacao_id")
    private Transacao transacao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public boolean isAtrasada() {
        return isAtrasada(DiaUtilUtils.hoje());
    }

    public boolean isAtrasada(LocalDate hoje) {
        return (status == StatusOcorrencia.AGUARDANDO_VALOR || status == StatusOcorrencia.PENDENTE)
                && dataVencimento != null
                && hoje.isAfter(dataVencimento);
    }

    public String getStatusEfetivo() {
        return isAtrasada() ? "ATRASADA" : status.name();
    }

    public BigDecimal getValorEfetivo() {
        if (valorPago != null) return valorPago;
        if (valorReal != null) return valorReal;
        if (valorEstimado != null) return valorEstimado;
        return BigDecimal.ZERO;
    }

    public BigDecimal getJurosMulta() {
        if (valorPago != null && valorReal != null && valorPago.compareTo(valorReal) > 0) {
            return valorPago.subtract(valorReal);
        }
        return BigDecimal.ZERO;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public ContaPagarModelo getModelo() { return modelo; }
    public void setModelo(ContaPagarModelo modelo) { this.modelo = modelo; }

    public LocalDate getMesReferencia() { return mesReferencia; }
    public void setMesReferencia(LocalDate mesReferencia) { this.mesReferencia = mesReferencia; }

    public Integer getNumeroParcela() { return numeroParcela; }
    public void setNumeroParcela(Integer numeroParcela) { this.numeroParcela = numeroParcela; }

    public LocalDate getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(LocalDate dataVencimento) { this.dataVencimento = dataVencimento; }

    public BigDecimal getValorEstimado() { return valorEstimado; }
    public void setValorEstimado(BigDecimal valorEstimado) { this.valorEstimado = valorEstimado; }

    public BigDecimal getValorReal() { return valorReal; }
    public void setValorReal(BigDecimal valorReal) { this.valorReal = valorReal; }

    public BigDecimal getValorPago() { return valorPago; }
    public void setValorPago(BigDecimal valorPago) { this.valorPago = valorPago; }

    public LocalDate getDataPagamento() { return dataPagamento; }
    public void setDataPagamento(LocalDate dataPagamento) { this.dataPagamento = dataPagamento; }

    public StatusOcorrencia getStatus() { return status; }
    public void setStatus(StatusOcorrencia status) { this.status = status; }

    public String getLinhaDigitavel() { return linhaDigitavel; }
    public void setLinhaDigitavel(String linhaDigitavel) { this.linhaDigitavel = linhaDigitavel; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public Transacao getTransacao() { return transacao; }
    public void setTransacao(Transacao transacao) { this.transacao = transacao; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
