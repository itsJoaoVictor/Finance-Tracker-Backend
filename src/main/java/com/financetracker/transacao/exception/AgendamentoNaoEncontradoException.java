package com.financetracker.transacao.exception;

public class AgendamentoNaoEncontradoException extends RuntimeException {
    public AgendamentoNaoEncontradoException() {
        super("Agendamento não encontrado.");
    }
}