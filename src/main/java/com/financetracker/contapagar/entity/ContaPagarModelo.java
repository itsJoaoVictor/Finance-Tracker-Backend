package com.financetracker.contapagar.entity;

import com.financetracker.categoria.entity.Categoria;
import com.financetracker.conta.entity.Conta;
import com.financetracker.contapagar.enums.TipoContaPagar;
import com.financetracker.usuario.entity.Usuario;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "contas_pagar_modelo")
public class ContaPagarModelo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 100)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoContaPagar tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(length = 50)
    private String agrupador;

    @Column(name = "dia_vencimento", nullable = false)
    private Integer diaVencimento;

    @Column(name = "valor_padrao", precision = 15, scale = 2)
    private BigDecimal valorPadrao;

    @Column(name = "total_parcelas")
    private Integer totalParcelas;

    @Column(name = "parcela_inicial")
    private Integer parcelaInicial;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "debito_automatico", nullable = false)
    private Boolean debitoAutomatico = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_padrao_id")
    private Conta contaPadrao;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public TipoContaPagar getTipo() { return tipo; }
    public void setTipo(TipoContaPagar tipo) { this.tipo = tipo; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }

    public String getAgrupador() { return agrupador; }
    public void setAgrupador(String agrupador) { this.agrupador = agrupador; }

    public Integer getDiaVencimento() { return diaVencimento; }
    public void setDiaVencimento(Integer diaVencimento) { this.diaVencimento = diaVencimento; }

    public BigDecimal getValorPadrao() { return valorPadrao; }
    public void setValorPadrao(BigDecimal valorPadrao) { this.valorPadrao = valorPadrao; }

    public Integer getTotalParcelas() { return totalParcelas; }
    public void setTotalParcelas(Integer totalParcelas) { this.totalParcelas = totalParcelas; }

    public Integer getParcelaInicial() { return parcelaInicial; }
    public void setParcelaInicial(Integer parcelaInicial) { this.parcelaInicial = parcelaInicial; }

    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }

    public LocalDate getDataFim() { return dataFim; }
    public void setDataFim(LocalDate dataFim) { this.dataFim = dataFim; }

    public Boolean getDebitoAutomatico() { return debitoAutomatico; }
    public void setDebitoAutomatico(Boolean debitoAutomatico) { this.debitoAutomatico = debitoAutomatico; }

    public Conta getContaPadrao() { return contaPadrao; }
    public void setContaPadrao(Conta contaPadrao) { this.contaPadrao = contaPadrao; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
