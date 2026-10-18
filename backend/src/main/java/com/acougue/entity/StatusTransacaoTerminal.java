package com.acougue.entity;

public enum StatusTransacaoTerminal {
    PENDENTE, APROVADA, NEGADA, CANCELADA, ERRO;

    public boolean isFinal() {
        return this != PENDENTE;
    }
}
