package com.acougue.modules.balanca;

import com.acougue.entity.CargaAgendada;
import com.acougue.entity.Modulo;
import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import com.acougue.repository.CargaAgendadaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/balanca/agente")
@RequiredArgsConstructor
public class BalancaAgentController {

    private final CargaAgendadaRepository cargaAgendadaRepo;
    private final BalancaSchedulerService  schedulerService;

    @ExigirPermissao(modulo = Modulo.CARGA_BALANCA, acao = Acao.VER)
    @GetMapping("/carga-pendente")
    public ResponseEntity<Map<String, Object>> cargaPendente() {
        Optional<CargaAgendada> pendente =
                cargaAgendadaRepo.findFirstByStatusOrderByCriadoEmAsc("PENDENTE");

        if (pendente.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        CargaAgendada c = pendente.get();
        return ResponseEntity.ok(Map.of(
                "id",           c.getId(),
                "tipoBalanca",  c.getTipoBalanca(),
                "ipBalanca",    c.getIpBalanca()    != null ? c.getIpBalanca()    : "",
                "portaBalanca", c.getPortaBalanca(),
                "produtos",     c.getProdutosCount(),
                "conteudo",     c.getConteudoCarga(),
                "criadoEm",     c.getCriadoEm().toString()
        ));
    }

    @ExigirPermissao(modulo = Modulo.CARGA_BALANCA, acao = Acao.EDITAR)
    @PostMapping("/confirmar/{id}")
    public ResponseEntity<Void> confirmar(@PathVariable Long id) {
        return cargaAgendadaRepo.findById(id).map(c -> {
            c.setStatus("APLICADA");
            c.setAplicadoEm(LocalDateTime.now());
            cargaAgendadaRepo.save(c);
            log.info("[Balanca] Carga #{} confirmada pelo agente local.", id);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @ExigirPermissao(modulo = Modulo.CARGA_BALANCA, acao = Acao.EDITAR)
    @PostMapping("/erro/{id}")
    public ResponseEntity<Void> registrarErro(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return cargaAgendadaRepo.findById(id).map(c -> {
            c.setStatus("ERRO");
            c.setErroMensagem(body.getOrDefault("mensagem", "Erro desconhecido"));
            cargaAgendadaRepo.save(c);
            log.warn("[Balanca] Carga #{} falhou: {}", id, c.getErroMensagem());
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @ExigirPermissao(modulo = Modulo.CARGA_BALANCA, acao = Acao.EDITAR)
    @PostMapping("/forcar-carga")
    public ResponseEntity<Map<String, Object>> forcarCarga() {
        CargaAgendada carga = schedulerService.gerarCargaManual();
        if (carga == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Nenhum produto com codigo_balanca cadastrado"));
        }
        return ResponseEntity.ok(Map.of(
                "id",       carga.getId(),
                "produtos", carga.getProdutosCount(),
                "status",   carga.getStatus()
        ));
    }

    @ExigirPermissao(modulo = Modulo.CARGA_BALANCA, acao = Acao.VER)
    @GetMapping("/historico")
    public ResponseEntity<List<CargaAgendada>> historico() {
        return ResponseEntity.ok(cargaAgendadaRepo.findTop20ByOrderByCriadoEmDesc());
    }
}
