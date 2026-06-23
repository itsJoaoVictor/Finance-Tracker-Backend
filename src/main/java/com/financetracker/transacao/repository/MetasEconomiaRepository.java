package com.financetracker.transacao.repository;

import com.financetracker.transacao.entity.MetasEconomia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MetasEconomiaRepository extends JpaRepository<MetasEconomia, UUID> {

    List<MetasEconomia> findByUsuarioIdAndAtivoTrue(UUID usuarioId);

    Optional<MetasEconomia> findByIdAndUsuarioIdAndAtivoTrue(UUID id, UUID usuarioId);

    boolean existsByIdAndUsuarioId(UUID id, UUID usuarioId);
}