package com.acougue.tenant;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final DadosIniciaisEmpresaService dadosIniciais;
    private final PasswordEncoder passwordEncoder;

    public TenantProvisioningService(DataSource dataSource, JdbcTemplate jdbc, TenantRepository tenantRepository,
                                     DadosIniciaisEmpresaService dadosIniciais, PasswordEncoder passwordEncoder) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.tenantRepository = tenantRepository;
        this.dadosIniciais = dadosIniciais;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @param adminSenha senha em claro do primeiro administrador da empresa
     *                   (vira BCrypt aqui; nunca é gravada nem logada em claro)
     */
    public Tenant provisionar(String nome, String adminNome, String adminLogin, String adminSenha) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome da empresa é obrigatório.");
        }
        if (adminNome == null || adminNome.isBlank() || adminLogin == null || adminLogin.isBlank()) {
            throw new IllegalArgumentException("Informe nome e login do administrador da empresa.");
        }
        if (adminSenha == null || adminSenha.length() < 6) {
            throw new IllegalArgumentException("A senha do administrador precisa ter pelo menos 6 caracteres.");
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

        // As migrations trazem o seed de demonstração (V3) e o usuário teste/teste (V9):
        // a empresa nova começa vazia, só com o administrador informado.
        dadosIniciais.prepararEmpresaNova(schemaName, adminNome.trim(), adminLogin.trim(),
                passwordEncoder.encode(adminSenha));

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
