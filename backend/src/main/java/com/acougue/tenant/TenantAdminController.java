package com.acougue.tenant;

import com.acougue.security.ContextoUsuario;
import com.acougue.security.UsuarioAutenticado;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/*
 Onboarding de negócios novos no SysFlexERP. Fica atrás de autenticação
 normal (SecurityConfig já cobre /** com anyRequest().authenticated()) e,
 por cima disso, exige o ADMINISTRADOR DA PLATAFORMA: superAdmin logado na
 empresa principal (schema public).

 Antes bastava ser superAdmin — mas TODA empresa tem o próprio SUPER_ADMIN
 (o dono daquele negócio), então o dono de qualquer empresa conseguia listar,
 ativar e desativar as empresas dos outros. Agora só quem está na empresa
 principal gerencia empresas.
 */
@RestController
@RequestMapping("/admin/tenants")
public class TenantAdminController {

    static final String SCHEMA_PRINCIPAL = "public";

    private final TenantProvisioningService provisioningService;
    private final TenantRepository tenantRepository;
    private final DadosIniciaisEmpresaService dadosIniciais;

    public TenantAdminController(TenantProvisioningService provisioningService, TenantRepository tenantRepository,
                                 DadosIniciaisEmpresaService dadosIniciais) {
        this.provisioningService = provisioningService;
        this.tenantRepository = tenantRepository;
        this.dadosIniciais = dadosIniciais;
    }

    @GetMapping
    public ResponseEntity<List<Tenant>> listar() {
        exigirAdministradorPlataforma();
        return ResponseEntity.ok(tenantRepository.listarTodos());
    }

    /** Corpo: { nome, adminNome, adminLogin, adminSenha } — a empresa nasce vazia, só com esse administrador. */
    @PostMapping
    public ResponseEntity<Tenant> provisionar(@RequestBody Map<String, String> body) {
        exigirAdministradorPlataforma();
        String nome = body.get("nome");
        if (nome == null || nome.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Tenant tenant = provisioningService.provisionar(nome,
                body.get("adminNome"), body.get("adminLogin"), body.get("adminSenha"));
        return ResponseEntity.status(HttpStatus.CREATED).body(tenant);
    }

    @PatchMapping("/{id}/ativar")
    public ResponseEntity<Tenant> ativar(@PathVariable Long id) {
        exigirAdministradorPlataforma();
        buscarOuFalhar(id);
        tenantRepository.ativar(id);
        return ResponseEntity.ok(buscarOuFalhar(id));
    }

    @PatchMapping("/{id}/desativar")
    public ResponseEntity<Tenant> desativar(@PathVariable Long id) {
        exigirAdministradorPlataforma();
        Tenant tenant = buscarOuFalhar(id);
        // "public" é a empresa principal — desativar por engano tiraria todo
        // mundo, inclusive você, do sistema.
        if (SCHEMA_PRINCIPAL.equals(tenant.schemaName())) {
            throw new IllegalArgumentException("O tenant principal (public) não pode ser desativado.");
        }
        tenantRepository.desativar(id);
        return ResponseEntity.ok(buscarOuFalhar(id));
    }

    /** Quantos registros de dados de negócio a empresa tem, por tabela — a tela mostra antes de limpar. */
    @GetMapping("/{id}/dados")
    public ResponseEntity<Map<String, Long>> contarDados(@PathVariable Long id) {
        exigirAdministradorPlataforma();
        return ResponseEntity.ok(dadosIniciais.contarDados(buscarOuFalhar(id).schemaName()));
    }

    /**
     * Apaga os dados de negócio da empresa (produtos, vendas, clientes, contas...),
     * mantendo usuários, perfis e configuração. Irreversível: exige que o corpo
     * repita o código da empresa — { "confirmacaoCodigo": "003" }.
     */
    @PostMapping("/{id}/limpar-dados")
    public ResponseEntity<Map<String, Long>> limparDados(@PathVariable Long id, @RequestBody Map<String, String> body) {
        exigirAdministradorPlataforma();
        Tenant tenant = buscarOuFalhar(id);
        if (!tenant.codigo().equals(body.get("confirmacaoCodigo"))) {
            throw new IllegalArgumentException("Confirmação inválida: digite o código da empresa (" + tenant.codigo() + ").");
        }
        return ResponseEntity.ok(dadosIniciais.limparDados(tenant.schemaName()));
    }

    private void exigirAdministradorPlataforma() {
        if (!ehAdministradorPlataforma(ContextoUsuario.atual())) {
            throw new AccessDeniedException(
                    "Somente o super administrador da empresa principal pode gerenciar empresas.");
        }
    }

    static boolean ehAdministradorPlataforma(UsuarioAutenticado usuario) {
        return usuario.isSuperAdmin() && SCHEMA_PRINCIPAL.equals(usuario.getTenantSchema());
    }

    private Tenant buscarOuFalhar(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tenant não encontrado: " + id));
    }
}
