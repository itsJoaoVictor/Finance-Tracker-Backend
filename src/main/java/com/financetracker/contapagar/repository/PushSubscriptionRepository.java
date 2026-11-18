package com.financetracker.contapagar.repository;

import com.financetracker.contapagar.entity.PushSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, UUID> {

    List<PushSubscriptionEntity> findByUsuarioId(UUID usuarioId);

    Optional<PushSubscriptionEntity> findByUsuarioIdAndEndpoint(UUID usuarioId, String endpoint);

    void deleteByUsuarioIdAndEndpoint(UUID usuarioId, String endpoint);
}
