package com.financetracker.transacao.repository;

import com.financetracker.transacao.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByUsuarioIdAndAtivoTrue(UUID usuarioId);

    Optional<Tag> findByIdAndUsuarioIdAndAtivoTrue(UUID id, UUID usuarioId);
}