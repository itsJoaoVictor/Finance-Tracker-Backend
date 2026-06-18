package com.financetracker.conta.entity;

import com.financetracker.conta.model.TipoConta;
import com.financetracker.usuario.entity.Usuario;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "contas")
public class Conta {

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
    private TipoConta tipo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(name = "conta_padrao", nullable = false)
    private Boolean contaPadrao = false;

    @Column(name = "cor_hexadecimal", length = 7)
    private String corHexadecimal;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public Conta() {}

    public UUID getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public TipoConta getTipo() { return tipo; }
    public void setTipo(TipoConta tipo) { this.tipo = tipo; }
    public BigDecimal getSaldo() { return saldo; }
    public void setSaldo(BigDecimal saldo) { this.saldo = saldo; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public Boolean getContaPadrao() { return contaPadrao; }
    public void setContaPadrao(Boolean contaPadrao) { this.contaPadrao = contaPadrao; }
    public String getCorHexadecimal() { return corHexadecimal; }
    public void setCorHexadecimal(String corHexadecimal) { this.corHexadecimal = corHexadecimal; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
