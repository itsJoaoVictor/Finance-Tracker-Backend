package com.financetracker.categoria.exception;

public class CategoriaGlobalImutavelException extends RuntimeException {
    public CategoriaGlobalImutavelException() {
        super("Não é permitido alterar ou inativar categorias padrão do sistema.");
    }
}
