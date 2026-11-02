package com.acougue.modules.jarvis;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Locale;

/*
 Código de 6 dígitos que muda a cada 30 segundos (RFC 6238), o mesmo do Google
 Authenticator e similares. Sem biblioteca: são poucas linhas sobre o HMAC do Java.
 */
final class Totp {

    private static final int PERIODO_SEGUNDOS = 30;
    private static final int JANELA_TOLERANCIA = 1; // relógio do celular um pouco fora de hora
    private static final String ALFABETO_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {}

    static boolean valido(String segredoBase32, String codigo, long epochSegundos) {
        if (codigo == null || !codigo.matches("\\d{6}")) return false;
        byte[] chave = decodificarBase32(segredoBase32);
        long passo = epochSegundos / PERIODO_SEGUNDOS;
        boolean confere = false;
        for (int i = -JANELA_TOLERANCIA; i <= JANELA_TOLERANCIA; i++) {
            confere |= MessageDigest.isEqual(
                    gerar(chave, passo + i).getBytes(StandardCharsets.US_ASCII),
                    codigo.getBytes(StandardCharsets.US_ASCII));
        }
        return confere;
    }

    static String gerar(byte[] chave, long passo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(chave, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(passo).array());
            int inicio = hash[hash.length - 1] & 0x0F;
            int numero = ByteBuffer.wrap(hash, inicio, 4).getInt() & 0x7FFFFFFF;
            return String.format("%06d", numero % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 indisponível", e);
        }
    }

    /** @throws IllegalArgumentException se o texto não for Base32 */
    static byte[] decodificarBase32(String texto) {
        String limpo = texto.trim().replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        int acumulado = 0;
        int bits = 0;
        for (char c : limpo.toCharArray()) {
            int valor = ALFABETO_BASE32.indexOf(c);
            if (valor < 0) throw new IllegalArgumentException("Segredo fora do formato Base32");
            acumulado = (acumulado << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                saida.write((acumulado >> bits) & 0xFF);
            }
        }
        return saida.toByteArray();
    }
}
