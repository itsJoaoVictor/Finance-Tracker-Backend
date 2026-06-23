package com.financetracker.transacao.exception;

public class TransacaoNaoEncontradaException extends RuntimeException {
    public TransacaoNaoEncontradaException() {
        super("Transação não encontrada.");
    }
}