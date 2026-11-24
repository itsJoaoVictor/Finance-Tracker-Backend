package com.financetracker.contapagar.repository;

import com.financetracker.contapagar.entity.FinanciamentoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface FinanciamentoTransacaoRepository extends JpaRepository<FinanciamentoTransacao, UUID> {

    @Query("SELECT ft FROM FinanciamentoTransacao ft JOIN FETCH ft.transacao t LEFT JOIN FETCH t.categoria " +
           "WHERE ft.financiamento.id = :financiamentoId ORDER BY t.data DESC, ft.criadoEm DESC")
    List<FinanciamentoTransacao> findByFinanciamentoIdWithTransacao(@Param("financiamentoId") UUID financiamentoId);

    Optional<FinanciamentoTransacao> findByFinanciamentoIdAndTransacaoId(UUID financiamentoId, UUID transacaoId);

    boolean existsByTransacaoId(UUID transacaoId);

    @Query("SELECT ft.transacao.id FROM FinanciamentoTransacao ft WHERE ft.usuario.id = :usuarioId")
    Set<UUID> findTransacaoIdsVinculadasByUsuarioId(@Param("usuarioId") UUID usuarioId);
}
