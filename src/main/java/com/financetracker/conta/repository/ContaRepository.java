package com.financetracker.conta.repository;

import com.financetracker.conta.entity.Conta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContaRepository extends JpaRepository<Conta, UUID> {

    List<Conta> findByUsuarioIdAndAtivoTrue(UUID usuarioId);

    Optional<Conta> findByIdAndUsuarioIdAndAtivoTrue(UUID id, UUID usuarioId);

    long countByUsuarioIdAndAtivoTrue(UUID usuarioId);

    @Modifying
    @Query("UPDATE Conta c SET c.contaPadrao = false WHERE c.usuario.id = :usuarioId")
    void clearContaPadraoByUsuarioId(@Param("usuarioId") UUID usuarioId);
}
