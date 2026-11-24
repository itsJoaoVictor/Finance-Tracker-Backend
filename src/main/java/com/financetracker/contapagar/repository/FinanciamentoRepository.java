package com.financetracker.contapagar.repository;

import com.financetracker.contapagar.entity.Financiamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinanciamentoRepository extends JpaRepository<Financiamento, UUID> {

    List<Financiamento> findByUsuarioIdAndAtivoTrueOrderByNomeAsc(UUID usuarioId);

    Optional<Financiamento> findByIdAndUsuarioIdAndAtivoTrue(UUID id, UUID usuarioId);
}
