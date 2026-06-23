package com.financetracker.transacao.exception;

public class PagamentoFaturaInvalidoException extends RuntimeException {
    public PagamentoFaturaInvalidoException(String message) {
        super(message);
    }
}