package com.financetracker.transacao.exception;

public class TagNaoEncontradaException extends RuntimeException {
    public TagNaoEncontradaException() {
        super("Tag não encontrada.");
    }
}