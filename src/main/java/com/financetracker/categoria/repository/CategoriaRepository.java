package com.financetracker.categoria.repository;

import com.financetracker.categoria.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {

    long countByUsuarioIdAndAtivoTrue(UUID usuarioId);

    @Query("SELECT c FROM Categoria c WHERE (c.usuario.id = :usuarioId OR c.usuario IS NULL) AND c.ativo = true")
    List<Categoria> findAtivasByUsuarioId(@Param("usuarioId") UUID usuarioId);

    @Query("SELECT c FROM Categoria c WHERE c.usuario IS NULL OR c.usuario.id = :usuarioId")
    List<Categoria> findAllByUsuarioId(@Param("usuarioId") UUID usuarioId);

    @Query("SELECT COUNT(c) > 0 FROM Categoria c WHERE (c.usuario IS NULL OR c.usuario.id = :usuarioId) AND c.ativo = true AND LOWER(c.nome) = LOWER(:nome)")
    boolean existsByNomeAtivoAndUsuarioId(@Param("nome") String nome, @Param("usuarioId") UUID usuarioId);

    @Query("SELECT COUNT(c) > 0 FROM Categoria c WHERE (c.usuario IS NULL OR c.usuario.id = :usuarioId) AND c.ativo = true AND LOWER(c.nome) = LOWER(:nome) AND c.id <> :id")
    boolean existsByNomeAtivoAndUsuarioIdExcludeId(@Param("nome") String nome, @Param("usuarioId") UUID usuarioId, @Param("id") UUID id);
}
