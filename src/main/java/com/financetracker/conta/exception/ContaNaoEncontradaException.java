package com.financetracker.conta.exception;

public class ContaNaoEncontradaException extends RuntimeException {
    public ContaNaoEncontradaException() {
        super("Conta não encontrada.");
    }
}
