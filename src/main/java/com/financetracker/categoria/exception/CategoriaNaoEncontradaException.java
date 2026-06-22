package com.financetracker.categoria.exception;

public class CategoriaNaoEncontradaException extends RuntimeException {
    public CategoriaNaoEncontradaException() {
        super("Categoria não encontrada.");
    }
}
