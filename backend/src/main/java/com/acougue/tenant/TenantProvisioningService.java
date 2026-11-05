package com.acougue.tenant;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
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
        if (adminSenha == null || adminSenha.length() < 8) {
            throw new IllegalArgumentException("A senha do administrador precisa ter pelo menos 8 caracteres.");
        }

        String slug = reservarSchema(nome.trim());
        String schemaName = schemaDe(slug);

        // Mesma configuração usada no boot para manter todas as empresas atualizadas
        MigradorSchemasEmpresas.migrar(dataSource, schemaName);

        // As migrations trazem o seed de demonstração (V3) e o usuário teste/teste (V9):
        // a empresa nova começa vazia, só com o administrador informado.
        dadosIniciais.prepararEmpresaNova(schemaName, nome.trim(), adminNome.trim(), adminLogin.trim(),
                passwordEncoder.encode(adminSenha));

        inserirComCodigoLivre(slug, nome.trim(), schemaName);

        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("Tenant recém-criado não encontrado: " + slug));
    }

    static final int TENTATIVAS_CODIGO = 10;

    // Sequencial, 3 dígitos (001, 002, ...) — curto o bastante pra passar por
    // telefone/WhatsApp pro dono do negócio novo digitar no login.
    // Maior código + 1 (antes era a QUANTIDADE + 1: repetia código se uma empresa
    // sumisse). Duas empresas criadas juntas leem o mesmo maior: a segunda bate na
    // unique de codigo e tenta o próximo.
    void inserirComCodigoLivre(String slug, String nome, String schemaName) {
        for (int i = 0; i < TENTATIVAS_CODIGO; i++) {
            String codigo = String.format("%03d", tenantRepository.maiorCodigo() + 1);
            try {
                tenantRepository.insertInativo(codigo, slug, nome, schemaName);
                return;
            } catch (DuplicateKeyException outraEmpresaPegouOCodigo) {
                // tenta de novo com o maior atualizado
            }
        }
        throw new IllegalStateException("Não foi possível reservar um código para a empresa. Tente de novo.");
    }

    // "Padaria da Maria" -> "padaria-da-maria". Se já existir, acrescenta
    // um sufixo numérico (padaria-da-maria-2) até achar um livre — nunca
    // falha por conflito, só o slug muda, o que a pessoa vê é o código.
    // O schema é criado aqui SEM "IF NOT EXISTS": é ele que reserva o nome. Antes, duas
    // empresas de mesmo nome criadas juntas caíam no mesmo schema (e uma sobra de criação
    // que falhou no meio também era reaproveitada).
    String reservarSchema(String nome) {
        String base = slugificar(nome);
        for (int sufixo = 1; sufixo <= 1000; sufixo++) {
            String slug = sufixo == 1 ? base : base + "-" + sufixo;
            if (tenantRepository.existsBySlug(slug)) continue;
            try {
                jdbc.execute("CREATE SCHEMA " + schemaDe(slug)); // slug só tem [a-z0-9-]
                return slug;
            } catch (DataAccessException schemaJaExiste) {
                // outra criação pegou este nome agora, ou é sobra antiga: próximo sufixo
            }
        }
        throw new IllegalStateException("Não foi possível reservar um nome para a empresa: " + nome);
    }

    private static String schemaDe(String slug) {
        return "tenant_" + slug.replace('-', '_');
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
