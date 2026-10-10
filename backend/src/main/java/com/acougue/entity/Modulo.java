package com.acougue.entity;

public enum Modulo {
    PDV("PDV / Caixa"),
    SANGRIA("Sangria / Suprimento"),
    PRODUTOS("Produtos"),
    RECEBIMENTO("Recebimento"),
    FICHAS_DESOSSA("Fichas de Desossa"),
    RATEIO_DESOSSA("Rateio de Desossa"),
    INVENTARIO("Inventário"),
    PERDAS("Perdas"),
    NF_SAIDA("NF de Saída"),
    FATURAMENTO("Faturamento"),
    CONTAS_RECEBER("Contas a Receber"),
    CONTAS_PAGAR("Contas a Pagar"),
    DRE("DRE"),
    RELATORIOS("Relatórios"),
    CARGA_BALANCA("Carga Balança"),
    USUARIOS("Usuários"),
    PERFIS("Perfis"),
    CLIENTES("Clientes"),
    AUDITORIA("Auditoria"),
    COMISSOES("Comissões");

    private final String rotulo;

    Modulo(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}