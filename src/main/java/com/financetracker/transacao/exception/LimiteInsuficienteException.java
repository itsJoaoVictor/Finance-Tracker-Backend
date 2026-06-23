package com.financetracker.transacao.exception;

public class LimiteInsuficienteException extends RuntimeException {
    public LimiteInsuficienteException(String message) {
        super(message);
    }
}