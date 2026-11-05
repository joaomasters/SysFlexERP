package com.acougue.modules.pagamento.provedor;

import com.acougue.entity.ProvedorPagamento;
import com.acougue.entity.StatusTransacaoTerminal;
import com.acougue.exception.BusinessException;
import com.acougue.modules.pagamento.ClienteHttpPagamento;
import com.acougue.modules.pagamento.ClienteHttpPagamento.Resposta;
import com.acougue.security.CacheDaEmpresa;
import com.acougue.security.ContextoUsuario;
import com.acougue.tenant.Tenant;
import com.acougue.tenant.TenantContext;
import com.acougue.tenant.TenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Veloce Smart TEF: o app "Smart TEF" (Cloudpar/Veloce) instalado na maquininha
 * fica em "Aguardando pagamento" e recebe a cobrança pela API da Veloce. Funciona
 * com a Máquina Smart Sicredi (Ingenico DX8000), que paga pelo M-SiTef.
 *
 * Configuração (painel.veloce.tech):
 *  - Configurações → Dispositivos: cadastra a maquininha, adquirente "Sitef", e
 *    gera o token curto (ex.: EOIVM) digitado no app. Esse token é o
 *    identificadorDispositivo do terminal no SysFlex.
 *  - Configurações → Usuários: um administrador só para a integração.
 *
 * Credencial (cifrada no banco): JSON {"login","senha","cnpj"} desse usuário e
 * da loja. O token da API vem de /v1/acessar e fica em memória; se a Veloce
 * recusar (401/403), o login é refeito uma vez.
 *
 * A API não consulta um pagamento pelo id, só lista por período: consultar()
 * lista a janela da cobrança e procura o id_tbl_pagamento. Documentação:
 * manual "API SmartTEF v1027" (cac.veloce.tech).
 */
@Component
public class VeloceSmartTefProvedor implements ProvedorTerminalNuvem, CacheDaEmpresa {

    // O manual em PDF (v1027) diz /v1, mas as rotas estão em /api/v1: em /v1 tudo responde 404
    static final String BASE = "https://api.veloce.tech/api/v1";
    private final String base;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_API = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final long MARGEM_CONSULTA_MIN = 10;

    private final ClienteHttpPagamento http;
    private final ObjectMapper json;
    private final TenantRepository tenantRepository;
    private final String baseUrlRetorno;
    private final Map<String, String> tokens = new ConcurrentHashMap<>(); // login -> token da API
    private final Map<String, String> empresaDoLogin = new ConcurrentHashMap<>(); // login -> schema, para o "Limpar cache"

    public VeloceSmartTefProvedor(ClienteHttpPagamento http, ObjectMapper json, TenantRepository tenantRepository,
                                  @Value("${pagamento.webhook.base-url:}") String baseUrlRetorno,
                                  @Value("${pagamento.veloce.url:" + BASE + "}") String base) {
        this.http = http;
        this.json = json;
        this.tenantRepository = tenantRepository;
        this.baseUrlRetorno = baseUrlRetorno.replaceAll("/+$", "");
        this.base = base.replaceAll("/+$", "");
    }

    @Override
    public ProvedorPagamento provedor() {
        return ProvedorPagamento.VELOCE;
    }

    record Credencial(String login, String senha, String cnpj) {}

    Credencial lerCredencial(String credencial) {
        try {
            JsonNode n = json.readTree(credencial);
            String cnpj = n.path("cnpj").asText("").replaceAll("\\D", "");
            Credencial c = new Credencial(n.path("login").asText("").trim(), n.path("senha").asText(""), cnpj);
            if (c.login().isEmpty() || c.senha().isEmpty() || cnpj.length() != 14) throw new IllegalArgumentException();
            return c;
        } catch (Exception e) {
            throw new BusinessException("Credencial da Veloce incompleta: informe e-mail, senha e CNPJ em Maquininhas → Credenciais.");
        }
    }

    /**
     * Id da cobrança na Veloce (pagamento_id). Leva o horário em milissegundos para não repetir
     * entre ambientes que usem o mesmo CNPJ (desenvolvimento e produção numeram transações do 1).
     */
    static long gerarPagamentoId(long transacaoId, long epochMillis) {
        return epochMillis * 1000 + (transacaoId % 1000);
    }

    /** idExterno = "<id_tbl_pagamento>:<pagamento_id>": um para achar na lista, o outro para cancelar. */
    record IdVeloce(long idTabela, long pagamentoId) {
        static IdVeloce de(String idExterno) {
            String[] partes = idExterno == null ? new String[0] : idExterno.split(":");
            if (partes.length != 2) throw new BusinessException("Identificador de cobrança Veloce inválido: " + idExterno);
            return new IdVeloce(Long.parseLong(partes[0]), Long.parseLong(partes[1]));
        }

        String texto() {
            return idTabela + ":" + pagamentoId;
        }

        Instant criadoEm() {
            return Instant.ofEpochMilli(pagamentoId / 1000);
        }
    }

    @Override
    public RetornoProvedor iniciar(CobrancaTerminal c, String credencial) {
        Credencial cred = lerCredencial(credencial);
        long pagamentoId = gerarPagamentoId(c.transacaoId(), System.currentTimeMillis());
        Resposta r = autenticado(cred, "POST", base + "/pagamento/criar", montarPagamento(c, pagamentoId, cred.cnpj(), urlRetorno(),
                nomeEmpresaAtual()));
        JsonNode corpo = r.json();
        if (!r.sucesso() || corpo == null || !corpo.path("ok").asBoolean(false)) {
            throw new BusinessException(mensagemErro(r, "enviar a cobrança para a maquininha"));
        }
        IdVeloce id = new IdVeloce(corpo.path("result").path("id_tbl_pagamento").asLong(), pagamentoId);
        return RetornoProvedor.pendente(id.texto(), r.corpo());
    }

    /** 0 débito, 1 crédito, 3 PIX (QR na tela da maquininha); 2 voucher não é usado. */
    static int tipoVeloce(String tipo) {
        return switch (tipo) {
            case "CREDITO" -> 1;
            case "PIX" -> 3;
            default -> 0;
        };
    }

    static Map<String, Object> montarPagamento(CobrancaTerminal c, long pagamentoId, String cnpj, String urlRetorno) {
        return montarPagamento(c, pagamentoId, cnpj, urlRetorno, null);
    }

    static Map<String, Object> montarPagamento(CobrancaTerminal c, long pagamentoId, String cnpj, String urlRetorno,
                                               String nomeLoja) {
        boolean credito = "CREDITO".equals(c.tipo());
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("valor", c.valor());
        corpo.put("indice", 0);
        corpo.put("token", c.identificadorDispositivo());
        corpo.put("pagamento_id", pagamentoId);
        corpo.put("url_retorno", urlRetorno);
        corpo.put("cnpj", cnpj);
        corpo.put("tipo", tipoVeloce(c.tipo()));
        corpo.put("parcelas", String.valueOf(credito ? c.parcelas() : 1));
        if (c.vendaId() != null) corpo.put("pedido", c.vendaId());
        if ("PIX".equals(c.tipo())) {
            // PIX exige identificador e nome (fora do manual: a API respondeu "Campo obrigatório
            // identificador / nome"). Sem cliente com CPF/CNPJ na venda, vai a própria loja.
            String documento = c.pagadorDocumento() != null ? c.pagadorDocumento() : cnpj;
            String nome = c.pagadorDocumento() != null ? c.pagadorNome()
                    : (nomeLoja != null && !nomeLoja.isBlank() ? nomeLoja : "Consumidor");
            corpo.put("identificador", documento);
            corpo.put("nome", nome);
            // Os nomes do manual (modo fila), caso a validação use estes
            corpo.put("identificador_cliente", documento);
            corpo.put("nome_cliente", nome);
        }
        return corpo;
    }

    @Override
    public RetornoProvedor consultar(String idExterno, String dispositivo, String credencial) {
        Credencial cred = lerCredencial(credencial);
        IdVeloce id = IdVeloce.de(idExterno);
        LocalDateTime inicio = LocalDateTime.ofInstant(id.criadoEm(), FUSO).minusMinutes(MARGEM_CONSULTA_MIN);
        LocalDateTime fim = LocalDateTime.now(FUSO).plusMinutes(MARGEM_CONSULTA_MIN);
        Resposta r = autenticado(cred, "GET", urlListagem(base, cred.cnpj(), inicio, fim), null);
        JsonNode corpo = r.json();
        if (corpo == null) {
            throw new BusinessException("Veloce: resposta inválida ao consultar (HTTP " + r.status() + ").");
        }
        // "A pesquisa não retornou resultados" vem como 404: a cobrança ainda não apareceu na lista
        for (JsonNode p : corpo.path("result")) {
            if (p.path("id_tbl_pagamento").asLong() == id.idTabela()) {
                return interpretar(idExterno, p);
            }
        }
        if (!r.sucesso() && !corpo.path("message").asText("").contains("não retornou resultados")) {
            throw new BusinessException(mensagemErro(r, "consultar a cobrança"));
        }
        return RetornoProvedor.pendente(idExterno, r.corpo());
    }

    static String urlListagem(String base, String cnpj, LocalDateTime inicio, LocalDateTime fim) {
        // "dt_incial" é a grafia do manual da Veloce
        return base + "/pagamento/listar?cnpj=" + cnpj
                + "&dt_incial=" + URLEncoder.encode(inicio.format(DATA_API), StandardCharsets.UTF_8)
                + "&dt_final=" + URLEncoder.encode(fim.format(DATA_API), StandardCharsets.UTF_8);
    }

    /**
     * status: 0 aberto, 1 em andamento, 2 pago, 3 erro.
     * status_cancelamento: 2 = cancelamento efetuado. dt_exclusao preenchida = rejeitada/excluída.
     */
    static RetornoProvedor interpretar(String idExterno, JsonNode p) {
        String bruto = p.toString();
        int status = p.path("status").asInt(0);
        String nsu = texto(p.path("adquirente_code"));
        String autorizacao = texto(p.path("auth_code"));
        String bandeira = texto(p.path("bandeira"));

        if (p.path("status_cancelamento").asInt(-1) == 2) {
            return new RetornoProvedor(StatusTransacaoTerminal.CANCELADA, idExterno, nsu, null, bandeira,
                    "Cancelado na maquininha", bruto);
        }
        if (status == 2) {
            return new RetornoProvedor(StatusTransacaoTerminal.APROVADA, idExterno, nsu, autorizacao, bandeira,
                    "Aprovado", bruto);
        }
        if (status == 3) {
            String motivo = texto(p.path("status_mensagem"));
            return new RetornoProvedor(StatusTransacaoTerminal.NEGADA, idExterno, nsu, null, bandeira,
                    "Negado: " + (motivo != null ? motivo : "recusado na maquininha"), bruto);
        }
        if (texto(p.path("dt_exclusao")) != null) {
            return new RetornoProvedor(StatusTransacaoTerminal.CANCELADA, idExterno, null, null, null,
                    "Cobrança rejeitada", bruto);
        }
        return RetornoProvedor.pendente(idExterno, bruto);
    }

    @Override
    public void cancelar(String idExterno, String dispositivo, String credencial) {
        Credencial cred = lerCredencial(credencial);
        IdVeloce id = IdVeloce.de(idExterno);
        Map<String, Object> corpo = Map.of("pagamento_id", id.pagamentoId(), "indice", 0, "token", dispositivo);
        Resposta r = autenticado(cred, "POST", base + "/pagamento/rejeitar", corpo);
        if (!r.sucesso() || r.json() == null || !r.json().path("ok").asBoolean(false)) {
            throw new BusinessException("A Veloce não permitiu cancelar a cobrança ("
                    + mensagemErro(r, "cancelar") + "). Cancele direto na maquininha.");
        }
    }

    @Override
    public TesteConexao testarConexao(String credencial) {
        Credencial cred;
        try {
            cred = lerCredencial(credencial);
            tokens.remove(cred.login());
            LocalDateTime agora = LocalDateTime.now(FUSO);
            Resposta r = autenticado(cred, "GET", urlListagem(base, cred.cnpj(), agora.minusDays(1), agora), null);
            String mensagem = r.json() != null ? r.json().path("message").asText("") : "";
            if (r.sucesso() || mensagem.contains("não retornou resultados")) {
                return new TesteConexao(true, "Login aceito e CNPJ " + cred.cnpj() + " liberado para este usuário.");
            }
            return new TesteConexao(false, "Login aceito, mas a Veloce recusou o CNPJ: " + mensagem);
        } catch (BusinessException e) {
            return new TesteConexao(false, e.getMessage());
        }
    }

    /** "Limpar cache": a próxima cobrança da empresa faz login de novo na Veloce. */
    @Override
    public void limparEmpresa(String schema, String codigo) {
        empresaDoLogin.entrySet().removeIf(e -> {
            if (!schema.equals(e.getValue())) return false;
            tokens.remove(e.getKey());
            return true;
        });
    }

    // ── Autenticação ───────────────────────────────────────────

    private Resposta autenticado(Credencial cred, String metodo, String url, Object corpo) {
        Resposta r = http.enviar(metodo, url, Map.of("Token", token(cred)), corpo);
        if (r.status() == 401 || r.status() == 403) {
            tokens.remove(cred.login());
            r = http.enviar(metodo, url, Map.of("Token", token(cred)), corpo);
        }
        return r;
    }

    private String token(Credencial cred) {
        String atual = tokens.get(cred.login());
        if (atual != null) return atual;
        Resposta r = http.enviar("POST", base + "/acessar", Map.of(),
                Map.of("login", cred.login(), "senha", cred.senha()));
        String token = r.json() != null ? texto(r.json().path("result").path("token")) : null;
        if (!r.sucesso() || token == null) {
            throw new BusinessException("Veloce recusou o login de integração: " + mensagemOuStatus(r));
        }
        tokens.put(cred.login(), token);
        empresaDoLogin.put(cred.login(), TenantContext.get());
        return token;
    }

    // ── URL de retorno (webhook) ───────────────────────────────

    /**
     * Para onde a Veloce avisa que o pagamento mudou. PAGAMENTO_WEBHOOK_BASE_URL fixa o endereço;
     * sem ela, usa o endereço público pelo qual o PDV chamou (no Vercel, o rewrite /api leva ao backend).
     */
    String urlRetorno() {
        String raiz = baseUrlRetorno;
        String contexto = "/api";
        if (raiz.isEmpty() && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            String proto = primeiro(req.getHeader("X-Forwarded-Proto"), req.getScheme());
            String host = primeiro(req.getHeader("X-Forwarded-Host"), req.getHeader("Host"));
            raiz = proto + "://" + host;
            contexto = req.getContextPath();
        }
        String url = raiz + contexto + "/pagamento/webhook/veloce";
        String codigo = codigoEmpresaAtual();
        return codigo != null ? url + "?tenant=" + codigo : url;
    }

    private String codigoEmpresaAtual() {
        Tenant t = empresaAtual();
        return t != null ? t.codigo() : null;
    }

    private String nomeEmpresaAtual() {
        Tenant t = empresaAtual();
        return t != null ? t.nome() : null;
    }

    private Tenant empresaAtual() {
        try {
            String schema = ContextoUsuario.atual().getTenantSchema();
            return tenantRepository.listarTodos().stream()
                    .filter(t -> t.schemaName().equals(schema))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ── auxiliares ─────────────────────────────────────────────

    private static String mensagemErro(Resposta r, String acao) {
        String msg = r.json() != null ? r.json().path("message").asText("") : "";
        if (msg.contains("Token de dispositivo não encontrado")) {
            return "Veloce: token da maquininha não encontrado. Confira o token em painel.veloce.tech → Dispositivos "
                    + "e se o dispositivo está habilitado.";
        }
        if (msg.contains("Pagamento existente")) {
            return "Veloce: já existe um pagamento com este número. Tente cobrar de novo.";
        }
        return "Veloce: falha ao " + acao + " (HTTP " + r.status() + ")" + (msg.isBlank() ? "" : ": " + msg)
                + camposRecusados(r);
    }

    /** No 400, a Veloce lista em result os campos recusados: {"cnpj": ["Campo obrigatório cnpj"], ...}. */
    private static String camposRecusados(Resposta r) {
        JsonNode result = r.json() != null ? r.json().path("result") : null;
        if (result == null || !result.isObject() || result.isEmpty()) return "";
        List<String> motivos = new ArrayList<>();
        result.fields().forEachRemaining(e -> {
            if (e.getValue().isArray() && !e.getValue().isEmpty()) {
                e.getValue().forEach(m -> motivos.add(m.asText()));
            } else {
                motivos.add(e.getKey() + ": " + e.getValue().asText());
            }
        });
        return " (" + String.join("; ", motivos) + ")";
    }

    /** Mensagem da Veloce ou, se ela não mandar texto, o código HTTP: o erro nunca sai em branco. */
    private static String mensagemOuStatus(Resposta r) {
        String msg = r.json() != null ? r.json().path("message").asText("") : "";
        return msg.isBlank() ? "HTTP " + r.status() : msg;
    }

    private static String primeiro(String a, String b) {
        return a != null && !a.isBlank() ? a.split(",")[0].trim() : b;
    }

    private static String texto(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() || n.asText().isBlank() ? null : n.asText();
    }
}
