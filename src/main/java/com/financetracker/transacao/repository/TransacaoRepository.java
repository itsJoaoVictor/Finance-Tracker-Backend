package com.financetracker.transacao.repository;

import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.TipoTransacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransacaoRepository extends JpaRepository<Transacao, UUID> {

    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.ativo = true " +
           "AND (t.numeroParcela IS NULL OR t.numeroParcela = 1) " +
           "AND t.tipo IN (:tipos) " +
           "AND (LOWER(t.descricao) LIKE LOWER(:descricaoPattern)) " +
           "AND t.data >= :dataInicio " +
           "AND t.data <= :dataFim " +
           "ORDER BY t.data DESC, t.criadoEm DESC")
    Page<Transacao> findFiltered(
        @Param("usuarioId") UUID usuarioId,
        @Param("tipos") List<TipoTransacao> tipos,
        @Param("descricaoPattern") String descricaoPattern,
        @Param("dataInicio") LocalDate dataInicio,
        @Param("dataFim") LocalDate dataFim,
        Pageable pageable
    );

    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.ativo = true " +
           "AND (t.numeroParcela IS NULL OR t.numeroParcela = 1) " +
           "ORDER BY t.data DESC, t.criadoEm DESC")
    List<Transacao> findByUsuarioIdAndAtivoTrueOrderByDataDesc(@Param("usuarioId") UUID usuarioId);

    Optional<Transacao> findByIdAndUsuarioIdAndAtivoTrue(UUID id, UUID usuarioId);

    List<Transacao> findByCartaoId(UUID cartaoId);

    List<Transacao> findByContaOrigemIdOrContaDestinoId(UUID contaOrigemId, UUID contaDestinoId);

    long countByUsuarioIdAndAtivoTrue(UUID usuarioId);

    List<Transacao> findByFaturaIdAndAtivoTrue(UUID faturaId);

    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.cartao.id = :cartaoId " +
           "AND t.descricao = :descricao AND t.totalParcelas = :totalParcelas AND t.ativo = true " +
           "ORDER BY t.numeroParcela ASC")
    List<Transacao> findParcelasAtivas(
        @Param("usuarioId") UUID usuarioId,
        @Param("cartaoId") UUID cartaoId,
        @Param("descricao") String descricao,
        @Param("totalParcelas") int totalParcelas);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.fatura.id = :faturaId " +
           "AND t.ativo = true AND t.tipo = 'COMPRA_CREDITO'")
    BigDecimal sumValorByFaturaIdAndAtivoTrue(@Param("faturaId") UUID faturaId);

    /** Soma apenas crédito rotativo (parcela única ou primeira parcela de assinatura) — exclui parcelas 2/3+ */
    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.fatura.id = :faturaId " +
           "AND t.ativo = true AND t.tipo = 'COMPRA_CREDITO' " +
           "AND (t.totalParcelas IS NULL OR t.totalParcelas <= 1)")
    BigDecimal sumRotativoByFaturaId(@Param("faturaId") UUID faturaId);

    @Query("SELECT COALESCE(SUM(CASE WHEN f.status != 'PAGA' THEN f.valorTotal - f.valorPago ELSE 0 END), 0) " +
           "FROM Fatura f WHERE f.cartao.id = :cartaoId " +
           "AND NOT (f.status = 'ATRASADA' AND f.rolladoOver = true)")
    BigDecimal sumFaturaAbertaByCartaoId(@Param("cartaoId") UUID cartaoId);

    List<Transacao> findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
        UUID usuarioId, LocalDate inicio, LocalDate fim);

    List<Transacao> findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataDesc(
        UUID usuarioId, LocalDate inicio, LocalDate fim);

    List<Transacao> findByUsuarioIdAndAtivoTrueAndDataAfter(UUID usuarioId, LocalDate data);



    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.ativo = true " +
           "AND LOWER(t.descricao) LIKE LOWER(CONCAT('%', :descricao, '%')) " +
           "ORDER BY t.criadoEm DESC")
    List<Transacao> findTopByDescricaoLike(@Param("usuarioId") UUID usuarioId,
                                           @Param("descricao") String descricao);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId " +
           "AND t.categoria.id = :categoriaId AND t.ativo = true AND t.data BETWEEN :inicio AND :fim " +
           "AND t.tipo IN (:tipos)")
    BigDecimal sumValorByCategoriaAndPeriodo(@Param("usuarioId") UUID usuarioId,
                                              @Param("categoriaId") UUID categoriaId,
                                              @Param("inicio") LocalDate inicio,
                                              @Param("fim") LocalDate fim,
                                              @Param("tipos") List<TipoTransacao> tipos);

    // ── Novas queries para insights comportamentais ───────────────────────────

    /** Conta o número de transações ativas de uma categoria no período (qualquer tipo). */
    @Query("SELECT COUNT(t) FROM Transacao t WHERE t.usuario.id = :usuarioId " +
           "AND t.ativo = true AND t.categoria.id = :categoriaId " +
           "AND t.data BETWEEN :inicio AND :fim")
    long countByCategoriaAndPeriodo(@Param("usuarioId") UUID usuarioId,
                                     @Param("categoriaId") UUID categoriaId,
                                     @Param("inicio") LocalDate inicio,
                                     @Param("fim") LocalDate fim);

    /** Soma o valor de todas as transações ativas de uma categoria no período (qualquer tipo). */
    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId " +
           "AND t.ativo = true AND t.categoria.id = :categoriaId " +
           "AND t.data BETWEEN :inicio AND :fim")
    BigDecimal sumValorByCategoriaAndPeriodoSemFiltroTipo(@Param("usuarioId") UUID usuarioId,
                                                           @Param("categoriaId") UUID categoriaId,
                                                           @Param("inicio") LocalDate inicio,
                                                           @Param("fim") LocalDate fim);

    /** Soma o valor total de todas as transações ativas do usuário no período (qualquer tipo). */
    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId " +
           "AND t.ativo = true AND t.data BETWEEN :inicio AND :fim")
    BigDecimal sumValorTotalByPeriodo(@Param("usuarioId") UUID usuarioId,
                                       @Param("inicio") LocalDate inicio,
                                       @Param("fim") LocalDate fim);

    // ── Queries para projeção híbrida de fatura ───────────────────────────

    /** Soma gastos variáveis por cartão (exclui parcelas 2/3+ e assinaturas). */
    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t " +
           "WHERE t.usuario.id = :usuarioId " +
           "AND t.cartao.id = :cartaoId " +
           "AND t.ativo = true " +
           "AND t.tipo = 'COMPRA_CREDITO' " +
           "AND (t.numeroParcela IS NULL OR t.numeroParcela = 1) " +
           "AND (LOWER(t.descricao) NOT LIKE 'assinatura:%') " +
           "AND t.data BETWEEN :inicio AND :fim")
    BigDecimal sumGastosVariaveisPorCartao(
        @Param("usuarioId") UUID usuarioId,
        @Param("cartaoId") UUID cartaoId,
        @Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim);

    /** Soma assinaturas já cobradas no mês (transações "Assinatura: %"). */
    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t " +
           "WHERE t.usuario.id = :usuarioId " +
           "AND t.cartao.id = :cartaoId " +
           "AND t.ativo = true " +
           "AND t.tipo = 'COMPRA_CREDITO' " +
           "AND (LOWER(t.descricao) LIKE 'assinatura:%') " +
           "AND t.data BETWEEN :inicio AND :fim")
    BigDecimal sumAssinaturasCobradasNoMes(
        @Param("usuarioId") UUID usuarioId,
        @Param("cartaoId") UUID cartaoId,
        @Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim);

    // ── Queries para Planejador de Compras (IA) ───────────────────────────

    /** Soma receitas (DEPOSITO) ignorando resgates de cofrinho */
    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId " +
           "AND t.ativo = true AND t.tipo = 'DEPOSITO' " +
           "AND t.metaOrigem IS NULL " +
           "AND t.data BETWEEN :inicio AND :fim")
    BigDecimal sumReceitasValidasPorPeriodo(@Param("usuarioId") UUID usuarioId,
                                            @Param("inicio") LocalDate inicio,
                                            @Param("fim") LocalDate fim);

    /** Soma despesas básicas (SAQUE, PIX) ignorando aportes em cofrinho */
    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId " +
           "AND t.ativo = true AND t.tipo IN ('SAQUE', 'PIX') " +
           "AND t.metaDestino IS NULL " +
           "AND t.data BETWEEN :inicio AND :fim")
    BigDecimal sumDespesasBasicasPorPeriodo(@Param("usuarioId") UUID usuarioId,
                                            @Param("inicio") LocalDate inicio,
                                            @Param("fim") LocalDate fim);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.contaOrigem.id = :contaId AND t.ativo = true AND t.data > :data")
    BigDecimal sumValorByContaOrigemAndDataAfter(@Param("contaId") UUID contaId, @Param("data") LocalDate data);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.contaDestino.id = :contaId AND t.ativo = true AND t.data > :data")
    BigDecimal sumValorByContaDestinoAndDataAfter(@Param("contaId") UUID contaId, @Param("data") LocalDate data);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.cartao.id = :cartaoId AND t.ativo = true AND t.tipo = :tipo AND t.data > :data")
    BigDecimal sumValorByCartaoAndDataAfterAndTipo(@Param("cartaoId") UUID cartaoId, @Param("tipo") TipoTransacao tipo, @Param("data") LocalDate data);
}