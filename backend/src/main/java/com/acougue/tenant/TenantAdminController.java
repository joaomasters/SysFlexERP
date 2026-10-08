package com.acougue.tenant;

import com.acougue.security.ContextoUsuario;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/*
 Onboarding de negócios novos no SysFlexERP. Fica atrás de autenticação
 normal (SecurityConfig já cobre /** com anyRequest().authenticated()) e,
 por cima disso, exige superAdmin — reaproveita o mesmo mecanismo que já
 protege a matriz de perfis, sem criar role nova. Hoje só o superAdmin do
 tenant public (você) provisiona tenants novos; é uma ação de dono da
 plataforma, não de dono de UM negócio específico.
 */
@RestController
@RequestMapping("/admin/tenants")
public class TenantAdminController {

    private final TenantProvisioningService provisioningService;

    public TenantAdminController(TenantProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @PostMapping
    public ResponseEntity<Tenant> provisionar(@RequestBody Map<String, String> body) {
        if (!ContextoUsuario.atual().isSuperAdmin()) {
            throw new AccessDeniedException("Somente super administradores podem criar tenants.");
        }
        String slug = body.get("slug");
        String nome = body.get("nome");
        if (slug == null || slug.isBlank() || nome == null || nome.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Tenant tenant = provisioningService.provisionar(slug, nome);
        return ResponseEntity.status(HttpStatus.CREATED).body(tenant);
    }
}
