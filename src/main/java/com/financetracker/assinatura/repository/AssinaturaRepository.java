package com.financetracker.assinatura.repository;

import com.financetracker.assinatura.entity.Assinatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssinaturaRepository extends JpaRepository<Assinatura, UUID> {

    List<Assinatura> findByUsuarioId(UUID usuarioId);

    Optional<Assinatura> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    List<Assinatura> findByCartaoIdAndAtivoTrue(UUID cartaoId);

    List<Assinatura> findByCartaoId(UUID cartaoId);

    List<Assinatura> findByAtivoTrueAndDataProximaCobrancaBetween(
        LocalDate inicio, LocalDate fim);

    List<Assinatura> findByAtivoTrueAndDataProximaCobrancaLessThanEqual(LocalDate data);

    /** Busca assinaturas ativas de um cartão cuja próxima cobrança esteja no período. */
    List<Assinatura> findByCartaoIdAndAtivoTrueAndDataProximaCobrancaBetween(
        UUID cartaoId, LocalDate inicio, LocalDate fim);

    @Query("""
        SELECT a FROM Assinatura a
        WHERE a.usuario.id = :usuarioId
          AND a.ativo = true
          AND a.dataProximaCobranca BETWEEN :inicio AND :fim
        ORDER BY a.cartao.id, a.dataProximaCobranca
    """)
    List<Assinatura> findProximasCobrançasPorUsuario(
        @Param("usuarioId") UUID usuarioId,
        @Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim);
}