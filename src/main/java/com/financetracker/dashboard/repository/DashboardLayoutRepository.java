package com.financetracker.dashboard.repository;

import com.financetracker.dashboard.entity.DashboardLayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DashboardLayoutRepository extends JpaRepository<DashboardLayout, UUID> {
    Optional<DashboardLayout> findByUsuarioId(UUID usuarioId);
}