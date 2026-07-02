package com.financetracker.ia.dto;

import java.util.List;

public record SimulacaoCompraResponse(
    boolean viavel,
    String mesRecomendadoParaCompra,
    Integer parcelasRecomendadas,
    String mensagemRecomendacao,
    List<MesSimulacaoDTO> simulacoesMesAMes,
    AnaliseCartaoSimulacaoDTO analiseCartao // Opcional: nulo se nenhum cartão for selecionado na simulação
) {}
