package com.financetracker.ia.repository;

import com.financetracker.ia.domain.IaClassificacaoAssinatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IaClassificacaoAssinaturaRepository extends JpaRepository<IaClassificacaoAssinatura, UUID> {

    Optional<IaClassificacaoAssinatura> findByAssinaturaId(UUID assinaturaId);

    List<IaClassificacaoAssinatura> findByUsuarioId(UUID usuarioId);

    List<IaClassificacaoAssinatura> findByUsuarioIdAndConfirmadoFalse(UUID usuarioId);

    List<IaClassificacaoAssinatura> findByUsuarioIdAndConfirmadoTrue(UUID usuarioId);

    boolean existsByAssinaturaIdAndConfirmadoTrue(UUID assinaturaId);

    @Modifying
    @Query("DELETE FROM IaClassificacaoAssinatura c WHERE c.assinatura.id IN :ids")
    void deleteByAssinaturaIds(@Param("ids") List<UUID> assinaturaIds);
}

