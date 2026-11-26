package com.financetracker.transacao.dto;

import java.time.LocalDate;

public record FaturaVencimentoRequest(
    LocalDate dataVencimento,
    Boolean restaurarPadrao
) {}
