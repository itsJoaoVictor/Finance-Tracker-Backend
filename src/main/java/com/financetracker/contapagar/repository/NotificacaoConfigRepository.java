package com.financetracker.contapagar.repository;

import com.financetracker.contapagar.entity.NotificacaoConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificacaoConfigRepository extends JpaRepository<NotificacaoConfig, UUID> {

    Optional<NotificacaoConfig> findByUsuarioId(UUID usuarioId);
}
