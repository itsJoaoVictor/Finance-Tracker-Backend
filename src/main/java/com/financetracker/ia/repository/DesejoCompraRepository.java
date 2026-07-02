package com.financetracker.ia.repository;

import com.financetracker.ia.entity.DesejoCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DesejoCompraRepository extends JpaRepository<DesejoCompra, UUID> {
    List<DesejoCompra> findByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId);
}
