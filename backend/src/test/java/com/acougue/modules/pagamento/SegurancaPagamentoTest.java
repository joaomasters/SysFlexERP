package com.acougue.modules.pagamento;

import com.acougue.entity.ModoIntegracaoTerminal;
import com.acougue.entity.ProvedorPagamento;
import com.acougue.exception.BusinessException;
import com.acougue.modules.pagamento.dto.SalvarTerminalDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Pagamentos — criptografia, assinatura de webhook e validação de terminais")
class SegurancaPagamentoTest {

    @Nested
    @DisplayName("CredencialCripto")
    class Cripto {

        @Test
        @DisplayName("cifra e decifra; o texto gravado não contém o segredo e muda a cada vez")
        void idaEVolta() {
            CredencialCripto cripto = new CredencialCripto("chave-de-teste");
            String a = cripto.cifrar("APP_USR-123-segredo");
            String b = cripto.cifrar("APP_USR-123-segredo");

            assertThat(a).startsWith("v1:").doesNotContain("segredo");
            assertThat(a).isNotEqualTo(b); // IV aleatório
            assertThat(cripto.decifrar(a)).isEqualTo("APP_USR-123-segredo");
        }

        @Test
        @DisplayName("chave diferente não consegue ler a credencial")
        void chaveErrada() {
            String cifrado = new CredencialCripto("chave-A").cifrar("token");

            assertThatThrownBy(() -> new CredencialCripto("chave-B").decifrar(cifrado))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("PAGAMENTO_CRIPTO_CHAVE");
        }

        @Test
        @DisplayName("sem chave configurada, salvar segredo é bloqueado com mensagem clara")
        void semChave() {
            CredencialCripto cripto = new CredencialCripto("");
            assertThat(cripto.configurada()).isFalse();
            assertThatThrownBy(() -> cripto.cifrar("token"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("PAGAMENTO_CRIPTO_CHAVE");
        }
    }

    @Nested
    @DisplayName("Assinatura do webhook Mercado Pago")
    class Assinatura {

        private String hmac(String manifesto, String segredo) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
        }

        @Test
        @DisplayName("aceita assinatura correta e recusa adulterada ou ausente")
        void valida() throws Exception {
            String v1 = hmac("id:123456;request-id:req-1;ts:1704908010;", "segredo");
            String header = "ts=1704908010,v1=" + v1;

            assertThat(AssinaturaWebhookMercadoPago.valida(header, "req-1", "123456", "segredo")).isTrue();
            assertThat(AssinaturaWebhookMercadoPago.valida(header, "req-1", "999999", "segredo")).isFalse();
            assertThat(AssinaturaWebhookMercadoPago.valida(header, "req-1", "123456", "outro")).isFalse();
            assertThat(AssinaturaWebhookMercadoPago.valida(null, "req-1", "123456", "segredo")).isFalse();
        }

        @Test
        @DisplayName("id alfanumérico entra em minúsculas no manifesto")
        void idMinusculo() throws Exception {
            String v1 = hmac("id:abc123;request-id:r;ts:1;", "s");
            assertThat(AssinaturaWebhookMercadoPago.valida("ts=1,v1=" + v1, "r", "ABC123", "s")).isTrue();
        }
    }

    @Nested
    @DisplayName("Validação de terminal")
    class Terminal {

        private SalvarTerminalDTO dto(ProvedorPagamento p, ModoIntegracaoTerminal modo) {
            SalvarTerminalDTO d = new SalvarTerminalDTO();
            d.setNome("Caixa 1");
            d.setProvedor(p);
            d.setModoIntegracao(modo);
            return d;
        }

        @Test
        @DisplayName("Sicredi não pode usar API em nuvem")
        void sicrediSemNuvem() {
            SalvarTerminalDTO d = dto(ProvedorPagamento.SICREDI, ModoIntegracaoTerminal.API_NUVEM);
            d.setIdentificadorDispositivo("X");
            assertThatThrownBy(() -> TerminalPagamentoService.validar(d))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Agente local");
        }

        @Test
        @DisplayName("nuvem exige identificador; agente local exige URL http(s)")
        void camposObrigatorios() {
            assertThatThrownBy(() -> TerminalPagamentoService.validar(
                    dto(ProvedorPagamento.MERCADO_PAGO, ModoIntegracaoTerminal.API_NUVEM)))
                    .hasMessageContaining("identificador");

            SalvarTerminalDTO agente = dto(ProvedorPagamento.STONE, ModoIntegracaoTerminal.AGENTE_LOCAL);
            agente.setAgenteUrl("javascript:alert(1)");
            assertThatThrownBy(() -> TerminalPagamentoService.validar(agente))
                    .hasMessageContaining("URL do agente");

            agente.setAgenteUrl("http://localhost:12345");
            assertThatCode(() -> TerminalPagamentoService.validar(agente)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("manual aceita qualquer adquirente, mas precisa de débito ou crédito")
        void manual() {
            SalvarTerminalDTO d = dto(ProvedorPagamento.SICREDI, ModoIntegracaoTerminal.MANUAL);
            assertThatCode(() -> TerminalPagamentoService.validar(d)).doesNotThrowAnyException();

            d.setAceitaDebito(false);
            d.setAceitaCredito(false);
            assertThatThrownBy(() -> TerminalPagamentoService.validar(d)).hasMessageContaining("débito, crédito");
        }
    }
}
