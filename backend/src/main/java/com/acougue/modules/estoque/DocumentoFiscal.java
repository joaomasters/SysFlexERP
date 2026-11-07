package com.acougue.modules.estoque;

/**
 * Validação e formatação de CPF/CNPJ (dígitos verificadores).
 * Aceita o número com ou sem máscara.
 */
public final class DocumentoFiscal {

    private DocumentoFiscal() {}

    public static String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    public static boolean cpfValido(String valor) {
        String d = somenteDigitos(valor);
        if (d.length() != 11 || todosIguais(d)) return false;
        return digito(d, 9, 10) == d.charAt(9) - '0' && digito(d, 10, 11) == d.charAt(10) - '0';
    }

    public static boolean cnpjValido(String valor) {
        String d = somenteDigitos(valor);
        if (d.length() != 14 || todosIguais(d)) return false;
        int[] p1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] p2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return digitoCnpj(d, p1) == d.charAt(12) - '0' && digitoCnpj(d, p2) == d.charAt(13) - '0';
    }

    /** Aplica a máscara 000.000.000-00 ou 00.000.000/0000-00 conforme o tamanho. */
    public static String formatar(String valor) {
        String d = somenteDigitos(valor);
        if (d.length() == 11) {
            return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
        }
        if (d.length() == 14) {
            return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "/"
                    + d.substring(8, 12) + "-" + d.substring(12);
        }
        return valor;
    }

    private static int digito(String d, int qtd, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < qtd; i++) soma += (d.charAt(i) - '0') * (pesoInicial - i);
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    private static int digitoCnpj(String d, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) soma += (d.charAt(i) - '0') * pesos[i];
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static boolean todosIguais(String d) {
        return d.chars().distinct().count() == 1;
    }
}