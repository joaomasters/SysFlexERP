package com.acougue.modules.pagamento;

import com.acougue.entity.Modulo;
import com.acougue.entity.ProvedorPagamento;
import com.acougue.entity.TerminalPagamento;
import com.acougue.exception.BusinessException;
import com.acougue.modules.pagamento.dto.ProvedorConfigDTO;
import com.acougue.modules.pagamento.dto.SalvarProvedorConfigDTO;
import com.acougue.modules.pagamento.dto.SalvarTerminalDTO;
import com.acougue.modules.pagamento.provedor.ProvedorTerminalNuvem;
import com.acougue.modules.pagamento.provedor.ProvedorTerminalNuvem.DispositivoRemoto;
import com.acougue.modules.pagamento.provedor.ProvedorTerminalNuvem.TesteConexao;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.ExigirPermissao;
import com.acougue.tenant.Tenant;
import com.acougue.tenant.TenantRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Configuração das maquininhas (credenciais e terminais) — módulo MAQUININHAS. */
@RestController
@RequestMapping("/maquininhas")
public class MaquininhasController {

    private final CredenciaisPagamentoService credenciais;
    private final TerminalPagamentoService terminalService;
    private final Map<ProvedorPagamento, ProvedorTerminalNuvem> provedores = new EnumMap<>(ProvedorPagamento.class);

    private final TenantRepository tenantRepository;

    public MaquininhasController(CredenciaisPagamentoService credenciais,
                                 TerminalPagamentoService terminalService,
                                 TenantRepository tenantRepository,
                                 List<ProvedorTerminalNuvem> implementacoes) {
        this.credenciais = credenciais;
        this.terminalService = terminalService;
        this.tenantRepository = tenantRepository;
        implementacoes.forEach(p -> provedores.put(p.provedor(), p));
    }

    // ── Credenciais ────────────────────────────────────────────

    @ExigirPermissao(modulo = Modulo.MAQUININHAS, acao = Acao.VER)
    @GetMapping("/provedores")
    public ResponseEntity<List<ProvedorConfigDTO>> listarProvedores() {
        return ResponseEntity.ok(credenciais.listar());
    }

    @ExigirPermissao(modulo = Modulo.MAQUININHAS, acao = Acao.EDITAR)
    @PutMapping("/provedores/{provedor}")
    public ResponseEntity<ProvedorConfigDTO> salvarProvedor(@PathVariable ProvedorPagamento provedor,
                                                            @RequestBody @Valid SalvarProvedorConfigDTO dto) {
        return ResponseEntity.ok(credenciais.salvar(provedor, dto));
    }

    @ExigirPermissao(modulo = Modulo.MAQUININHAS, acao = Acao.VER)
    @PostMapping("/provedores/{provedor}/testar")
    public ResponseEntity<TesteConexao> testar(@PathVariable ProvedorPagamento provedor) {
        try {
            return ResponseEntity.ok(nuvem(provedor).testarConexao(credenciais.credencialObrigatoria(provedor)));
        } catch (BusinessException e) {
            return ResponseEntity.ok(new TesteConexao(false, e.getMessage()));
        }
    }

    @ExigirPermissao(modulo = Modulo.MAQUININHAS, acao = Acao.VER)
    @GetMapping("/provedores/{provedor}/dispositivos")
    public ResponseEntity<List<DispositivoRemoto>> dispositivos(@PathVariable ProvedorPagamento provedor) {
        return ResponseEntity.ok(nuvem(provedor).listarDispositivos(credenciais.credencialObrigatoria(provedor)));
    }

    @ExigirPermissao(modulo = Modulo.MAQUININHAS, acao = Acao.EDITAR)
    @PostMapping("/provedores/{provedor}/dispositivos/{dispositivo}/modo-pdv")
    public ResponseEntity<Void> ativarModoPdv(@PathVariable ProvedorPagamento provedor, @PathVariable String dispositivo) {
        nuvem(provedor).ativarModoPdv(dispositivo, credenciais.credencialObrigatoria(provedor));
        return ResponseEntity.noContent().build();
    }

    /**
     * Caminhos de webhook desta empresa, para colar nos painéis das adquirentes.
     * A tela prefixa com o domínio (window.location.origin) — o rewrite /api
     * do Vercel repassa o POST para o backend.
     */
    @ExigirPermissao(modulo = Modulo.MAQUININHAS, acao = Acao.VER)
    @GetMapping("/webhooks")
    public ResponseEntity<Map<String, String>> webhooks() {
        String schema = ContextoUsuario.atual().getTenantSchema();
        String codigo = tenantRepository.listarTodos().stream()
                .filter(t -> t.schemaName().equals(schema))
                .map(Tenant::codigo)
                .findFirst()
                .orElse("<CODIGO_DA_EMPRESA>");
        return ResponseEntity.ok(Map.of(
                "codigoEmpresa", codigo,
                "mercadoPago", "/api/pagamento/webhook/mercadopago?tenant=" + codigo,
                "stone", "/api/pagamento/webhook/stone?tenant=" + codigo));
    }

    // ── Terminais ──────────────────────────────────────────────

    @ExigirPermissao(modulo = Modulo.MAQUININHAS, acao = Acao.VER)
    @GetMapping("/terminais")
    public ResponseEntity<List<TerminalPagamento>> listarTerminais() {
        return ResponseEntity.ok(terminalService.listar());
    }

    @ExigirPermissao(modulo = Modulo.MAQUININHAS, acao = Acao.CRIAR)
    @PostMapping("/terminais")
    public ResponseEntity<TerminalPagamento> criarTerminal(@RequestBody @Valid SalvarTerminalDTO dto) {
        return ResponseEntity.ok(terminalService.criar(dto));
    }

    @ExigirPermissao(modulo = Modulo.MAQUININHAS, acao = Acao.EDITAR)
    @PutMapping("/terminais/{id}")
    public ResponseEntity<TerminalPagamento> atualizarTerminal(@PathVariable Long id,
                                                               @RequestBody @Valid SalvarTerminalDTO dto) {
        return ResponseEntity.ok(terminalService.atualizar(id, dto));
    }

    private ProvedorTerminalNuvem nuvem(ProvedorPagamento provedor) {
        ProvedorTerminalNuvem p = provedores.get(provedor);
        if (p == null) {
            throw new BusinessException(provedor.getRotulo()
                    + " não tem API em nuvem — use maquininhas em modo Agente local (TEF) ou Manual.");
        }
        return p;
    }
}
