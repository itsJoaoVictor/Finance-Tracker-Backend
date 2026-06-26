package com.financetracker.ia.repository;

import com.financetracker.ia.domain.IaInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface IaInsightRepository extends JpaRepository<IaInsight, UUID> {
    List<IaInsight> findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(UUID usuarioId);

    /** Verifica no banco se já existe um insight não lido do tipo informado com a string fornecida nos metadados. */
    boolean existsByUsuarioIdAndTipoAndLidoFalseAndMetadadosContaining(
            UUID usuarioId, com.financetracker.ia.domain.TipoInsight tipo, String metadadosContains);
}
