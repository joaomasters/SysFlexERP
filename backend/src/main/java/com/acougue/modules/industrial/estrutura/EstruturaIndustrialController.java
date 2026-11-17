package com.acougue.modules.industrial.estrutura;

import com.acougue.entity.Modulo;
import com.acougue.modules.industrial.estrutura.EstruturaDto.*;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.ExigirPermissao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Estrutura produtiva (módulo IND_ESTRUTURA). Só responde nas empresas com os módulos
 * industriais habilitados (ModuloIndustrialInterceptor). Inativar pede permissão de excluir;
 * reativar, mudar status, horímetro e manutenção pedem permissão de editar.
 */
@RestController
@RequestMapping("/industrial")
@RequiredArgsConstructor
public class EstruturaIndustrialController {

    private final EstruturaService estrutura;
    private final MaquinaService maquinas;
    private final FerramentaService ferramentas;
    private final AlocacaoService alocacoes;

    private static Long usuario() {
        return ContextoUsuario.atual().getUsuarioId();
    }

    // ── Unidades ─────────────────────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.VER)
    @GetMapping("/unidades")
    public List<UnidadeOut> unidades() { return estrutura.listarUnidades(); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.CRIAR)
    @PostMapping("/unidades")
    public UnidadeOut criarUnidade(@RequestBody @Valid UnidadeIn in) { return estrutura.salvarUnidade(null, in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PutMapping("/unidades/{id}")
    public UnidadeOut editarUnidade(@PathVariable Long id, @RequestBody @Valid UnidadeIn in) {
        return estrutura.salvarUnidade(id, in, usuario());
    }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EXCLUIR)
    @PostMapping("/unidades/{id}/inativar")
    public ResponseEntity<Void> inativarUnidade(@PathVariable Long id) { estrutura.ativarUnidade(id, false); return ok(); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PostMapping("/unidades/{id}/reativar")
    public ResponseEntity<Void> reativarUnidade(@PathVariable Long id) { estrutura.ativarUnidade(id, true); return ok(); }

    // ── Setores ──────────────────────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.VER)
    @GetMapping("/setores")
    public List<SetorOut> setores() { return estrutura.listarSetores(); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.CRIAR)
    @PostMapping("/setores")
    public SetorOut criarSetor(@RequestBody @Valid SetorIn in) { return estrutura.salvarSetor(null, in); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PutMapping("/setores/{id}")
    public SetorOut editarSetor(@PathVariable Long id, @RequestBody @Valid SetorIn in) { return estrutura.salvarSetor(id, in); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EXCLUIR)
    @PostMapping("/setores/{id}/inativar")
    public ResponseEntity<Void> inativarSetor(@PathVariable Long id) { estrutura.ativarSetor(id, false); return ok(); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PostMapping("/setores/{id}/reativar")
    public ResponseEntity<Void> reativarSetor(@PathVariable Long id) { estrutura.ativarSetor(id, true); return ok(); }

    // ── Centros de trabalho ──────────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.VER)
    @GetMapping("/centros")
    public List<CentroOut> centros() { return estrutura.listarCentros(); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.CRIAR)
    @PostMapping("/centros")
    public CentroOut criarCentro(@RequestBody @Valid CentroIn in) { return estrutura.salvarCentro(null, in); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PutMapping("/centros/{id}")
    public CentroOut editarCentro(@PathVariable Long id, @RequestBody @Valid CentroIn in) { return estrutura.salvarCentro(id, in); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EXCLUIR)
    @PostMapping("/centros/{id}/inativar")
    public ResponseEntity<Void> inativarCentro(@PathVariable Long id) { estrutura.ativarCentro(id, false); return ok(); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PostMapping("/centros/{id}/reativar")
    public ResponseEntity<Void> reativarCentro(@PathVariable Long id) { estrutura.ativarCentro(id, true); return ok(); }

    // ── Turnos ───────────────────────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.VER)
    @GetMapping("/turnos")
    public List<TurnoOut> turnos() { return estrutura.listarTurnos(); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.CRIAR)
    @PostMapping("/turnos")
    public TurnoOut criarTurno(@RequestBody @Valid TurnoIn in) { return estrutura.salvarTurno(null, in); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PutMapping("/turnos/{id}")
    public TurnoOut editarTurno(@PathVariable Long id, @RequestBody @Valid TurnoIn in) { return estrutura.salvarTurno(id, in); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EXCLUIR)
    @PostMapping("/turnos/{id}/inativar")
    public ResponseEntity<Void> inativarTurno(@PathVariable Long id) { estrutura.ativarTurno(id, false); return ok(); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PostMapping("/turnos/{id}/reativar")
    public ResponseEntity<Void> reativarTurno(@PathVariable Long id) { estrutura.ativarTurno(id, true); return ok(); }

    // ── Tipos de máquina ─────────────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.VER)
    @GetMapping("/tipos-maquina")
    public List<TipoMaquinaOut> tipos() { return maquinas.listarTipos(); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.CRIAR)
    @PostMapping("/tipos-maquina")
    public TipoMaquinaOut criarTipo(@RequestBody @Valid TipoMaquinaIn in) { return maquinas.salvarTipo(null, in); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PutMapping("/tipos-maquina/{id}")
    public TipoMaquinaOut editarTipo(@PathVariable Long id, @RequestBody @Valid TipoMaquinaIn in) {
        return maquinas.salvarTipo(id, in);
    }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EXCLUIR)
    @PostMapping("/tipos-maquina/{id}/inativar")
    public ResponseEntity<Void> inativarTipo(@PathVariable Long id) { maquinas.ativarTipo(id, false); return ok(); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PostMapping("/tipos-maquina/{id}/reativar")
    public ResponseEntity<Void> reativarTipo(@PathVariable Long id) { maquinas.ativarTipo(id, true); return ok(); }

    // ── Máquinas ─────────────────────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.VER)
    @GetMapping("/maquinas")
    public List<MaquinaOut> maquinas() { return maquinas.listarMaquinas(); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.VER)
    @GetMapping("/maquinas/{id}")
    public MaquinaOut maquina(@PathVariable Long id) { return maquinas.buscarMaquina(id); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.CRIAR)
    @PostMapping("/maquinas")
    public MaquinaOut criarMaquina(@RequestBody @Valid MaquinaIn in) { return maquinas.salvarMaquina(null, in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PutMapping("/maquinas/{id}")
    public MaquinaOut editarMaquina(@PathVariable Long id, @RequestBody @Valid MaquinaIn in) {
        return maquinas.salvarMaquina(id, in, usuario());
    }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PostMapping("/maquinas/{id}/status")
    public MaquinaOut mudarStatus(@PathVariable Long id, @RequestBody @Valid MudarStatusIn in) {
        return maquinas.mudarStatus(id, in, usuario());
    }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PostMapping("/maquinas/{id}/horimetro")
    public MaquinaOut horimetro(@PathVariable Long id, @RequestBody @Valid HorimetroIn in) {
        return maquinas.registrarHorimetro(id, in, usuario());
    }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PostMapping("/maquinas/{id}/manutencoes")
    public EventoOut manutencao(@PathVariable Long id, @RequestBody @Valid ManutencaoIn in) {
        return maquinas.registrarManutencao(id, in, usuario());
    }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.VER)
    @GetMapping("/maquinas/{id}/historico")
    public List<EventoOut> historico(@PathVariable Long id) { return maquinas.historico(id); }

    // ── Ferramentas e matrizes ───────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.VER)
    @GetMapping("/ferramentas")
    public List<FerramentaOut> ferramentas() { return ferramentas.listar(); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.CRIAR)
    @PostMapping("/ferramentas")
    public FerramentaOut criarFerramenta(@RequestBody @Valid FerramentaIn in) { return ferramentas.salvar(null, in); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PutMapping("/ferramentas/{id}")
    public FerramentaOut editarFerramenta(@PathVariable Long id, @RequestBody @Valid FerramentaIn in) {
        return ferramentas.salvar(id, in);
    }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PostMapping("/ferramentas/{id}/status")
    public FerramentaOut statusFerramenta(@PathVariable Long id, @RequestBody @Valid StatusIn in) {
        return ferramentas.mudarStatus(id, in.status());
    }

    // ── Operadores ───────────────────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.VER)
    @GetMapping("/operadores")
    public List<OperadorOut> operadores() { return alocacoes.operadores(); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.VER)
    @GetMapping("/alocacoes")
    public List<AlocacaoOut> alocacoes() { return alocacoes.listar(); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.CRIAR)
    @PostMapping("/alocacoes")
    public AlocacaoOut alocar(@RequestBody @Valid AlocacaoIn in) { return alocacoes.criar(in); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PostMapping("/alocacoes/{id}/encerrar")
    public AlocacaoOut encerrar(@PathVariable Long id, @RequestBody @Valid EncerrarAlocacaoIn in) {
        return alocacoes.encerrar(id, in.vigenteAte());
    }

    private static ResponseEntity<Void> ok() {
        return ResponseEntity.ok().build();
    }
}
