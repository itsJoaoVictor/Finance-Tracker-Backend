package com.financetracker.contapagar.repository;

import com.financetracker.contapagar.entity.ContaPagarModelo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContaPagarModeloRepository extends JpaRepository<ContaPagarModelo, UUID> {

    List<ContaPagarModelo> findByUsuarioIdAndAtivoTrueOrderByNomeAsc(UUID usuarioId);

    List<ContaPagarModelo> findByUsuarioIdAndFinanciamentoIdAndAtivoTrueOrderByNomeAsc(UUID usuarioId, UUID financiamentoId);

    List<ContaPagarModelo> findByUsuarioIdAndFinanciamentoIsNullAndAtivoTrueOrderByNomeAsc(UUID usuarioId);

    List<ContaPagarModelo> findByAtivoTrue();

    Optional<ContaPagarModelo> findByIdAndUsuarioIdAndAtivoTrue(UUID id, UUID usuarioId);
}
