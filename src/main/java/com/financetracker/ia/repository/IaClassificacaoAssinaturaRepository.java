package com.financetracker.ia.repository;

import com.financetracker.ia.domain.IaClassificacaoAssinatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IaClassificacaoAssinaturaRepository extends JpaRepository<IaClassificacaoAssinatura, UUID> {

    Optional<IaClassificacaoAssinatura> findByAssinaturaId(UUID assinaturaId);

    List<IaClassificacaoAssinatura> findByUsuarioId(UUID usuarioId);

    List<IaClassificacaoAssinatura> findByUsuarioIdAndConfirmadoFalse(UUID usuarioId);

    /** Classificações confirmadas há mais de N dias — precisa de revisão */
    List<IaClassificacaoAssinatura> findByUsuarioIdAndConfirmadoTrueAndAtualizadoEmBefore(UUID usuarioId, java.time.LocalDateTime dataLimite);

    boolean existsByAssinaturaIdAndConfirmadoTrue(UUID assinaturaId);
}
