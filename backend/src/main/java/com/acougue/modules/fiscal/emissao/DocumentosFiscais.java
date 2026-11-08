package com.acougue.modules.fiscal.emissao;

/** Conferência dos documentos do emitente (dígitos verificadores), sem consultar a Receita. */
public final class DocumentosFiscais {

    private DocumentosFiscais() {}

    /**
     * CNPJ numérico ou alfanumérico (emitido a partir de jul/2026): 12 caracteres [0-9A-Z]
     * + 2 dígitos verificadores. No cálculo, cada caractere vale o código ASCII menos 48,
     * o que mantém o resultado de sempre para os CNPJs só de números.
     */
    public static boolean cnpjValido(String cnpj) {
        if (cnpj == null || !cnpj.matches("[0-9A-Z]{12}[0-9]{2}") || cnpj.matches("(.)\\1{13}")) return false;
        return digitoCnpj(cnpj, 12) == cnpj.charAt(12) - '0' && digitoCnpj(cnpj, 13) == cnpj.charAt(13) - '0';
    }

    private static int digitoCnpj(String cnpj, int tamanho) {
        int soma = 0;
        int peso = 2;
        for (int i = tamanho - 1; i >= 0; i--) {
            soma += (cnpj.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /** CPF com os dois dígitos verificadores (o do consumidor no "CPF na nota"). */
    public static boolean cpfValido(String cpf) {
        if (cpf == null || !cpf.matches("[0-9]{11}") || cpf.matches("(.)\\1{10}")) return false;
        return digitoCpf(cpf, 9) == cpf.charAt(9) - '0' && digitoCpf(cpf, 10) == cpf.charAt(10) - '0';
    }

    private static int digitoCpf(String cpf, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) soma += (cpf.charAt(i) - '0') * (tamanho + 1 - i);
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /** Inscrição Estadual do Paraná: 8 dígitos + 2 verificadores (módulo 11, pesos do Sintegra). */
    public static boolean inscricaoEstadualPrValida(String ie) {
        if (ie == null || !ie.matches("[0-9]{10}")) return false;
        int d1 = digitoIePr(ie, new int[]{3, 2, 7, 6, 5, 4, 3, 2});
        int d2 = digitoIePr(ie, new int[]{4, 3, 2, 7, 6, 5, 4, 3, 2});
        return d1 == ie.charAt(8) - '0' && d2 == ie.charAt(9) - '0';
    }

    private static int digitoIePr(String ie, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) soma += (ie.charAt(i) - '0') * pesos[i];
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /** Só letras e números, em maiúsculas (tira pontos, barras e traços). */
    public static String limpar(String valor) {
        if (valor == null) return null;
        String limpo = valor.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
        return limpo.isEmpty() ? null : limpo;
    }
}
