package com.financetracker.contapagar.entity;

import com.financetracker.usuario.entity.Usuario;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "financiamentos")
public class Financiamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 255)
    private String descricao;

    @Column(name = "cor_hexadecimal", length = 7)
    private String corHexadecimal = "#7c5cfc";

    @Column(name = "valor_pago_anteriormente", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorPagoAnteriormente = BigDecimal.ZERO;

    @Column(name = "valor_contratado", precision = 15, scale = 2)
    private BigDecimal valorContratado;

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

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getCorHexadecimal() { return corHexadecimal; }
    public void setCorHexadecimal(String corHexadecimal) { this.corHexadecimal = corHexadecimal; }

    public BigDecimal getValorPagoAnteriormente() { return valorPagoAnteriormente; }
    public void setValorPagoAnteriormente(BigDecimal valorPagoAnteriormente) { this.valorPagoAnteriormente = valorPagoAnteriormente; }

    public BigDecimal getValorContratado() { return valorContratado; }
    public void setValorContratado(BigDecimal valorContratado) { this.valorContratado = valorContratado; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
