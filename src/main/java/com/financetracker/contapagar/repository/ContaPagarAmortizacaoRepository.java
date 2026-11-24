package com.financetracker.contapagar.repository;

import com.financetracker.contapagar.entity.ContaPagarAmortizacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ContaPagarAmortizacaoRepository extends JpaRepository<ContaPagarAmortizacao, UUID> {

    List<ContaPagarAmortizacao> findByModeloIdOrderByDataDesc(UUID modeloId);

    List<ContaPagarAmortizacao> findByModeloIdIn(List<UUID> modeloIds);

    @Query("SELECT a.transacao.id FROM ContaPagarAmortizacao a WHERE a.usuario.id = :usuarioId AND a.transacao IS NOT NULL")
    Set<UUID> findTransacaoIdsVinculadasByUsuarioId(@Param("usuarioId") UUID usuarioId);
}
