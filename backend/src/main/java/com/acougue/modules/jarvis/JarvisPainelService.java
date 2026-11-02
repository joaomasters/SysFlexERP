package com.acougue.modules.jarvis;

import com.acougue.tenant.Tenant;
import com.acougue.tenant.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;

/*
 Números de todas as empresas numa tela só. Cada empresa tem o próprio schema,
 então a consulta é por JdbcTemplate com o schema escrito na SQL: o Hibernate
 fica preso a um único schema durante a requisição.
 */
@Slf4j
@Service
public class JarvisPainelService {

    private static final Pattern SCHEMA_VALIDO = Pattern.compile("^(public|tenant_[a-z0-9_]+)$");
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final int LIMITE_ACESSOS = 100;

    public record ResumoEmpresa(Long id, String codigo, String nome, boolean ativo,
                                long vendasHoje, BigDecimal faturamentoHoje, BigDecimal faturamentoMes,
                                long produtosAtivos, long usuariosAtivos, LocalDateTime ultimaVenda,
                                boolean erro) {}

    public record Acesso(Long id, String login, String acao, String empresaCodigo, String empresaNome,
                         OffsetDateTime criadoEm) {}

    private final JdbcTemplate jdbc;
    private final TenantRepository tenantRepository;

    public JarvisPainelService(JdbcTemplate jdbc, TenantRepository tenantRepository) {
        this.jdbc = jdbc;
        this.tenantRepository = tenantRepository;
    }

    public List<ResumoEmpresa> listarEmpresas() {
        LocalDate hoje = LocalDate.now(FUSO);
        return tenantRepository.listarTodos().stream().map(t -> resumir(t, hoje)).toList();
    }

    // Uma empresa com problema aparece marcada, sem esconder as outras
    private ResumoEmpresa resumir(Tenant t, LocalDate hoje) {
        try {
            if (!SCHEMA_VALIDO.matcher(t.schemaName()).matches()) {
                throw new IllegalStateException("Schema fora do padrão");
            }
            String s = t.schemaName();
            return jdbc.queryForObject("""
                    SELECT (SELECT COUNT(*) FROM %1$s.vendas WHERE status = 'FECHADA' AND CAST(data_venda AS date) = ?) AS vendas_hoje,
                           (SELECT COALESCE(SUM(total), 0) FROM %1$s.vendas WHERE status = 'FECHADA' AND CAST(data_venda AS date) = ?) AS faturamento_hoje,
                           (SELECT COALESCE(SUM(total), 0) FROM %1$s.vendas WHERE status = 'FECHADA' AND CAST(data_venda AS date) >= ?) AS faturamento_mes,
                           (SELECT COUNT(*) FROM %1$s.produtos WHERE ativo) AS produtos_ativos,
                           (SELECT COUNT(*) FROM %1$s.usuarios WHERE ativo) AS usuarios_ativos,
                           (SELECT MAX(data_venda) FROM %1$s.vendas WHERE status = 'FECHADA') AS ultima_venda
                    """.formatted(s),
                    (rs, i) -> new ResumoEmpresa(t.id(), t.codigo(), t.nome(), t.ativo(),
                            rs.getLong("vendas_hoje"), rs.getBigDecimal("faturamento_hoje"),
                            rs.getBigDecimal("faturamento_mes"), rs.getLong("produtos_ativos"),
                            rs.getLong("usuarios_ativos"), rs.getObject("ultima_venda", LocalDateTime.class), false),
                    Date.valueOf(hoje), Date.valueOf(hoje), Date.valueOf(hoje.withDayOfMonth(1)));
        } catch (Exception e) {
            log.warn("JARVIS: falha ao ler a empresa {}: {}", t.codigo(), e.getMessage());
            return new ResumoEmpresa(t.id(), t.codigo(), t.nome(), t.ativo(), 0, BigDecimal.ZERO, BigDecimal.ZERO,
                    0, 0, null, true);
        }
    }

    public void registrarLogin(String login) {
        jdbc.update("INSERT INTO public.jarvis_acessos (login, acao) VALUES (?, 'LOGIN')", login);
    }

    public void registrarVisualizacao(String login, Tenant empresa) {
        jdbc.update("""
                INSERT INTO public.jarvis_acessos (login, acao, tenant_id, tenant_codigo, tenant_nome)
                VALUES (?, 'VISUALIZAR_EMPRESA', ?, ?, ?)
                """, login, empresa.id(), empresa.codigo(), empresa.nome());
    }

    public List<Acesso> listarAcessos() {
        return jdbc.query("""
                SELECT id, login, acao, tenant_codigo, tenant_nome, criado_em
                FROM public.jarvis_acessos ORDER BY criado_em DESC, id DESC LIMIT ?
                """,
                (rs, i) -> new Acesso(rs.getLong("id"), rs.getString("login"), rs.getString("acao"),
                        rs.getString("tenant_codigo"), rs.getString("tenant_nome"),
                        rs.getObject("criado_em", OffsetDateTime.class)),
                LIMITE_ACESSOS);
    }
}
