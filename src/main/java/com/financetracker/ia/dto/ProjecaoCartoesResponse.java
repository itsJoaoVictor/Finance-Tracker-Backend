package com.financetracker.ia.dto;

import java.util.List;

public record ProjecaoCartoesResponse(
    List<ProjecaoCartaoDTO> projecoes,
    long totalCartoes,
    boolean dadosInsuficientes
) {}
