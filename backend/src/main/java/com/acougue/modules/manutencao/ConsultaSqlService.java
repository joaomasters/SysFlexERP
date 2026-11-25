package com.acougue.modules.manutencao;

import com.acougue.exception.BusinessException;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.UsuarioAutenticado;
import com.acougue.security.auditoria.AuditoriaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/*
 Consulta SQL (só SELECT) do SUPER_ADMIN, sempre nos dados da empresa dele.

 A segurança NÃO depende de analisar o texto do SQL (isso sempre tem furo). A consulta roda numa
 conexão separada, aberta com um usuário do Postgres criado só para esta empresa:
  - sem superusuário e sem herdar papéis: não lê o schema de outra empresa nem as tabelas da
    plataforma (public.tenants, flyway...), não escreve (só tem SELECT) e não consegue "virar"
    outro usuário (set_config('role') é conferido contra quem abriu a conexão);
  - colunas de segredo (senha, hash, token, *_cripto...) ficam fora do GRANT;
  - transação só de leitura, tempo máximo e no máximo MAX_LINHAS linhas;
  - a senha desse usuário é sorteada a cada consulta, vale 2 minutos e é apagada no fim.
 O texto só é conferido para dar mensagem clara (uma consulta por vez, começando com SELECT/WITH).
 Toda consulta fica na auditoria.
 */
@Slf4j
@Service
public class ConsultaSqlService {

    static final int MAX_LINHAS = 1000;
    static final int TEMPO_MAXIMO_S = 15;
    static final int MAX_CARACTERES = 10_000;

    /** Colunas que nunca são liberadas, em nenhuma tabela. */
    static final Pattern COLUNA_SECRETA = Pattern.compile(
            "(?i).*(senha|password|hash|token|segredo|secret|totp|cripto|pfx).*");
    /** Tabelas da plataforma (ficam no schema public, junto da empresa 001). */
    static final Set<String> TABELAS_FORA = Set.of("tenants", "flyway_schema_history", "jarvis_acessos");
    private static final Pattern SCHEMA_VALIDO = Pattern.compile("[a-z0-9_]+");

    public record Resultado(List<String> colunas, List<List<String>> linhas, boolean cortado, long milissegundos) {}

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final AuditoriaService auditoria;
    private final SecureRandom aleatorio = new SecureRandom();
    private final Map<String, Object> travas = new ConcurrentHashMap<>();
    private volatile String urlBanco;

    public ConsultaSqlService(JdbcTemplate jdbc, DataSource dataSource, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.auditoria = auditoria;
    }

    public Resultado consultar(String sqlDigitado) {
        UsuarioAutenticado usuario = exigirSuperAdmin();
        String sql = conferirTexto(sqlDigitado);
        String schema = usuario.getTenantSchema();
        if (schema == null || !SCHEMA_VALIDO.matcher(schema).matches()) {
            throw new BusinessException("Empresa sem schema válido para consulta.");
        }
        String papel = papelDaEmpresa(schema);
        long inicio = System.nanoTime();
        Resultado r;
        synchronized (travas.computeIfAbsent(papel, k -> new Object())) {
            String senha = prepararPapel(papel, schema);
            try {
                r = executar(sql, papel, senha, inicio);
            } finally {
                desligarPapel(papel);
            }
        }
        auditoria.registrar("MANUTENCAO", "CONSULTAR", "Consulta SQL (" + r.linhas().size()
                + (r.cortado() ? "+" : "") + " linhas): " + resumir(sql));
        log.info("Consulta SQL de {} em {}: {} linha(s) em {} ms", usuario.getLogin(), schema, r.linhas().size(), r.milissegundos());
        return r;
    }

    /** Uma consulta só, começando com SELECT ou WITH. Ponto e vírgula no fim é aceito. */
    static String conferirTexto(String sql) {
        if (sql == null || sql.isBlank()) throw new BusinessException("Digite a consulta.");
        if (sql.length() > MAX_CARACTERES) {
            throw new BusinessException("Consulta muito longa (máximo " + MAX_CARACTERES + " caracteres).");
        }
        String limpo = sql.strip().replaceAll(";\\s*$", "").strip();
        if (limpo.contains(";")) {
            throw new BusinessException("Uma consulta por vez (sem ponto e vírgula no meio).");
        }
        String semComentario = limpo.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--.*$", " ").strip();
        if (!semComentario.matches("(?is)^(select|with)\\b.*")) {
            throw new BusinessException("Só é permitido consultar: a consulta deve começar com SELECT ou WITH.");
        }
        return limpo;
    }

    static String papelDaEmpresa(String schema) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(schema.getBytes(StandardCharsets.UTF_8));
            return "sysflex_consulta_" + HexFormat.of().formatHex(d, 0, 8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Cria/atualiza o usuário de consulta da empresa e devolve a senha desta consulta. */
    private String prepararPapel(String papel, String schema) {
        jdbc.execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + papel + "') THEN "
                + "CREATE ROLE " + papel + " NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION "
                + "NOBYPASSRLS CONNECTION LIMIT 2; END IF; END $$");
        String s = '"' + schema + '"';
        jdbc.execute("ALTER ROLE " + papel + " SET search_path = " + s);
        jdbc.execute("ALTER ROLE " + papel + " SET default_transaction_read_only = on");
        jdbc.execute("ALTER ROLE " + papel + " SET statement_timeout = '" + TEMPO_MAXIMO_S + "s'");

        // Refaz as permissões a cada consulta: tabela nova de migration entra, coluna secreta nova sai
        jdbc.execute("REVOKE ALL ON ALL TABLES IN SCHEMA " + s + " FROM " + papel);
        jdbc.execute("GRANT USAGE ON SCHEMA " + s + " TO " + papel);
        Map<String, List<String>> colunas = new LinkedHashMap<>();
        jdbc.query("SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = ? "
                        + "ORDER BY table_name, ordinal_position",
                rs -> { colunas.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2)); },
                schema);
        colunas.forEach((tabela, cols) -> {
            if (TABELAS_FORA.contains(tabela)) return;
            List<String> liberadas = cols.stream().filter(c -> !COLUNA_SECRETA.matcher(c).matches()).toList();
            if (liberadas.isEmpty()) return;
            String alvo = s + "." + aspas(tabela);
            if (liberadas.size() == cols.size()) {
                jdbc.execute("GRANT SELECT ON " + alvo + " TO " + papel);
            } else {
                jdbc.execute("GRANT SELECT (" + liberadas.stream().map(ConsultaSqlService::aspas)
                        .collect(Collectors.joining(", ")) + ") ON " + alvo + " TO " + papel);
            }
        });

        byte[] b = new byte[24];
        aleatorio.nextBytes(b);
        String senha = Base64.getUrlEncoder().withoutPadding().encodeToString(b);   // só [A-Za-z0-9_-]
        jdbc.execute("ALTER ROLE " + papel + " WITH LOGIN PASSWORD '" + senha + "' VALID UNTIL '"
                + OffsetDateTime.now().plusMinutes(2) + "'");
        return senha;
    }

    private void desligarPapel(String papel) {
        try {
            jdbc.execute("ALTER ROLE " + papel + " WITH NOLOGIN PASSWORD NULL");
        } catch (Exception e) {
            log.warn("Não foi possível desligar o usuário de consulta {}: {}", papel, e.getMessage());
        }
    }

    private Resultado executar(String sql, String papel, String senha, long inicio) {
        Properties p = new Properties();
        p.setProperty("user", papel);
        p.setProperty("password", senha);
        p.setProperty("ApplicationName", "sysflex-consulta");
        p.setProperty("connectTimeout", "10");
        try (Connection c = DriverManager.getConnection(urlBanco(), p)) {
            c.setReadOnly(true);
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.setQueryTimeout(TEMPO_MAXIMO_S);
                st.setMaxRows(MAX_LINHAS + 1);
                st.setFetchSize(200);
                try (ResultSet rs = st.executeQuery(sql)) {
                    ResultSetMetaData md = rs.getMetaData();
                    List<String> colunas = new ArrayList<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) colunas.add(md.getColumnLabel(i));
                    List<List<String>> linhas = new ArrayList<>();
                    boolean cortado = false;
                    while (rs.next()) {
                        if (linhas.size() == MAX_LINHAS) {
                            cortado = true;
                            break;
                        }
                        List<String> linha = new ArrayList<>(colunas.size());
                        for (int i = 1; i <= colunas.size(); i++) linha.add(texto(rs.getObject(i)));
                        linhas.add(linha);
                    }
                    return new Resultado(colunas, linhas, cortado, (System.nanoTime() - inicio) / 1_000_000);
                }
            } finally {
                c.rollback();
            }
        } catch (SQLException e) {
            throw new BusinessException(traduzir(e));
        }
    }

    private static String texto(Object v) {
        if (v == null) return null;
        if (v instanceof byte[]) return "<binário>";
        return v.toString();
    }

    static String traduzir(SQLException e) {
        String m = Objects.requireNonNullElse(e.getMessage(), "erro desconhecido");
        String estado = Objects.requireNonNullElse(e.getSQLState(), "");
        if (estado.equals("42501")) {
            return "Sem acesso: a tabela ou coluna é protegida (senhas, tokens, dados da plataforma) ou é de outra empresa. " + m;
        }
        if (estado.equals("25006")) return "Só é permitido consultar (a consulta tentou alterar dados).";
        if (estado.equals("57014")) return "A consulta passou de " + TEMPO_MAXIMO_S + " segundos e foi cancelada.";
        return m;
    }

    private static String aspas(String identificador) {
        return '"' + identificador.replace("\"", "\"\"") + '"';
    }

    private static String resumir(String sql) {
        String uma = sql.replaceAll("\\s+", " ");
        return uma.length() > 500 ? uma.substring(0, 500) + "…" : uma;
    }

    private String urlBanco() throws SQLException {
        if (urlBanco == null) {
            try (Connection c = dataSource.getConnection()) {
                urlBanco = c.getMetaData().getURL();
            }
        }
        return urlBanco;
    }

    private static UsuarioAutenticado exigirSuperAdmin() {
        UsuarioAutenticado usuario = ContextoUsuario.atual();
        if (!usuario.isSuperAdmin() || usuario.isJarvis() || usuario.isSomenteLeitura()) {
            throw new AccessDeniedException("Apenas o SUPER_ADMIN da empresa pode usar a consulta SQL.");
        }
        return usuario;
    }
}
