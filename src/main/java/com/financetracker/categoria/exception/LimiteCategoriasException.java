package com.financetracker.categoria.exception;

public class LimiteCategoriasException extends RuntimeException {
    public LimiteCategoriasException() {
        super("Limite máximo de 50 categorias customizadas atingido.");
    }
}
