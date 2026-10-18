package com.acougue.entity;

public enum ModoIntegracaoTerminal {
    /** O servidor cobra direto na API da adquirente (Mercado Pago Point, Stone Connect). */
    API_NUVEM,
    /** O navegador do caixa chama um agente TEF instalado no computador (contrato em docs/MAQUININHAS.md). */
    AGENTE_LOCAL,
    /** Maquininha não integrada: o operador passa o cartão e digita NSU/autorização. */
    MANUAL
}
