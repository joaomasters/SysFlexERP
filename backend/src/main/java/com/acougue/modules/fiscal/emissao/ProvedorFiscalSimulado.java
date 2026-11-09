package com.acougue.modules.fiscal.emissao;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * "Autoriza" NFC-e sem falar com a SEFAZ, para testar o PDV e o DANFE enquanto não há
 * provedor contratado. Só existe com fiscal.provedor-simulado=true e só em homologação,
 * em que o DANFE sai marcado "SEM VALOR FISCAL". A chave e o QR Code seguem o formato real.
 */
@Component
@ConditionalOnProperty(name = "fiscal.provedor-simulado", havingValue = "true")
public class ProvedorFiscalSimulado implements ProvedorFiscal {

    static final String URL_QRCODE_PR = "http://www.fazenda.pr.gov.br/nfce/qrcode";
    static final String URL_CONSULTA_PR = "http://www.fazenda.pr.gov.br/nfce/consulta";

    private final SecureRandom aleatorio = new SecureRandom();

    @Override
    public String codigo() {
        return "SIMULADO";
    }

    @Override
    public String rotulo() {
        return "Simulado (testes, sem SEFAZ)";
    }

    @Override
    public boolean simulado() {
        return true;
    }

    @Override
    public RetornoEmissao emitirNfce(NfceEmissao nota, CredencialFiscal credencial) {
        if (!"HOMOLOGACAO".equals(nota.ambiente())) {
            return RetornoEmissao.erro("O provedor simulado só emite em homologação.");
        }
        String chave = ChaveAcesso.gerar(ChaveAcesso.UF_PR, nota.dataEmissao(), nota.emitente().cnpj(), 65,
                nota.serie(), nota.numero(), 1, 10_000_000 + aleatorio.nextInt(90_000_000));
        String protocolo = "9" + String.format("%014d", Math.abs(aleatorio.nextLong()) % 100_000_000_000_000L);
        return new RetornoEmissao("AUTORIZADA", chave, protocolo, "Autorizado o uso da NFC-e (simulado)",
                urlQrCode(chave, 2, credencial.cscId(), credencial.cscToken()), URL_CONSULTA_PR, null,
                "SIM-" + chave, LocalDateTime.now());
    }

    /**
     * QR Code versão 2, emissão on-line: chave|2|ambiente|id do CSC|hash, e o hash é o
     * SHA-1 (hexadecimal maiúsculo) desses quatro campos seguidos do código CSC.
     */
    static String urlQrCode(String chave, int tpAmb, String cscId, String cscToken) {
        String parametros = chave + "|2|" + tpAmb + "|" + Integer.parseInt(cscId);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1")
                    .digest((parametros + cscToken).getBytes(StandardCharsets.UTF_8));
            return URL_QRCODE_PR + "?p=" + parametros + "|" + HexFormat.of().withUpperCase().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
