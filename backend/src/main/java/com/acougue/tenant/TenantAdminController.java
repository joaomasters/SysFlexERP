package com.acougue.tenant;

import com.acougue.security.ContextoUsuario;
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
 por cima disso, exige superAdmin — reaproveita o mesmo mecanismo que já
 protege a matriz de perfis, sem criar role nova. Hoje só o superAdmin do
 tenant public (você) provisiona/ativa tenants novos; é uma ação de dono
 da plataforma, não de dono de UM negócio específico.
 */
@RestController
@RequestMapping("/admin/tenants")
public class TenantAdminController {

    private final TenantProvisioningService provisioningService;
    private final TenantRepository tenantRepository;

    public TenantAdminController(TenantProvisioningService provisioningService, TenantRepository tenantRepository) {
        this.provisioningService = provisioningService;
        this.tenantRepository = tenantRepository;
    }

    @GetMapping
    public ResponseEntity<List<Tenant>> listar() {
        exigirSuperAdmin();
        return ResponseEntity.ok(tenantRepository.listarTodos());
    }

    @PostMapping
    public ResponseEntity<Tenant> provisionar(@RequestBody Map<String, String> body) {
        exigirSuperAdmin();
        String nome = body.get("nome");
        if (nome == null || nome.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Tenant tenant = provisioningService.provisionar(nome);
        return ResponseEntity.status(HttpStatus.CREATED).body(tenant);
    }

    @PatchMapping("/{id}/ativar")
    public ResponseEntity<Tenant> ativar(@PathVariable Long id) {
        exigirSuperAdmin();
        buscarOuFalhar(id);
        tenantRepository.ativar(id);
        return ResponseEntity.ok(buscarOuFalhar(id));
    }

    @PatchMapping("/{id}/desativar")
    public ResponseEntity<Tenant> desativar(@PathVariable Long id) {
        exigirSuperAdmin();
        Tenant tenant = buscarOuFalhar(id);
        // "public" é o tenant em produção hoje (Açougue do Zé) — desativar
        // por engano tiraria todo mundo, inclusive você, do sistema.
        if ("public".equals(tenant.schemaName())) {
            throw new IllegalArgumentException("O tenant principal (public) não pode ser desativado.");
        }
        tenantRepository.desativar(id);
        return ResponseEntity.ok(buscarOuFalhar(id));
    }

    private void exigirSuperAdmin() {
        if (!ContextoUsuario.atual().isSuperAdmin()) {
            throw new AccessDeniedException("Somente super administradores podem gerenciar tenants.");
        }
    }

    private Tenant buscarOuFalhar(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tenant não encontrado: " + id));
    }
}
