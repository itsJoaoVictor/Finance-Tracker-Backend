package com.financetracker.transacao.repository;

import com.financetracker.transacao.entity.Fatura;
import com.financetracker.transacao.enums.StatusFatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FaturaRepository extends JpaRepository<Fatura, UUID> {

    Optional<Fatura> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    List<Fatura> findByCartaoIdAndUsuarioIdOrderByMesReferenciaDesc(UUID cartaoId, UUID usuarioId);

    Optional<Fatura> findByCartaoIdAndStatus(UUID cartaoId, StatusFatura status);

    Optional<Fatura> findByCartaoIdAndStatusAndUsuarioId(UUID cartaoId, StatusFatura status, UUID usuarioId);

    Optional<Fatura> findByCartaoIdAndUsuarioIdAndMesReferencia(UUID cartaoId, UUID usuarioId, LocalDate mesReferencia);

    List<Fatura> findByUsuarioIdAndStatus(UUID usuarioId, StatusFatura status);

    List<Fatura> findByUsuarioId(UUID usuarioId);

    boolean existsByCartaoIdAndStatus(UUID cartaoId, StatusFatura status);
}