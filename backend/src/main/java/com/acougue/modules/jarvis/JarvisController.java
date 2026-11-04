package com.acougue.modules.jarvis;

import com.acougue.security.ContextoUsuario;
import com.acougue.security.UsuarioAutenticado;
import com.acougue.tenant.Tenant;
import com.acougue.tenant.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Painel do JARVIS. Só a sessão do painel entra aqui: nem usuário de empresa, nem a visualização. */
@RestController
@RequestMapping("/jarvis")
public class JarvisController {

    private final JarvisPainelService painel;
    private final JarvisAutenticacao autenticacao;
    private final TenantRepository tenantRepository;

    public JarvisController(JarvisPainelService painel, JarvisAutenticacao autenticacao,
                            TenantRepository tenantRepository) {
        this.painel = painel;
        this.autenticacao = autenticacao;
        this.tenantRepository = tenantRepository;
    }

    @GetMapping("/empresas")
    public ResponseEntity<List<JarvisPainelService.ResumoEmpresa>> empresas() {
        exigirPainel();
        return ResponseEntity.ok(painel.listarEmpresas());
    }

    @GetMapping("/acessos")
    public ResponseEntity<List<JarvisPainelService.Acesso>> acessos() {
        exigirPainel();
        return ResponseEntity.ok(painel.listarAcessos());
    }

    /** Registra o acesso antes de emitir o token: visualização sem registro não acontece. */
    @PostMapping("/empresas/{id}/visualizar")
    public ResponseEntity<Map<String, Object>> visualizar(@PathVariable Long id) {
        UsuarioAutenticado jarvis = exigirPainel();
        Tenant empresa = tenantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Empresa não encontrada: " + id));
        painel.registrarVisualizacao(jarvis.getLogin(), empresa);
        return ResponseEntity.ok(autenticacao.sessaoEmpresa(empresa.schemaName(), empresa.codigo(), empresa.nome()));
    }

    private UsuarioAutenticado exigirPainel() {
        UsuarioAutenticado usuario = ContextoUsuario.atual();
        if (!usuario.isJarvis() || usuario.isSomenteLeitura()) {
            throw new AccessDeniedException("Acesso restrito ao JARVIS.");
        }
        return usuario;
    }
}
