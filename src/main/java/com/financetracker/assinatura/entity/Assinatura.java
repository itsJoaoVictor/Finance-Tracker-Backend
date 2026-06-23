package com.financetracker.assinatura.entity;

import com.financetracker.assinatura.enums.TipoRecorrencia;
import com.financetracker.assinatura.enums.UnidadeFrequencia;
import com.financetracker.cartao.entity.Cartao;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.usuario.entity.Usuario;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assinaturas")
public class Assinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_id", nullable = false)
    private Cartao cartao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_recorrencia", nullable = false, length = 30)
    private TipoRecorrencia tipoRecorrencia;

    @Column
    private Integer frequencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidade_frequencia", length = 20)
    private UnidadeFrequencia unidadeFrequencia;

    @Column(name = "dia_cobranca", nullable = false)
    private int diaCobranca;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_proxima_cobranca", nullable = false)
    private LocalDate dataProximaCobranca;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public Assinatura() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public Cartao getCartao() { return cartao; }
    public void setCartao(Cartao cartao) { this.cartao = cartao; }
    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public TipoRecorrencia getTipoRecorrencia() { return tipoRecorrencia; }
    public void setTipoRecorrencia(TipoRecorrencia tipoRecorrencia) { this.tipoRecorrencia = tipoRecorrencia; }
    public Integer getFrequencia() { return frequencia; }
    public void setFrequencia(Integer frequencia) { this.frequencia = frequencia; }
    public UnidadeFrequencia getUnidadeFrequencia() { return unidadeFrequencia; }
    public void setUnidadeFrequencia(UnidadeFrequencia unidadeFrequencia) { this.unidadeFrequencia = unidadeFrequencia; }
    public int getDiaCobranca() { return diaCobranca; }
    public void setDiaCobranca(int diaCobranca) { this.diaCobranca = diaCobranca; }
    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }
    public LocalDate getDataProximaCobranca() { return dataProximaCobranca; }
    public void setDataProximaCobranca(LocalDate dataProximaCobranca) { this.dataProximaCobranca = dataProximaCobranca; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}