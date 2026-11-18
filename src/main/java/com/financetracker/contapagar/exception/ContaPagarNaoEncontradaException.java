package com.financetracker.contapagar.exception;

public class ContaPagarNaoEncontradaException extends RuntimeException {
    public ContaPagarNaoEncontradaException() {
        super("Conta a pagar não encontrada.");
    }

    public ContaPagarNaoEncontradaException(String message) {
        super(message);
    }
}
