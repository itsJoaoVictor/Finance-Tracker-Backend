package com.financetracker.contapagar.repository;

import com.financetracker.contapagar.entity.NotificacaoEnviada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificacaoEnviadaRepository extends JpaRepository<NotificacaoEnviada, UUID> {

    boolean existsByOcorrenciaIdAndGatilho(UUID ocorrenciaId, String gatilho);
}
