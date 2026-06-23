package com.financetracker.transacao.repository;

import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.TipoTransacao;
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

    List<Transacao> findByUsuarioIdAndAtivoTrueOrderByDataDesc(UUID usuarioId);

    Optional<Transacao> findByIdAndUsuarioIdAndAtivoTrue(UUID id, UUID usuarioId);

    long countByUsuarioIdAndAtivoTrue(UUID usuarioId);

    List<Transacao> findByFaturaIdAndAtivoTrue(UUID faturaId);

    List<Transacao> findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
        UUID usuarioId, LocalDate inicio, LocalDate fim);

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
}