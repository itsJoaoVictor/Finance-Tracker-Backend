package com.financetracker.transacao.exception;

public class FaturaNaoEncontradaException extends RuntimeException {
    public FaturaNaoEncontradaException() {
        super("Fatura não encontrada.");
    }
}