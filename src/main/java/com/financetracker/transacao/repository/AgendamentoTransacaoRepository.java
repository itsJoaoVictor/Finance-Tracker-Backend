package com.financetracker.transacao.repository;

import com.financetracker.transacao.entity.AgendamentoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgendamentoTransacaoRepository extends JpaRepository<AgendamentoTransacao, UUID> {

    List<AgendamentoTransacao> findByUsuarioIdAndAtivoTrue(UUID usuarioId);

    Optional<AgendamentoTransacao> findByIdAndUsuarioIdAndAtivoTrue(UUID id, UUID usuarioId);

    List<AgendamentoTransacao> findByAtivoTrueAndDataProximaExecucaoLessThanEqual(LocalDate data);

    List<AgendamentoTransacao> findByContaOrigemIdOrContaDestinoId(UUID contaOrigemId, UUID contaDestinoId);
}