package com.acougue.tenant;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.regex.Pattern;

/*
 Onboarding de um tenant novo: cria o schema e aplica nele a MESMA cadeia
 de migrations (classpath:db/migration) que já roda em public — nenhuma
 migration existente é editada, só reaplicada num schema vazio. Isso cria
 produtos/vendas/caixa/usuarios/perfis do zero, prontos pro primeiro login
 daquele negócio.
 */
@Service
public class TenantProvisioningService {

    private static final Pattern SLUG_VALIDO = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TenantRepository tenantRepository;

    public TenantProvisioningService(DataSource dataSource, JdbcTemplate jdbc, TenantRepository tenantRepository) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.tenantRepository = tenantRepository;
    }

    public Tenant provisionar(String slugBruto, String nome) {
        String slug = normalizarSlug(slugBruto);
        if (!SLUG_VALIDO.matcher(slug).matches()) {
            throw new IllegalArgumentException(
                    "Slug inválido: use apenas letras minúsculas, números e hífen (ex: padaria-maria)");
        }
        if (tenantRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Já existe um tenant com o slug '" + slug + "'");
        }

        String schemaName = "tenant_" + slug.replace('-', '_');

        // Defesa extra — Flyway também cria o schema sozinho via createSchemas(true),
        // mas deixar explícito não custa nada e documenta a intenção.
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);

        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();

        String codigo = proximoCodigo();
        tenantRepository.insert(codigo, slug, nome, schemaName);

        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("Tenant recém-criado não encontrado: " + slug));
    }

    private String normalizarSlug(String slugBruto) {
        return slugBruto == null ? "" : slugBruto.trim().toLowerCase();
    }

    // Sequencial, 3 dígitos (001, 002, ...) — curto o bastante pra passar por
    // telefone/WhatsApp pro dono do negócio novo digitar no login.
    private String proximoCodigo() {
        return String.format("%03d", tenantRepository.contarTenants() + 1);
    }
}
