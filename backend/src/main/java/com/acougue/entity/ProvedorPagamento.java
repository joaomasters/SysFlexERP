package com.acougue.entity;

/** Adquirentes de cartão suportadas. O nome é gravado no banco — não renomear. */
public enum ProvedorPagamento {
    STONE("Stone"),
    MERCADO_PAGO("Mercado Pago"),
    SICREDI("Sicredi"),
    /** App Smart TEF da Veloce na maquininha (ex.: Máquina Smart Sicredi com M-SiTef). */
    VELOCE("Veloce Smart TEF");

    private final String rotulo;

    ProvedorPagamento(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Provedores com API em nuvem implementada (ver modules.pagamento.provedor). */
    public boolean suportaApiNuvem() {
        return this == STONE || this == MERCADO_PAGO || this == VELOCE;
    }
}
