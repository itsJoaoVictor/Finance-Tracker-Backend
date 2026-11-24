package com.financetracker.contapagar.dto;

import java.util.List;
import java.util.UUID;

public record VincularItensFinanciamentoRequest(
        List<UUID> modeloIds,
        List<UUID> transacaoIds
) {}
