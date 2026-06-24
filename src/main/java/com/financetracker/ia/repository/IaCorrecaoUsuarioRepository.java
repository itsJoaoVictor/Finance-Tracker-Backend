package com.financetracker.ia.repository;

import com.financetracker.ia.domain.IaCorrecaoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface IaCorrecaoUsuarioRepository extends JpaRepository<IaCorrecaoUsuario, UUID> {
    List<IaCorrecaoUsuario> findByUsuarioId(UUID usuarioId);
}
