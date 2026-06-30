package com.financetracker.ia.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DominioEfeitoDominoResponse(
    List<AlertaCartao> alertas,
    String mensagem
) {
    public record AlertaCartao(
        UUID cartaoId,
        String cartaoNome,
        BigDecimal limiteDisponivel,
        BigDecimal totalCobranca,
        int diasRestantes,
        int essenciaisAfetadas,
        int importantesAfetadas,
        int opcionaisAfetadas,
        String nivelAlerta,
        List<ItemRanking> ranking,
        List<String> recomendacoes
    ) {}

    public record ItemRanking(
        UUID assinaturaId,
        String nome,
        BigDecimal valor,
        String essencialidade,
        LocalDate dataCobranca,
        boolean falha
    ) {}
}
