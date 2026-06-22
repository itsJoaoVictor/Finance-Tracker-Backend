package com.financetracker.categoria.exception;

public class NomeCategoriaDuplicadoException extends RuntimeException {
    public NomeCategoriaDuplicadoException() {
        super("Já existe uma categoria ativa com este nome.");
    }
}
