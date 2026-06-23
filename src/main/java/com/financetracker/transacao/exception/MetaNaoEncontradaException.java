package com.financetracker.transacao.exception;

public class MetaNaoEncontradaException extends RuntimeException {
    public MetaNaoEncontradaException() {
        super("Meta de economia não encontrada.");
    }
}