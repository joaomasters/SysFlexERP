package com.acougue.modules.industrial;

import com.acougue.tenant.TenantContext;
import com.acougue.tenant.TenantRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** O menu pergunta aqui se mostra a parte industrial. Qualquer usuário logado pode perguntar. */
@RestController
@RequestMapping("/industrial")
public class ModuloIndustrialController {

    private final TenantRepository tenantRepository;

    public ModuloIndustrialController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public record Situacao(boolean habilitado) {}

    @GetMapping("/situacao")
    public ResponseEntity<Situacao> situacao() {
        String schema = TenantContext.get();
        return ResponseEntity.ok(new Situacao(schema != null && tenantRepository.moduloIndustrialHabilitado(schema)));
    }
}
