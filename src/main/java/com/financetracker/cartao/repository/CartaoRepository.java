package com.financetracker.cartao.repository;

import com.financetracker.cartao.entity.Cartao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartaoRepository extends JpaRepository<Cartao, UUID> {

    List<Cartao> findByUsuarioIdAndAtivoTrue(UUID usuarioId);

    Optional<Cartao> findByIdAndUsuarioIdAndAtivoTrue(UUID id, UUID usuarioId);

    long countByUsuarioIdAndAtivoTrue(UUID usuarioId);

    List<Cartao> findByContaId(UUID contaId);
}
