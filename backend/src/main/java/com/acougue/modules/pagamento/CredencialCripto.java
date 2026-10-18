package com.acougue.modules.pagamento;

import com.acougue.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra os segredos das adquirentes (access token, secret key, webhook secret)
 * antes de irem para o banco. AES-256-GCM com chave derivada (SHA-256) da
 * variável de ambiente PAGAMENTO_CRIPTO_CHAVE.
 *
 * Sem a chave configurada o sistema continua subindo normalmente — só não
 * deixa SALVAR credenciais (erro explicando o que configurar). Trocar a chave
 * depois invalida as credenciais já salvas: será preciso cadastrá-las de novo.
 *
 * Formato gravado: "v1:" + base64(iv[12] || ciphertext+tag).
 */
@Component
public class CredencialCripto {

    private static final String PREFIXO = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec chave;
    private final SecureRandom random = new SecureRandom();

    public CredencialCripto(@Value("${pagamento.cripto-chave:}") String segredo) {
        this.chave = (segredo == null || segredo.isBlank()) ? null : derivar(segredo);
    }

    public boolean configurada() {
        return chave != null;
    }

    public String cifrar(String textoPlano) {
        if (textoPlano == null || textoPlano.isBlank()) return null;
        exigirChave();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));
            byte[] saida = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return PREFIXO + Base64.getEncoder().encodeToString(saida);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao cifrar credencial", e);
        }
    }

    public String decifrar(String armazenado) {
        if (armazenado == null || armazenado.isBlank()) return null;
        exigirChave();
        if (!armazenado.startsWith(PREFIXO)) {
            throw new BusinessException("Credencial salva em formato desconhecido. Cadastre-a novamente.");
        }
        try {
            byte[] dados = Base64.getDecoder().decode(armazenado.substring(PREFIXO.length()));
            ByteBuffer buffer = ByteBuffer.wrap(dados);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] cifrado = new byte[buffer.remaining()];
            buffer.get(cifrado);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Tag GCM inválida = chave trocada ou dado adulterado
            throw new BusinessException(
                    "Não foi possível ler a credencial salva (a PAGAMENTO_CRIPTO_CHAVE mudou?). Cadastre-a novamente.");
        }
    }

    private void exigirChave() {
        if (chave == null) {
            throw new BusinessException(
                    "Chave de criptografia não configurada. Defina a variável de ambiente " +
                    "PAGAMENTO_CRIPTO_CHAVE no servidor (ex: Railway) antes de salvar credenciais.");
        }
    }

    private static SecretKeySpec derivar(String segredo) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(segredo.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
