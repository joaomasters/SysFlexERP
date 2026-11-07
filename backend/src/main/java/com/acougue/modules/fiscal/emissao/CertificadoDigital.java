package com.acougue.modules.fiscal.emissao;

import com.acougue.exception.BusinessException;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** O que interessa de um certificado A1 (.pfx): de quem é e até quando vale. */
public record CertificadoDigital(String titular, String cnpj, LocalDateTime validade) {

    // e-CNPJ: o CN termina com ":<CNPJ>" (ex.: "LOJA EXEMPLO LTDA:12345678000195")
    private static final Pattern CNPJ_NO_CN = Pattern.compile(":([0-9A-Z]{12}[0-9]{2})\\s*$");

    /** Abre o .pfx com a senha. Senha errada, arquivo inválido ou sem chave privada → BusinessException. */
    public static CertificadoDigital ler(byte[] pfx, String senha) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(pfx), senha == null ? new char[0] : senha.toCharArray());
            for (String alias : Collections.list(ks.aliases())) {
                if (ks.isKeyEntry(alias) && ks.getCertificate(alias) instanceof X509Certificate c) {
                    String cn = nomeComum(c.getSubjectX500Principal().getName());
                    Matcher m = CNPJ_NO_CN.matcher(cn);
                    return new CertificadoDigital(cn, m.find() ? m.group(1) : null,
                            LocalDateTime.ofInstant(c.getNotAfter().toInstant(), ZoneId.of("America/Sao_Paulo")));
                }
            }
            throw new BusinessException("O arquivo não tem a chave do certificado. Exporte o A1 com a chave privada (.pfx).");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Não foi possível abrir o certificado: confira o arquivo .pfx e a senha.");
        }
    }

    private static String nomeComum(String dn) {
        for (String parte : dn.split(",(?=[A-Z]+=)")) {
            if (parte.startsWith("CN=")) return parte.substring(3).trim();
        }
        return dn;
    }

    public boolean vencido() {
        return validade.isBefore(LocalDateTime.now());
    }
}
