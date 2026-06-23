package com.financetracker.assinatura.repository;

import com.financetracker.assinatura.entity.Assinatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssinaturaRepository extends JpaRepository<Assinatura, UUID> {

    List<Assinatura> findByUsuarioId(UUID usuarioId);

    Optional<Assinatura> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    List<Assinatura> findByAtivoTrueAndDataProximaCobrancaBetween(
        LocalDate inicio, LocalDate fim);

    List<Assinatura> findByAtivoTrueAndDataProximaCobrancaLessThanEqual(LocalDate data);
}