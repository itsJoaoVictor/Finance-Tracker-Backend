package com.financetracker.transacao.repository;

import com.financetracker.transacao.entity.OrcamentoCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrcamentoCategoriaRepository extends JpaRepository<OrcamentoCategoria, UUID> {

    Optional<OrcamentoCategoria> findByUsuarioIdAndCategoriaId(UUID usuarioId, UUID categoriaId);

    List<OrcamentoCategoria> findByUsuarioId(UUID usuarioId);
}