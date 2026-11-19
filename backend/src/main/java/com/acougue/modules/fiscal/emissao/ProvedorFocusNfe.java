package com.acougue.modules.fiscal.emissao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * Focus NFe (API REST v2). A Focus guarda o certificado e o CSC de cada empresa e assina/transmite;
 * o sistema manda os dados da venda com um token por empresa e ambiente.
 *
 * Contrato de falhas (ProvedorFiscal): resposta 4xx = a Focus recusou a requisição antes da SEFAZ
 * → FalhaAntesDoEnvioException (ERRO). 5xx, timeout ou "processando" = resultado desconhecido;
 * a consulta usa a mesma ref da emissão (idempotência da Focus).
 */
@Component
public class ProvedorFocusNfe implements ProvedorFiscal {

    private static final Logger log = LoggerFactory.getLogger(ProvedorFocusNfe.class);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    /** Formas que levam o grupo de cartão/PIX; sem integração com a maquininha = tipo 2. */
    private static final Set<String> FORMAS_COM_GRUPO_CARTAO = Set.of("03", "04", "17");

    static final String TEXTO_HOMOLOGACAO = "NOTA FISCAL EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL";

    private final String urlHomologacao;
    private final String urlProducao;
    private final ObjectMapper json;
    private final HttpClient http;

    public ProvedorFocusNfe(@Value("${fiscal.focus.url-homologacao:https://homologacao.focusnfe.com.br}") String urlHomologacao,
                            @Value("${fiscal.focus.url-producao:https://api.focusnfe.com.br}") String urlProducao,
                            ObjectMapper json) {
        this.urlHomologacao = urlHomologacao;
        this.urlProducao = urlProducao;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public String codigo() {
        return "FOCUS_NFE";
    }

    @Override
    public String rotulo() {
        return "Focus NFe";
    }

    @Override
    public boolean usaCertificadoLocal() {
        return false;
    }

    @Override
    public boolean exigeToken() {
        return true;
    }

    // ── emissão ───────────────────────────────────────────────

    @Override
    public RetornoEmissao emitirNfce(NfceEmissao nota, CredencialFiscal credencial) {
        String corpo;
        try {
            corpo = json.writeValueAsString(montar(nota));
        } catch (IOException e) {
            throw new FalhaAntesDoEnvioException("Não foi possível montar a nota: " + e.getMessage(), e);
        }
        Resposta r = enviar("POST", base(nota.ambiente()) + "/v2/nfce?ref=" + url(nota.ref()), corpo, credencial, Duration.ofSeconds(60));
        if (r.status() >= 500) throw new IllegalStateException("Focus NFe respondeu " + r.status());
        if (r.status() >= 400) throw new FalhaAntesDoEnvioException(mensagemErro(r));
        return retorno(r.corpo(), nota.ambiente(), credencial);
    }

    @Override
    public Optional<RetornoEmissao> consultarNfce(String ref, String chave, String ambiente, CredencialFiscal credencial) {
        Resposta r = enviar("GET", base(ambiente) + "/v2/nfce/" + url(ref) + "?completa=0", null, credencial, Duration.ofSeconds(30));
        if (r.status() == 404) {
            return Optional.of(RetornoEmissao.naoEncontrada("A Focus NFe não recebeu esta nota (" + mensagemErro(r) + ")."));
        }
        if (r.status() >= 400) throw new IllegalStateException("Consulta na Focus NFe falhou: " + mensagemErro(r));
        return Optional.of(retorno(r.corpo(), ambiente, credencial));
    }

    @Override
    public Optional<PedidoInutilizacao.Retorno> inutilizar(PedidoInutilizacao p, CredencialFiscal credencial) {
        ObjectNode o = json.createObjectNode();
        o.put("cnpj", p.cnpj());
        o.put("serie", String.valueOf(p.serie()));
        o.put("numero_inicial", String.valueOf(p.numeroInicial()));
        o.put("numero_final", String.valueOf(p.numeroFinal()));
        o.put("justificativa", p.justificativa());
        String caminho = p.modelo() == 55 ? "/v2/nfe/inutilizacao" : "/v2/nfce/inutilizacao";
        Resposta r = enviar("POST", base(p.ambiente()) + caminho, o.toString(), credencial, Duration.ofSeconds(60));
        if (r.status() >= 500) throw new IllegalStateException("Focus NFe respondeu " + r.status());
        if (r.status() >= 400) return Optional.of(new PedidoInutilizacao.Retorno("REJEITADA", null, mensagemErro(r)));
        JsonNode n = ler(r.corpo());
        String status = texto(n, "status");
        String mensagem = juntar(texto(n, "status_sefaz"), texto(n, "mensagem_sefaz"));
        if ("autorizado".equals(status)) {
            String protocolo = texto(n, "protocolo_sefaz");
            if (protocolo == null) protocolo = texto(n, "protocolo");
            return Optional.of(new PedidoInutilizacao.Retorno("HOMOLOGADA", protocolo, mensagem));
        }
        if ("erro_autorizacao".equals(status)) return Optional.of(new PedidoInutilizacao.Retorno("REJEITADA", null, mensagem));
        throw new IllegalStateException("Inutilização sem resposta definitiva da Focus NFe (status " + status + ").");
    }

    // ── JSON da NFC-e ──────────────────────────────────────────

    ObjectNode montar(NfceEmissao n) {
        if (n.emitente().pisCofinsCst() == null) {
            throw new FalhaAntesDoEnvioException("Falta o CST de PIS/COFINS na configuração fiscal.");
        }
        ObjectNode o = json.createObjectNode();
        o.put("cnpj_emitente", n.emitente().cnpj());
        o.put("data_emissao", n.dataEmissao().atZone(FUSO).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        o.put("numero", n.numero());
        o.put("serie", n.serie());
        o.put("natureza_operacao", "VENDA AO CONSUMIDOR");
        o.put("presenca_comprador", "1");   // operação presencial
        o.put("modalidade_frete", "9");     // sem frete
        o.put("local_destino", "1");        // operação interna
        o.put("consumidor_final", "1");
        if (n.destinatarioDocumento() != null) {
            o.put(n.destinatarioDocumento().length() == 11 ? "cpf_destinatario" : "cnpj_destinatario", n.destinatarioDocumento());
        }
        ArrayNode itens = o.putArray("items");
        int i = 1;
        for (NfceEmissao.Item it : n.itens()) {
            ObjectNode x = itens.addObject();
            int numeroItem = i++;
            x.put("numero_item", String.valueOf(numeroItem));
            x.put("codigo_produto", it.codigo());
            // regra da SEFAZ: em homologação o 1º item leva este texto no lugar da descrição
            x.put("descricao", numeroItem == 1 && !"PRODUCAO".equals(n.ambiente()) ? TEXTO_HOMOLOGACAO : it.descricao());
            x.put("codigo_ncm", it.ncm());
            if (it.cest() != null) x.put("cest", it.cest());
            x.put("cfop", it.cfop());
            x.put("unidade_comercial", it.unidade());
            x.put("unidade_tributavel", it.unidade());
            x.put("quantidade_comercial", dec(it.quantidade(), 4));
            x.put("quantidade_tributavel", dec(it.quantidade(), 4));
            x.put("valor_unitario_comercial", dec(it.valorUnitario(), 10));
            x.put("valor_unitario_tributavel", dec(it.valorUnitario(), 10));
            x.put("valor_bruto", dec(it.valorBruto(), 2));
            if (it.desconto() != null && it.desconto().signum() > 0) x.put("valor_desconto", dec(it.desconto(), 2));
            x.put("codigo_barras_comercial", it.ean() == null ? "SEM GTIN" : it.ean());
            x.put("codigo_barras_tributavel", it.ean() == null ? "SEM GTIN" : it.ean());
            x.put("icms_origem", it.origem() == null ? "0" : it.origem());
            x.put("icms_situacao_tributaria", it.csosn());
            x.put("pis_situacao_tributaria", n.emitente().pisCofinsCst());
            x.put("cofins_situacao_tributaria", n.emitente().pisCofinsCst());
        }
        ArrayNode pags = o.putArray("formas_pagamento");
        for (NfceEmissao.Pagamento p : n.pagamentos()) {
            ObjectNode x = pags.addObject();
            x.put("forma_pagamento", p.formaPagamento());
            x.put("valor_pagamento", dec(p.valor(), 2));
            if (FORMAS_COM_GRUPO_CARTAO.contains(p.formaPagamento())) x.put("tipo_integracao", "2");
        }
        if (n.troco() != null && n.troco().signum() > 0) o.put("valor_troco", dec(n.troco(), 2));
        return o;
    }

    private RetornoEmissao retorno(String corpo, String ambiente, CredencialFiscal credencial) {
        JsonNode n = ler(corpo);
        String status = texto(n, "status");
        String mensagem = juntar(texto(n, "status_sefaz"), texto(n, "mensagem_sefaz"));
        String chave = texto(n, "chave_nfe");
        if (chave != null && chave.startsWith("NFe")) chave = chave.substring(3);
        return switch (status == null ? "" : status) {
            case "autorizado" -> new RetornoEmissao("AUTORIZADA", chave, texto(n, "protocolo"), mensagem,
                    texto(n, "qrcode_url"), texto(n, "url_consulta_nf"),
                    baixarXml(ambiente, texto(n, "caminho_xml_nota_fiscal"), credencial),
                    texto(n, "ref"), dataHora(texto(n, "data_emissao")));
            case "cancelado" -> new RetornoEmissao("CANCELADA", chave, texto(n, "protocolo"), mensagem,
                    texto(n, "qrcode_url"), texto(n, "url_consulta_nf"), null, texto(n, "ref"), null);
            case "erro_autorizacao" -> new RetornoEmissao("REJEITADA", chave, null, mensagem, null, null, null, texto(n, "ref"), null);
            case "denegado" -> new RetornoEmissao("DENEGADA", chave, null, mensagem, null, null, null, texto(n, "ref"), null);
            // processando ou qualquer status novo: não dá para afirmar nada
            default -> RetornoEmissao.desconhecido(chave, "Focus NFe ainda processando (status " + status + "): consulte a situação.");
        };
    }

    /** O XML autorizado precisa ser guardado (5 anos). Falha aqui não muda o resultado da nota. */
    private String baixarXml(String ambiente, String caminho, CredencialFiscal credencial) {
        if (caminho == null) return null;
        try {
            Resposta r = enviar("GET", base(ambiente) + caminho, null, credencial, Duration.ofSeconds(20));
            return r.status() == 200 ? r.corpo() : null;
        } catch (RuntimeException e) {
            log.warn("Não foi possível baixar o XML da NFC-e da Focus: {}", e.getMessage());
            return null;
        }
    }

    // ── HTTP ───────────────────────────────────────────────────

    record Resposta(int status, String corpo) {}

    private Resposta enviar(String metodo, String url, String corpo, CredencialFiscal credencial, Duration timeout) {
        if (credencial == null || credencial.tokenApi() == null) {
            throw new FalhaAntesDoEnvioException("Falta o token da Focus NFe na configuração fiscal.");
        }
        String auth = Base64.getEncoder().encodeToString((credencial.tokenApi() + ":").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Authorization", "Basic " + auth).header("Accept", "application/json");
        if (corpo == null) {
            b.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/json").method(metodo, HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8));
        }
        try {
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Resposta(r.statusCode(), r.body());
        } catch (IOException e) {
            throw new IllegalStateException("Sem resposta da Focus NFe: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Comunicação com a Focus NFe interrompida.", e);
        }
    }

    private String base(String ambiente) {
        return "PRODUCAO".equals(ambiente) ? urlProducao : urlHomologacao;
    }

    private String mensagemErro(Resposta r) {
        try {
            JsonNode n = json.readTree(r.corpo());
            String m = texto(n, "mensagem");
            JsonNode erros = n.get("erros");
            if (erros != null && erros.isArray()) {
                StringBuilder sb = new StringBuilder(m == null ? "" : m);
                for (JsonNode e : erros) sb.append(sb.isEmpty() ? "" : " | ").append(texto(e, "mensagem"));
                m = sb.toString();
            }
            return "Focus NFe (" + r.status() + "): " + (m == null || m.isBlank() ? texto(n, "codigo") : m);
        } catch (IOException | RuntimeException e) {
            return "Focus NFe respondeu " + r.status();
        }
    }

    private JsonNode ler(String corpo) {
        try {
            return json.readTree(corpo == null ? "{}" : corpo);
        } catch (IOException e) {
            throw new IllegalStateException("Resposta inválida da Focus NFe.", e);
        }
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n == null ? null : n.get(campo);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String juntar(String codigo, String mensagem) {
        if (codigo == null) return mensagem;
        return mensagem == null ? codigo : codigo + " - " + mensagem;
    }

    private static String dec(BigDecimal v, int casas) {
        return (v == null ? BigDecimal.ZERO : v).setScale(casas, RoundingMode.HALF_UP).toPlainString();
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static LocalDateTime dataHora(String s) {
        if (s == null) return null;
        try {
            return OffsetDateTime.parse(s).atZoneSameInstant(FUSO).toLocalDateTime();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
