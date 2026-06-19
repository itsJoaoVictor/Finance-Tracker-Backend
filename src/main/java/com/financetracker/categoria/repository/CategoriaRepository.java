package com.financetracker.categoria.repository;

import com.financetracker.categoria.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {

    @Query("SELECT c FROM Categoria c WHERE (c.usuario IS NULL AND c.ativo = true) OR (c.usuario.id = :usuarioId AND c.ativo = true) ORDER BY c.nome ASC")
    List<Categoria> findAllVisibleAndActive(@Param("usuarioId") UUID usuarioId);

    @Query("SELECT c FROM Categoria c WHERE (c.usuario IS NULL AND c.ativo = true) OR (c.usuario.id = :usuarioId) ORDER BY c.nome ASC")
    List<Categoria> findAllVisible(@Param("usuarioId") UUID usuarioId);

    long countByUsuarioIdAndAtivoTrue(UUID usuarioId);

    @Query("SELECT COUNT(c) > 0 FROM Categoria c WHERE c.ativo = true AND LOWER(c.nome) = LOWER(:nome) AND (c.usuario IS NULL OR c.usuario.id = :usuarioId)")
    boolean existsByNomeIgnoreCaseAndActiveAndVisible(@Param("nome") String nome, @Param("usuarioId") UUID usuarioId);

    @Query("SELECT COUNT(c) > 0 FROM Categoria c WHERE c.ativo = true AND LOWER(c.nome) = LOWER(:nome) AND (c.usuario IS NULL OR c.usuario.id = :usuarioId) AND c.id <> :excludeId")
    boolean existsByNomeIgnoreCaseAndActiveAndVisibleExcludingId(@Param("nome") String nome, @Param("usuarioId") UUID usuarioId, @Param("excludeId") UUID excludeId);
}
