package com.acougue.modules.industrial.pcp;

import com.acougue.entity.Modulo;
import com.acougue.modules.industrial.estrutura.MaquinaRepository;
import com.acougue.modules.industrial.pcp.PcpDto.*;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.ExigirPermissao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Ordens de produção e planejamento (módulo IND_PCP). Mudar status e escolher máquina pedem permissão de editar. */
@RestController
@RequestMapping("/industrial")
@RequiredArgsConstructor
public class PcpController {

    private final OrdemProducaoService ordens;
    private final CargaService carga;
    private final MaquinaRepository maquinas;

    private static Long usuario() {
        return ContextoUsuario.atual().getUsuarioId();
    }

    @ExigirPermissao(modulo = Modulo.IND_PCP, acao = Acao.VER)
    @GetMapping("/ordens")
    public List<OrdemResumo> listar(@RequestParam(required = false) String status) { return ordens.listar(status); }

    @ExigirPermissao(modulo = Modulo.IND_PCP, acao = Acao.VER)
    @GetMapping("/ordens/{id}")
    public OrdemOut buscar(@PathVariable Long id) { return ordens.buscar(id); }

    @ExigirPermissao(modulo = Modulo.IND_PCP, acao = Acao.CRIAR)
    @PostMapping("/ordens")
    public OrdemOut criar(@RequestBody @Valid OrdemIn in) { return ordens.criar(in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_PCP, acao = Acao.EDITAR)
    @PutMapping("/ordens/{id}")
    public OrdemOut editar(@PathVariable Long id, @RequestBody @Valid OrdemIn in) { return ordens.editar(id, in); }

    @ExigirPermissao(modulo = Modulo.IND_PCP, acao = Acao.EDITAR)
    @PostMapping("/ordens/{id}/status")
    public OrdemOut status(@PathVariable Long id, @RequestBody @Valid StatusIn in) {
        return ordens.mudarStatus(id, in, usuario());
    }

    @ExigirPermissao(modulo = Modulo.IND_PCP, acao = Acao.EDITAR)
    @PutMapping("/ordens/{id}/operacoes/{operacaoId}/maquina")
    public OrdemOut maquina(@PathVariable Long id, @PathVariable Long operacaoId, @RequestBody @Valid MaquinaOperacaoIn in) {
        return ordens.definirMaquina(id, operacaoId, in);
    }

    @ExigirPermissao(modulo = Modulo.IND_PCP, acao = Acao.VER)
    @GetMapping("/pcp/carga")
    public CargaOut carga(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return carga.carga(de, ate);
    }

    /** Máquinas para o PCP escolher nas operações (sem exigir acesso ao cadastro da estrutura). */
    @ExigirPermissao(modulo = Modulo.IND_PCP, acao = Acao.VER)
    @GetMapping("/pcp/maquinas")
    public List<MaquinaOpcao> maquinas() {
        return maquinas.listarTodas().stream().filter(m -> !"INATIVA".equals(m.getStatus()))
                .map(m -> new MaquinaOpcao(m.getId(), m.getCodigo(), m.getDescricao(), m.getCentroTrabalho().getId(),
                        m.getTipoMaquina().getId(), m.getStatus()))
                .toList();
    }
}
