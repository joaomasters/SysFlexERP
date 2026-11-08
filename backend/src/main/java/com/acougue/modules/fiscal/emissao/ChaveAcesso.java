package com.acougue.modules.fiscal.emissao;

import java.time.LocalDateTime;

/**
 * Chave de acesso da NF-e/NFC-e (44 posições): UF, AAMM, CNPJ, modelo, série, número,
 * tipo de emissão, código numérico e o dígito verificador (módulo 11, pesos 2 a 9).
 * Com CNPJ alfanumérico, cada caractere vale o código ASCII menos 48, como no CNPJ.
 */
public final class ChaveAcesso {

    /** Código IBGE do Paraná. */
    public static final String UF_PR = "41";

    private ChaveAcesso() {}

    public static String gerar(String cUf, LocalDateTime emissao, String cnpj, int modelo, int serie, int numero,
                               int tipoEmissao, int codigoNumerico) {
        String base = cUf
                + String.format("%02d%02d", emissao.getYear() % 100, emissao.getMonthValue())
                + cnpj
                + String.format("%02d%03d%09d%d%08d", modelo, serie, numero, tipoEmissao, codigoNumerico);
        if (base.length() != 43) {
            throw new IllegalArgumentException("Dados inválidos para a chave de acesso: " + base);
        }
        return base + digito(base);
    }

    static int digito(String base) {
        int soma = 0;
        int peso = 2;
        for (int i = base.length() - 1; i >= 0; i--) {
            soma += (base.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    public static boolean valida(String chave) {
        return chave != null && chave.matches("[0-9A-Z]{43}[0-9]")
                && digito(chave.substring(0, 43)) == chave.charAt(43) - '0';
    }
}
