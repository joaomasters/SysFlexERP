package com.acougue.modules.estoque;

import com.acougue.modules.fiscal.emissao.DocumentosFiscais;

/**
 * Validação e formatação de CPF/CNPJ do cadastro (clientes, fornecedores).
 * Aceita o número com ou sem máscara. O CNPJ pode ser alfanumérico (Receita, jul/2026): a regra
 * dos dígitos verificadores é uma só, em {@link DocumentosFiscais}.
 */
public final class DocumentoFiscal {

    private DocumentoFiscal() {}

    public static String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    /** Letras e dígitos em maiúsculas, sem pontos, barra e traço ("" quando vazio). */
    public static String normalizar(String valor) {
        return valor == null ? "" : valor.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
    }

    public static boolean cpfValido(String valor) {
        return DocumentosFiscais.cpfValido(normalizar(valor));
    }

    public static boolean cnpjValido(String valor) {
        return DocumentosFiscais.cnpjValido(normalizar(valor));
    }

    /** Aplica a máscara 000.000.000-00 ou 00.000.000/0000-00 conforme o tamanho. */
    public static String formatar(String valor) {
        String d = normalizar(valor);
        if (d.length() == 11 && d.chars().allMatch(Character::isDigit)) {
            return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
        }
        if (d.length() == 14) {
            return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "/"
                    + d.substring(8, 12) + "-" + d.substring(12);
        }
        return valor;
    }
}
