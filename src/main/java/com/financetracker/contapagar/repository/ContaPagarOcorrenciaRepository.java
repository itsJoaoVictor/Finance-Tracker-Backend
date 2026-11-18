package com.financetracker.contapagar.repository;

import com.financetracker.contapagar.entity.ContaPagarOcorrencia;
import com.financetracker.contapagar.enums.StatusOcorrencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContaPagarOcorrenciaRepository extends JpaRepository<ContaPagarOcorrencia, UUID> {

    Optional<ContaPagarOcorrencia> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    Optional<ContaPagarOcorrencia> findByModeloIdAndMesReferencia(UUID modeloId, LocalDate mesReferencia);

    Optional<ContaPagarOcorrencia> findByTransacaoId(UUID transacaoId);

    List<ContaPagarOcorrencia> findByModeloIdOrderByMesReferenciaAsc(UUID modeloId);

    List<ContaPagarOcorrencia> findByModeloIdAndStatusOrderByMesReferenciaDesc(UUID modeloId, StatusOcorrencia status);

    @Query("SELECT o FROM ContaPagarOcorrencia o JOIN FETCH o.modelo m " +
           "WHERE o.usuario.id = :usuarioId AND m.ativo = true " +
           "AND o.mesReferencia >= :inicio AND o.mesReferencia <= :fim " +
           "ORDER BY o.dataVencimento ASC, m.nome ASC")
    List<ContaPagarOcorrencia> findByUsuarioAndMesReferenciaBetween(
            @Param("usuarioId") UUID usuarioId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("SELECT o FROM ContaPagarOcorrencia o JOIN FETCH o.modelo m " +
           "WHERE o.usuario.id = :usuarioId AND m.ativo = true " +
           "AND o.dataVencimento >= :inicio AND o.dataVencimento <= :fim " +
           "ORDER BY o.dataVencimento ASC")
    List<ContaPagarOcorrencia> findByUsuarioAndVencimentoBetween(
            @Param("usuarioId") UUID usuarioId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("SELECT o FROM ContaPagarOcorrencia o JOIN FETCH o.modelo m " +
           "WHERE o.usuario.id = :usuarioId AND m.ativo = true " +
           "AND o.status IN ('AGUARDANDO_VALOR', 'PENDENTE') " +
           "AND o.dataVencimento <= :dataLimite " +
           "ORDER BY o.dataVencimento ASC")
    List<ContaPagarOcorrencia> findPendentesAteData(
            @Param("usuarioId") UUID usuarioId,
            @Param("dataLimite") LocalDate dataLimite);

    @Query("SELECT o FROM ContaPagarOcorrencia o JOIN FETCH o.modelo m " +
           "WHERE m.ativo = true AND o.status IN ('AGUARDANDO_VALOR', 'PENDENTE') " +
           "AND o.dataVencimento <= :dataLimite")
    List<ContaPagarOcorrencia> findAllPendentesAteData(@Param("dataLimite") LocalDate dataLimite);
}
