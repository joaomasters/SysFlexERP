package com.acougue.modules.fiscal.emissao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;

/** Adaptador da Focus NFe contra um servidor HTTP local que responde como a API v2 deles. */
@DisplayName("Provedor Focus NFe")
class ProvedorFocusNfeTest {

    private record Chamada(String metodo, String caminho, String auth, String corpo) {}
    private record Resposta(int status, String corpo) {}

    private HttpServer servidor;
    private final List<Chamada> chamadas = new ArrayList<>();
    private final Map<String, Resposta> respostas = new ConcurrentHashMap<>();
    private ProvedorFocusNfe focus;
    private final ObjectMapper json = new ObjectMapper();
    private final CredencialFiscal cred = new CredencialFiscal(null, null, null, null, "TOKEN-TESTE");

    @BeforeEach
    void subir() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String caminho = troca.getRequestURI().toString();
            chamadas.add(new Chamada(troca.getRequestMethod(), caminho, troca.getRequestHeaders().getFirst("Authorization"), corpo));
            Resposta r = respostas.entrySet().stream().filter(e -> caminho.startsWith(e.getKey()))
                    .map(Map.Entry::getValue).findFirst().orElse(new Resposta(404, "{\"codigo\":\"nao_encontrado\",\"mensagem\":\"Nota fiscal não encontrada\"}"));
            byte[] b = r.corpo().getBytes(StandardCharsets.UTF_8);
            troca.sendResponseHeaders(r.status(), b.length);
            try (OutputStream os = troca.getResponseBody()) {
                os.write(b);
            }
        });
        servidor.start();
        String base = "http://127.0.0.1:" + servidor.getAddress().getPort();
        focus = new ProvedorFocusNfe(base, base + "/prod", json);
    }

    @AfterEach
    void descer() {
        servidor.stop(0);
    }

    private static NfceEmissao nota(String pisCofins) {
        return new NfceEmissao("HOMOLOGACAO", "PR",
                new NfceEmissao.Emitente("28803350000105", "1234567850", "Loja", null, (short) 1, null, null, null, null,
                        "4122404", null, null, null, pisCofins),
                (short) 1, 41, LocalDateTime.of(2026, 11, 19, 15, 0), "52998224725",
                List.of(new NfceEmissao.Item("PIC", null, "Picanha", "02013000", null, "5102", "0", "102", "KG",
                        new BigDecimal("1.250"), new BigDecimal("69.90"), new BigDecimal("87.38"), new BigDecimal("2.38"))),
                List.of(new NfceEmissao.Pagamento("04", new BigDecimal("50.00")), new NfceEmissao.Pagamento("01", new BigDecimal("40.00"))),
                new BigDecimal("5.00"), new BigDecimal("85.00"), 7L, "41261128803350000105650010000000411000000011", "sysflex-nfce-99");
    }

    @Test
    @DisplayName("autorizada: manda o JSON da NFC-e com token e ref, e guarda chave, protocolo, QR Code e XML")
    void autorizada() throws Exception {
        respostas.put("/v2/nfce?ref=", new Resposta(201, """
                {"status":"autorizado","status_sefaz":"100","mensagem_sefaz":"Autorizado o uso da NF-e",
                 "chave_nfe":"NFe41261128803350000105650010000000419876543210","protocolo":"141260000012345",
                 "qrcode_url":"http://www.fazenda.pr.gov.br/nfce/qrcode?p=x","url_consulta_nf":"http://www.fazenda.pr.gov.br/nfce/consulta",
                 "caminho_xml_nota_fiscal":"/arquivos/nota.xml","ref":"sysflex-nfce-99","data_emissao":"2026-11-19T15:00:00-03:00"}"""));
        respostas.put("/arquivos/nota.xml", new Resposta(200, "<nfeProc/>"));

        RetornoEmissao r = focus.emitirNfce(nota("49"), cred);

        assertThat(r.status()).isEqualTo("AUTORIZADA");
        assertThat(r.chave()).isEqualTo("41261128803350000105650010000000419876543210");
        assertThat(r.protocolo()).isEqualTo("141260000012345");
        assertThat(r.urlQrcode()).contains("qrcode");
        assertThat(r.xml()).isEqualTo("<nfeProc/>");
        assertThat(r.autorizadaEm()).isEqualTo(LocalDateTime.of(2026, 11, 19, 15, 0));

        Chamada post = chamadas.get(0);
        assertThat(post.metodo()).isEqualTo("POST");
        assertThat(post.caminho()).isEqualTo("/v2/nfce?ref=sysflex-nfce-99");
        assertThat(post.auth()).isEqualTo("Basic " + Base64.getEncoder().encodeToString("TOKEN-TESTE:".getBytes()));
        JsonNode j = json.readTree(post.corpo());
        assertThat(j.get("cnpj_emitente").asText()).isEqualTo("28803350000105");
        assertThat(j.get("data_emissao").asText()).isEqualTo("2026-11-19T15:00:00-03:00");
        assertThat(j.get("numero").asInt()).isEqualTo(41);
        assertThat(j.get("cpf_destinatario").asText()).isEqualTo("52998224725");
        assertThat(j.get("valor_troco").asText()).isEqualTo("5.00");
        JsonNode item = j.get("items").get(0);
        assertThat(item.get("codigo_ncm").asText()).isEqualTo("02013000");
        assertThat(item.get("descricao").asText()).isEqualTo(ProvedorFocusNfe.TEXTO_HOMOLOGACAO);
        assertThat(item.get("icms_situacao_tributaria").asText()).isEqualTo("102");
        assertThat(item.get("pis_situacao_tributaria").asText()).isEqualTo("49");
        assertThat(item.get("quantidade_comercial").asText()).isEqualTo("1.2500");
        assertThat(item.get("valor_bruto").asText()).isEqualTo("87.38");
        assertThat(item.get("valor_desconto").asText()).isEqualTo("2.38");
        assertThat(item.get("codigo_barras_comercial").asText()).isEqualTo("SEM GTIN");
        JsonNode pags = j.get("formas_pagamento");
        assertThat(pags.get(0).get("tipo_integracao").asText()).isEqualTo("2");   // débito sem maquininha integrada
        assertThat(pags.get(1).has("tipo_integracao")).isFalse();                  // dinheiro
    }

    @Test
    @DisplayName("SEFAZ recusou: REJEITADA com o motivo; denegada: DENEGADA")
    void recusas() {
        respostas.put("/v2/nfce?ref=", new Resposta(200,
                "{\"status\":\"erro_autorizacao\",\"status_sefaz\":\"778\",\"mensagem_sefaz\":\"NCM inexistente\"}"));
        RetornoEmissao r = focus.emitirNfce(nota("49"), cred);
        assertThat(r.status()).isEqualTo("REJEITADA");
        assertThat(r.mensagem()).isEqualTo("778 - NCM inexistente");

        respostas.put("/v2/nfce?ref=", new Resposta(200, "{\"status\":\"denegado\",\"status_sefaz\":\"302\",\"mensagem_sefaz\":\"Irregular\"}"));
        assertThat(focus.emitirNfce(nota("49"), cred).status()).isEqualTo("DENEGADA");
    }

    @Test
    @DisplayName("Focus recusou a requisição (4xx): não foi transmitida → falha antes do envio")
    void requisicaoInvalida() {
        respostas.put("/v2/nfce?ref=", new Resposta(422,
                "{\"codigo\":\"requisicao_invalida\",\"mensagem\":\"Parâmetros inválidos\",\"erros\":[{\"mensagem\":\"cfop inválido\"}]}"));
        assertThatThrownBy(() -> focus.emitirNfce(nota("49"), cred))
                .isInstanceOf(FalhaAntesDoEnvioException.class).hasMessageContaining("cfop inválido");
    }

    @Test
    @DisplayName("erro 5xx ou ainda processando: resultado desconhecido (nunca ERRO)")
    void desconhecido() {
        respostas.put("/v2/nfce?ref=", new Resposta(502, "Bad Gateway"));
        assertThatThrownBy(() -> focus.emitirNfce(nota("49"), cred))
                .isInstanceOf(IllegalStateException.class).isNotInstanceOf(FalhaAntesDoEnvioException.class);

        respostas.put("/v2/nfce?ref=", new Resposta(202, "{\"status\":\"processando_autorizacao\"}"));
        assertThat(focus.emitirNfce(nota("49"), cred).status()).isEqualTo("DESCONHECIDO");
    }

    @Test
    @DisplayName("sem CST de PIS/COFINS ou sem token: recusa antes de chamar a Focus")
    void semDados() {
        assertThatThrownBy(() -> focus.emitirNfce(nota(null), cred))
                .isInstanceOf(FalhaAntesDoEnvioException.class).hasMessageContaining("PIS/COFINS");
        assertThatThrownBy(() -> focus.emitirNfce(nota("49"), new CredencialFiscal(null, null, null, null, null)))
                .isInstanceOf(FalhaAntesDoEnvioException.class).hasMessageContaining("token");
        assertThat(chamadas).isEmpty();
    }

    @Test
    @DisplayName("consulta pela ref: autorizada, ou não encontrada quando a Focus nunca recebeu")
    void consulta() {
        respostas.put("/v2/nfce/sysflex-nfce-99", new Resposta(200,
                "{\"status\":\"autorizado\",\"chave_nfe\":\"NFe41261128803350000105650010000000419876543210\",\"protocolo\":\"1412\"}"));
        assertThat(focus.consultarNfce("sysflex-nfce-99", null, "HOMOLOGACAO", cred).orElseThrow().status()).isEqualTo("AUTORIZADA");
        assertThat(chamadas.get(0).caminho()).isEqualTo("/v2/nfce/sysflex-nfce-99?completa=0");

        respostas.clear();
        assertThat(focus.consultarNfce("sysflex-nfce-100", null, "HOMOLOGACAO", cred).orElseThrow().status())
                .isEqualTo(RetornoEmissao.NAO_ENCONTRADA);
    }

    @Test
    @DisplayName("inutilização homologada devolve o protocolo; produção usa a URL de produção")
    void inutilizacao() throws Exception {
        respostas.put("/prod/v2/nfce/inutilizacao", new Resposta(200,
                "{\"status\":\"autorizado\",\"status_sefaz\":\"102\",\"mensagem_sefaz\":\"Inutilizacao homologada\",\"protocolo_sefaz\":\"141260000077\"}"));
        PedidoInutilizacao p = new PedidoInutilizacao("PRODUCAO", "PR", "28803350000105", (short) 65, (short) 1, 4, 6, 26,
                "Falha na transmissao do PDV");

        PedidoInutilizacao.Retorno r = focus.inutilizar(p, cred).orElseThrow();

        assertThat(r.status()).isEqualTo("HOMOLOGADA");
        assertThat(r.protocolo()).isEqualTo("141260000077");
        JsonNode corpo = json.readTree(chamadas.get(0).corpo());
        assertThat(corpo.get("numero_inicial").asText()).isEqualTo("4");
        assertThat(corpo.get("cnpj").asText()).isEqualTo("28803350000105");
    }
}
