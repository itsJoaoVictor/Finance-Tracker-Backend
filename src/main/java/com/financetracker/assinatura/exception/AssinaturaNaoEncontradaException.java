package com.financetracker.assinatura.exception;

public class AssinaturaNaoEncontradaException extends RuntimeException {
    public AssinaturaNaoEncontradaException() {
        super("Assinatura não encontrada.");
    }
}