package com.financetracker.cartao.exception;

public class CartaoNaoEncontradoException extends RuntimeException {
    public CartaoNaoEncontradoException() {
        super("Cartão não encontrado.");
    }
}
