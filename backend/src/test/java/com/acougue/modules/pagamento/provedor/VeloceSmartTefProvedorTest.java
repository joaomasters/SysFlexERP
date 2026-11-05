package com.acougue.modules.pagamento.provedor;

import com.acougue.entity.StatusTransacaoTerminal;
import com.acougue.exception.BusinessException;
import com.acougue.modules.pagamento.ClienteHttpPagamento;
import com.acougue.modules.pagamento.ClienteHttpPagamento.Resposta;
import com.acougue.modules.pagamento.provedor.ProvedorTerminalNuvem.CobrancaTerminal;
import com.acougue.modules.pagamento.provedor.ProvedorTerminalNuvem.RetornoProvedor;
import com.acougue.tenant.TenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Veloce Smart TEF")
class VeloceSmartTefProvedorTest {

    private static final String CREDENCIAL =
            "{\"login\":\"integracao@loja.com\",\"senha\":\"Senha#123456\",\"cnpj\":\"28.803.350/0001-05\"}";
    private static final String CNPJ = "28803350000105";

    private final ObjectMapper om = new ObjectMapper();
    private final ClienteHttpPagamento http = mock(ClienteHttpPagamento.class);
    private final VeloceSmartTefProvedor provedor =
            new VeloceSmartTefProvedor(http, om, mock(TenantRepository.class), "https://sistema.exemplo/",
                    VeloceSmartTefProvedor.BASE);

    private Resposta resposta(int status, String corpo) throws Exception {
        return new Resposta(status, corpo, om.readTree(corpo));
    }

    private void loginDevolve(String token) throws Exception {
        when(http.enviar(eq("POST"), eq(VeloceSmartTefProvedor.BASE + "/acessar"), any(), any()))
                .thenReturn(resposta(200, "{\"ok\":true,\"message\":\"Acesso permitido\",\"result\":{\"token\":\"" + token + "\"}}"));
    }

    private static CobrancaTerminal cobranca(String tipo, int parcelas) {
        return new CobrancaTerminal(42L, 7L, "EOIVM", tipo, parcelas, new BigDecimal("15.60"));
    }

    @Nested
    @DisplayName("Envio da cobrança")
    class Envio {

        @Test
        @DisplayName("faz login, envia para o token da maquininha e guarda o id da Veloce")
        void iniciar() throws Exception {
            loginDevolve("tok-1");
            when(http.enviar(eq("POST"), eq(VeloceSmartTefProvedor.BASE + "/pagamento/criar"), any(), any()))
                    .thenReturn(resposta(200, "{\"ok\":true,\"result\":{\"id_tbl_pagamento\":4,\"status\":0}}"));

            RetornoProvedor r = provedor.iniciar(cobranca("CREDITO", 3), CREDENCIAL);

            assertThat(r.status()).isEqualTo(StatusTransacaoTerminal.PENDENTE);
            assertThat(r.idExterno()).startsWith("4:");
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
            ArgumentCaptor<Object> corpo = ArgumentCaptor.forClass(Object.class);
            verify(http).enviar(eq("POST"), eq(VeloceSmartTefProvedor.BASE + "/pagamento/criar"), headers.capture(), corpo.capture());
            assertThat(headers.getValue()).containsEntry("Token", "tok-1");
            @SuppressWarnings("unchecked")
            Map<String, Object> enviado = (Map<String, Object>) corpo.getValue();
            assertThat(enviado).containsEntry("token", "EOIVM").containsEntry("cnpj", CNPJ)
                    .containsEntry("tipo", 1).containsEntry("parcelas", "3").containsEntry("indice", 0)
                    .containsEntry("url_retorno", "https://sistema.exemplo/api/pagamento/webhook/veloce");
            assertThat(enviado.get("valor")).isEqualTo(new BigDecimal("15.60"));
        }

        @Test
        @DisplayName("débito vai como tipo 0 e sempre à vista")
        void debito() {
            Map<String, Object> corpo = VeloceSmartTefProvedor.montarPagamento(cobranca("DEBITO", 5), 123L, CNPJ, "u");
            assertThat(corpo).containsEntry("tipo", 0).containsEntry("parcelas", "1").containsEntry("pedido", 7L);
        }

        @Test
        @DisplayName("PIX vai como tipo 3 e à vista")
        void pix() {
            Map<String, Object> corpo = VeloceSmartTefProvedor.montarPagamento(cobranca("PIX", 1), 123L, CNPJ, "u");
            assertThat(corpo).containsEntry("tipo", 3).containsEntry("parcelas", "1");
        }

        @Test
        @DisplayName("token da maquininha errado vira mensagem que diz onde conferir")
        void tokenDispositivoErrado() throws Exception {
            loginDevolve("tok-1");
            when(http.enviar(eq("POST"), eq(VeloceSmartTefProvedor.BASE + "/pagamento/criar"), any(), any()))
                    .thenReturn(resposta(404, "{\"ok\":false,\"message\":\"Token de dispositivo não encontrado\",\"result\":null}"));

            assertThatThrownBy(() -> provedor.iniciar(cobranca("DEBITO", 1), CREDENCIAL))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Dispositivos");
        }

        @Test
        @DisplayName("campos recusados pela Veloce aparecem na mensagem")
        void camposRecusados() throws Exception {
            loginDevolve("tok-1");
            when(http.enviar(eq("POST"), eq(VeloceSmartTefProvedor.BASE + "/pagamento/criar"), any(), any()))
                    .thenReturn(resposta(400, "{\"ok\":false,\"message\":\"Campos obrigatórios\","
                            + "\"result\":{\"nome_cliente\":[\"Campo obrigatório nome cliente\"]}}"));

            assertThatThrownBy(() -> provedor.iniciar(cobranca("PIX", 1), CREDENCIAL))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Campos obrigatórios")
                    .hasMessageContaining("Campo obrigatório nome cliente");
        }

        @Test
        @DisplayName("pagamento repetido NÃO vira aprovação, mesmo vindo pago")
        void pagamentoExistente() throws Exception {
            loginDevolve("tok-1");
            when(http.enviar(eq("POST"), eq(VeloceSmartTefProvedor.BASE + "/pagamento/criar"), any(), any()))
                    .thenReturn(resposta(400, "{\"ok\":false,\"message\":\"Pagamento existente já pago\",\"result\":{\"status\":2}}"));

            assertThatThrownBy(() -> provedor.iniciar(cobranca("DEBITO", 1), CREDENCIAL))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("já existe");
        }

        @Test
        @DisplayName("token da API recusado: refaz o login uma vez e repete")
        void renovaToken() throws Exception {
            when(http.enviar(eq("POST"), eq(VeloceSmartTefProvedor.BASE + "/acessar"), any(), any()))
                    .thenReturn(resposta(200, "{\"ok\":true,\"result\":{\"token\":\"velho\"}}"))
                    .thenReturn(resposta(200, "{\"ok\":true,\"result\":{\"token\":\"novo\"}}"));
            when(http.enviar(eq("POST"), eq(VeloceSmartTefProvedor.BASE + "/pagamento/criar"), eq(Map.of("Token", "velho")), any()))
                    .thenReturn(resposta(401, "{\"ok\":false,\"message\":\"Token inválido\"}"));
            when(http.enviar(eq("POST"), eq(VeloceSmartTefProvedor.BASE + "/pagamento/criar"), eq(Map.of("Token", "novo")), any()))
                    .thenReturn(resposta(200, "{\"ok\":true,\"result\":{\"id_tbl_pagamento\":9}}"));

            assertThat(provedor.iniciar(cobranca("DEBITO", 1), CREDENCIAL).idExterno()).startsWith("9:");
            verify(http, times(2)).enviar(eq("POST"), eq(VeloceSmartTefProvedor.BASE + "/acessar"), any(), any());
        }

        @Test
        @DisplayName("usa as rotas em /api/v1 (o manual em PDF diz /v1, que responde 404)")
        void enderecoDaApi() {
            assertThat(VeloceSmartTefProvedor.BASE).isEqualTo("https://api.veloce.tech/api/v1");
        }

        @Test
        @DisplayName("resposta sem mensagem mostra o código HTTP em vez de sair em branco")
        void mensagemVazia() throws Exception {
            when(http.enviar(eq("POST"), eq(VeloceSmartTefProvedor.BASE + "/acessar"), any(), any()))
                    .thenReturn(resposta(404, "{\"message\":\"\"}"));

            assertThatThrownBy(() -> provedor.iniciar(cobranca("DEBITO", 1), CREDENCIAL))
                    .isInstanceOf(BusinessException.class).hasMessageEndingWith("HTTP 404");
        }

        @Test
        @DisplayName("login recusado e credencial incompleta dão mensagens claras")
        void credenciais() throws Exception {
            when(http.enviar(eq("POST"), eq(VeloceSmartTefProvedor.BASE + "/acessar"), any(), any()))
                    .thenReturn(resposta(400, "{\"ok\":false,\"message\":\"Senha incorreta\",\"result\":[]}"));

            assertThatThrownBy(() -> provedor.iniciar(cobranca("DEBITO", 1), CREDENCIAL))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Senha incorreta");
            assertThatThrownBy(() -> provedor.iniciar(cobranca("DEBITO", 1), "{\"login\":\"a\",\"senha\":\"b\",\"cnpj\":\"123\"}"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("CNPJ");
            assertThatThrownBy(() -> provedor.iniciar(cobranca("DEBITO", 1), "não é json"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("incompleta");
        }
    }

    @Nested
    @DisplayName("Identificação da cobrança")
    class Identificacao {

        @Test
        @DisplayName("o número não repete entre ambientes e guarda o horário para a consulta")
        void pagamentoId() {
            long t = 1_793_750_000_000L;
            long id = VeloceSmartTefProvedor.gerarPagamentoId(42, t);

            assertThat(id).isNotEqualTo(VeloceSmartTefProvedor.gerarPagamentoId(42, t + 1));
            VeloceSmartTefProvedor.IdVeloce lido = VeloceSmartTefProvedor.IdVeloce.de("4:" + id);
            assertThat(lido.idTabela()).isEqualTo(4);
            assertThat(lido.pagamentoId()).isEqualTo(id);
            assertThat(lido.criadoEm().toEpochMilli()).isEqualTo(t);
            assertThat(String.valueOf(id).length()).isLessThanOrEqualTo(20);
            assertThatThrownBy(() -> VeloceSmartTefProvedor.IdVeloce.de("abc")).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("a listagem usa a data no formato do manual")
        void urlListagem() {
            String url = VeloceSmartTefProvedor.urlListagem(VeloceSmartTefProvedor.BASE, CNPJ,
                    LocalDateTime.of(2026, 11, 3, 20, 5, 0), LocalDateTime.of(2026, 11, 3, 21, 0, 9));
            assertThat(url).isEqualTo(VeloceSmartTefProvedor.BASE
                    + "/pagamento/listar?cnpj=28803350000105&dt_incial=03%2F11%2F2026+20%3A05%3A00&dt_final=03%2F11%2F2026+21%3A00%3A09");
        }
    }

    @Nested
    @DisplayName("Resultado do pagamento")
    class Resultado {

        private JsonNode p(String s) throws Exception {
            return om.readTree(s);
        }

        @Test
        @DisplayName("pago → APROVADA com NSU, autorização e bandeira")
        void aprovado() throws Exception {
            RetornoProvedor r = VeloceSmartTefProvedor.interpretar("4:1", p(
                    "{\"status\":2,\"adquirente_code\":\"882342\",\"auth_code\":\"076442\",\"bandeira\":\"MASTERCARD\"}"));
            assertThat(r.status()).isEqualTo(StatusTransacaoTerminal.APROVADA);
            assertThat(r.nsu()).isEqualTo("882342");
            assertThat(r.autorizacao()).isEqualTo("076442");
            assertThat(r.bandeira()).isEqualTo("MASTERCARD");
        }

        @Test
        @DisplayName("aberto ou em andamento → PENDENTE; erro → NEGADA com o motivo")
        void estados() throws Exception {
            assertThat(VeloceSmartTefProvedor.interpretar("x", p("{\"status\":0}")).status()).isEqualTo(StatusTransacaoTerminal.PENDENTE);
            assertThat(VeloceSmartTefProvedor.interpretar("x", p("{\"status\":1}")).status()).isEqualTo(StatusTransacaoTerminal.PENDENTE);
            RetornoProvedor negado = VeloceSmartTefProvedor.interpretar("x", p("{\"status\":3,\"status_mensagem\":\"Saldo insuficiente\"}"));
            assertThat(negado.status()).isEqualTo(StatusTransacaoTerminal.NEGADA);
            assertThat(negado.mensagem()).contains("Saldo insuficiente");
        }

        @Test
        @DisplayName("cancelado ou rejeitado → CANCELADA, mesmo que o status diga pago")
        void cancelado() throws Exception {
            assertThat(VeloceSmartTefProvedor.interpretar("x", p("{\"status\":2,\"status_cancelamento\":2}")).status())
                    .isEqualTo(StatusTransacaoTerminal.CANCELADA);
            assertThat(VeloceSmartTefProvedor.interpretar("x", p("{\"status\":0,\"dt_exclusao\":\"2026-11-03 21:00:00\"}")).status())
                    .isEqualTo(StatusTransacaoTerminal.CANCELADA);
        }

        @Test
        @DisplayName("consulta acha a cobrança na lista pelo id da Veloce")
        void consultar() throws Exception {
            loginDevolve("tok");
            long pid = VeloceSmartTefProvedor.gerarPagamentoId(42, System.currentTimeMillis());
            when(http.enviar(eq("GET"), startsWith(VeloceSmartTefProvedor.BASE + "/pagamento/listar?cnpj=" + CNPJ), any(), isNull()))
                    .thenReturn(resposta(200, "{\"ok\":true,\"result\":["
                            + "{\"id_tbl_pagamento\":3,\"status\":2},"
                            + "{\"id_tbl_pagamento\":4,\"status\":2,\"adquirente_code\":\"111\",\"auth_code\":\"222\"}]}"));

            RetornoProvedor r = provedor.consultar("4:" + pid, "EOIVM", CREDENCIAL);

            assertThat(r.status()).isEqualTo(StatusTransacaoTerminal.APROVADA);
            assertThat(r.nsu()).isEqualTo("111");
        }

        @Test
        @DisplayName("lista vazia continua PENDENTE; outro erro da Veloce sobe")
        void consultarSemResultado() throws Exception {
            loginDevolve("tok");
            String id = "4:" + VeloceSmartTefProvedor.gerarPagamentoId(42, System.currentTimeMillis());
            when(http.enviar(eq("GET"), anyString(), any(), isNull()))
                    .thenReturn(resposta(404, "{\"ok\":false,\"message\":\"A pesquisa não retornou resultados\",\"result\":[]}"))
                    .thenReturn(resposta(404, "{\"ok\":false,\"message\":\"Empresa não existe ou usuário atual não pode acessar\",\"result\":[]}"));

            assertThat(provedor.consultar(id, "EOIVM", CREDENCIAL).status()).isEqualTo(StatusTransacaoTerminal.PENDENTE);
            assertThatThrownBy(() -> provedor.consultar(id, "EOIVM", CREDENCIAL))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Empresa não existe");
        }
    }

    @Nested
    @DisplayName("Cancelamento e teste de conexão")
    class CancelamentoETeste {

        @Test
        @DisplayName("cancelar rejeita a cobrança pendente na maquininha")
        void cancelar() throws Exception {
            loginDevolve("tok");
            when(http.enviar(eq("POST"), eq(VeloceSmartTefProvedor.BASE + "/pagamento/rejeitar"), any(), any()))
                    .thenReturn(resposta(200, "{\"ok\":true,\"message\":\"Sucesso ao rejeitar pagamento\"}"))
                    .thenReturn(resposta(422, "{\"ok\":false,\"message\":\"Pagamento já realizado ou rejeitado\"}"));

            provedor.cancelar("4:555", "EOIVM", CREDENCIAL);
            verify(http).enviar(eq("POST"), eq(VeloceSmartTefProvedor.BASE + "/pagamento/rejeitar"), any(),
                    eq(Map.of("pagamento_id", 555L, "indice", 0, "token", "EOIVM")));

            assertThatThrownBy(() -> provedor.cancelar("4:555", "EOIVM", CREDENCIAL))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Cancele direto na maquininha");
        }

        @Test
        @DisplayName("teste de conexão confere login e acesso ao CNPJ")
        void testarConexao() throws Exception {
            loginDevolve("tok");
            when(http.enviar(eq("GET"), anyString(), any(), isNull()))
                    .thenReturn(resposta(404, "{\"ok\":false,\"message\":\"A pesquisa não retornou resultados\",\"result\":[]}"))
                    .thenReturn(resposta(404, "{\"ok\":false,\"message\":\"Empresa não existe ou usuário atual não pode acessar\"}"));

            assertThat(provedor.testarConexao(CREDENCIAL).ok()).isTrue();
            ProvedorTerminalNuvem.TesteConexao recusado = provedor.testarConexao(CREDENCIAL);
            assertThat(recusado.ok()).isFalse();
            assertThat(recusado.mensagem()).contains("CNPJ");
        }
    }
}
