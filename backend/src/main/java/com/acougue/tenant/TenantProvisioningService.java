package com.acougue.tenant;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.text.Normalizer;

/*
 Onboarding de um tenant novo: cria o schema e aplica nele a MESMA cadeia
 de migrations (classpath:db/migration) que já roda em public — nenhuma
 migration existente é editada, só reaplicada num schema vazio. Isso cria
 produtos/vendas/caixa/usuarios/perfis do zero, prontos pro primeiro login
 daquele negócio.

 Nasce INATIVO de propósito — só pode logar depois que alguém com acesso
 de super admin ativar explicitamente (ver TenantAdminController). Quem
 chama aqui só informa o nome; slug (interno, usado só pro nome do schema)
 e código (o que a pessoa digita no login) são gerados sozinhos.
 */
@Service
public class TenantProvisioningService {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TenantRepository tenantRepository;

    public TenantProvisioningService(DataSource dataSource, JdbcTemplate jdbc, TenantRepository tenantRepository) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.tenantRepository = tenantRepository;
    }

    public Tenant provisionar(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome da empresa é obrigatório.");
        }

        String slug = slugUnico(nome.trim());
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
        tenantRepository.insertInativo(codigo, slug, nome.trim(), schemaName);

        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("Tenant recém-criado não encontrado: " + slug));
    }

    // Sequencial, 3 dígitos (001, 002, ...) — curto o bastante pra passar por
    // telefone/WhatsApp pro dono do negócio novo digitar no login.
    private String proximoCodigo() {
        return String.format("%03d", tenantRepository.contarTenants() + 1);
    }

    // "Padaria da Maria" -> "padaria-da-maria". Se já existir, acrescenta
    // um sufixo numérico (padaria-da-maria-2) até achar um livre — nunca
    // falha por conflito, só o slug muda, o que a pessoa vê é o código.
    private String slugUnico(String nome) {
        String base = slugificar(nome);
        String slug = base;
        int sufixo = 2;
        while (tenantRepository.existsBySlug(slug)) {
            slug = base + "-" + sufixo++;
        }
        return slug;
    }

    private String slugificar(String texto) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = semAcento.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("[\\s-]+", "-");
        return slug.isBlank() ? "empresa" : slug;
    }
}
