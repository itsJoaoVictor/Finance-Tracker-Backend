package com.financetracker.contapagar.repository;

import com.financetracker.contapagar.entity.ContaPagarAmortizacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContaPagarAmortizacaoRepository extends JpaRepository<ContaPagarAmortizacao, UUID> {

    List<ContaPagarAmortizacao> findByModeloIdOrderByDataDesc(UUID modeloId);
}
