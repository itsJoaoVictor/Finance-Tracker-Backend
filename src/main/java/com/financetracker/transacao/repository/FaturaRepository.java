package com.financetracker.transacao.repository;

import com.financetracker.transacao.entity.Fatura;
import com.financetracker.transacao.enums.StatusFatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

@Repository
public interface FaturaRepository extends JpaRepository<Fatura, UUID> {

    Optional<Fatura> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    List<Fatura> findByCartaoIdAndUsuarioIdOrderByMesReferenciaDesc(UUID cartaoId, UUID usuarioId);

    Optional<Fatura> findByCartaoIdAndStatus(UUID cartaoId, StatusFatura status);

    Optional<Fatura> findByCartaoIdAndStatusAndUsuarioId(UUID cartaoId, StatusFatura status, UUID usuarioId);

    Optional<Fatura> findByCartaoIdAndUsuarioIdAndMesReferencia(UUID cartaoId, UUID usuarioId, LocalDate mesReferencia);

    List<Fatura> findByUsuarioIdAndStatus(UUID usuarioId, StatusFatura status);

    List<Fatura> findByUsuarioId(UUID usuarioId);

    List<Fatura> findByCartaoId(UUID cartaoId);

    boolean existsByCartaoIdAndStatus(UUID cartaoId, StatusFatura status);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(f.valorTotal - f.valorPago), 0) FROM Fatura f WHERE f.usuario.id = :usuarioId " +
           "AND f.mesReferencia = :mesReferencia AND f.cartao.ativo = true AND f.status != 'PAGA'")
    BigDecimal sumValorTotalByUsuarioAndMesReferencia(@org.springframework.data.repository.query.Param("usuarioId") UUID usuarioId,
                                                      @org.springframework.data.repository.query.Param("mesReferencia") LocalDate mesReferencia);
}