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

    long countByUsuarioIdAndAtivoTrue(UUID usuarioId);

    List<Transacao> findByFaturaIdAndAtivoTrue(UUID faturaId);

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
}