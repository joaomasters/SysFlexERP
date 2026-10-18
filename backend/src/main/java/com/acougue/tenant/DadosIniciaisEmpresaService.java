package com.acougue.tenant;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Pattern;

/*
 Deixa o schema de uma empresa só com CONFIGURAÇÃO, sem dados de negócio.

 Por que existe: o tenant novo é montado reaplicando a cadeia inteira de
 migrations — e a V3 (seed de demonstração do começo do projeto) e a V9
 (usuário teste/teste) vêm junto. Não dá para editar essas migrations: já
 rodaram em produção e o Flyway recusaria o checksum diferente. Então, depois
 de migrar, apagamos aqui tudo o que é dado de negócio.

 A regra é por EXCLUSÃO: esvazia todas as tabelas, menos as de configuração
 abaixo. Tabela nova criada no futuro já nasce vazia em empresa nova, sem
 precisar lembrar de atualizar esta classe.

 Nunca roda em "public" (a empresa principal) nem em schema fora do padrão
 tenant_* — ver validarSchema().
 */
@Service
public class DadosIniciaisEmpresaService {

    /** Configuração que a empresa precisa para funcionar — nunca é apagada. */
    static final Set<String> TABELAS_MANTIDAS = Set.of(
            "flyway_schema_history",      // controle das migrations
            "perfis",                     // perfis padrão (SUPER_ADMIN, ADMIN, GESTOR, CAIXA)
            "perfil_permissoes",          // matriz de permissões dos perfis
            "usuarios",                   // quem acessa (tratado à parte na criação)
            "pagamento_provedor_config",  // linhas das adquirentes (vazias numa empresa nova)
            "tenants"                     // registro de empresas (só é usado em public)
    );

    private static final Pattern SCHEMA_TENANT = Pattern.compile("^tenant_[a-z0-9_]+$");

    private final JdbcTemplate jdbc;

    public DadosIniciaisEmpresaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Quantos registros de dados de negócio cada tabela tem (só as com dados). Para a tela confirmar antes de limpar. */
    public Map<String, Long> contarDados(String schema) {
        validarSchema(schema);
        return jdbc.execute((ConnectionCallback<Map<String, Long>>) con -> contar(con, schema));
    }

    /** Apaga os dados de negócio de uma empresa existente, mantendo usuários e configuração. Tudo ou nada. */
    public Map<String, Long> limparDados(String schema) {
        validarSchema(schema);
        return emTransacao(con -> {
            Map<String, Long> antes = contar(con, schema);
            esvaziar(con, schema);
            return antes;
        });
    }

    /**
     * Empresa recém-migrada: esvazia os dados de demonstração e troca o
     * usuário padrão teste/teste pelo administrador informado. Tudo ou nada.
     */
    public void prepararEmpresaNova(String schema, String adminNome, String adminLogin, String adminSenhaHash) {
        validarSchema(schema);
        emTransacao(con -> {
            esvaziar(con, schema);
            Long perfilSuperAdmin;
            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id FROM " + schema + ".perfis WHERE nome = 'SUPER_ADMIN'")) {
                if (!rs.next()) throw new IllegalStateException("Perfil SUPER_ADMIN não encontrado em " + schema);
                perfilSuperAdmin = rs.getLong(1);
            }
            try (Statement st = con.createStatement()) {
                // O teste/teste da V9 tem senha conhecida — nunca pode existir numa empresa real.
                st.executeUpdate("DELETE FROM " + schema + ".usuarios");
            }
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO " + schema
                    + ".usuarios (nome, login, senha_hash, perfil_id, ativo) VALUES (?, ?, ?, ?, TRUE)")) {
                ps.setString(1, adminNome);
                ps.setString(2, adminLogin);
                ps.setString(3, adminSenhaHash);
                ps.setLong(4, perfilSuperAdmin);
                ps.executeUpdate();
            }
            return null;
        });
    }

    static void validarSchema(String schema) {
        if (schema == null || "public".equals(schema) || !SCHEMA_TENANT.matcher(schema).matches()) {
            throw new IllegalArgumentException(
                    "Operação permitida só em empresas adicionais (schema tenant_*), nunca na empresa principal.");
        }
    }

    // ── internos ───────────────────────────────────────────────

    private List<String> tabelasDeDados(Connection con, String schema) throws SQLException {
        List<String> tabelas = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String t = rs.getString(1);
                    if (!TABELAS_MANTIDAS.contains(t)) tabelas.add(t);
                }
            }
        }
        return tabelas;
    }

    private Map<String, Long> contar(Connection con, String schema) throws SQLException {
        Map<String, Long> contagem = new LinkedHashMap<>();
        for (String t : tabelasDeDados(con, schema)) {
            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery("SELECT count(*) FROM " + schema + ".\"" + t + "\"")) {
                rs.next();
                long n = rs.getLong(1);
                if (n > 0) contagem.put(t, n);
            }
        }
        return contagem;
    }

    private void esvaziar(Connection con, String schema) throws SQLException {
        List<String> tabelas = tabelasDeDados(con, schema);
        if (tabelas.isEmpty()) return;
        // Todas no MESMO TRUNCATE: as chaves estrangeiras entre elas não atrapalham.
        // Sem CASCADE de propósito — se uma tabela mantida apontasse para uma
        // destas, o Postgres recusa em vez de apagar a tabela mantida junto.
        StringJoiner lista = new StringJoiner(", ");
        tabelas.forEach(t -> lista.add(schema + ".\"" + t + "\""));
        try (Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE " + lista + " RESTART IDENTITY");
            // Cliente genérico usado pelo PDV/NF como "consumidor final" (criado pela V1).
            st.executeUpdate("INSERT INTO " + schema + ".clientes (nome, tipo_pessoa, tipo_cliente) "
                    + "VALUES ('CONSUMIDOR', 'PF', 'VAREJO')");
        }
    }

    @FunctionalInterface
    private interface Trabalho<T> {
        T executar(Connection con) throws SQLException;
    }

    private <T> T emTransacao(Trabalho<T> trabalho) {
        return jdbc.execute((ConnectionCallback<T>) con -> {
            boolean autoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                T resultado = trabalho.executar(con);
                con.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(autoCommit);
            }
        });
    }
}
