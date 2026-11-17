package com.acougue.modules.industrial.extrusao;

import com.acougue.entity.Modulo;
import com.acougue.modules.industrial.extrusao.ExtrusaoDto.*;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.ExigirPermissao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Extrusão. Cadastros (ligas, lotes de matéria-prima, receitas) no módulo IND_EXTRUSAO; medições e perdas são
 * registradas no chão de fábrica (IND_APONTAMENTO). Nada aqui envia comando a equipamento.
 */
@RestController
@RequestMapping("/industrial")
@RequiredArgsConstructor
public class ExtrusaoController {

    private final LigaService ligas;
    private final LoteMpService lotes;
    private final ReceitaService receitas;
    private final MedicaoService medicoes;
    private final PerdaService perdas;
    private final com.acougue.modules.industrial.estrutura.TipoMaquinaRepository tipos;
    private final com.acougue.repository.FornecedorRepository fornecedores;

    public record Opcao(Long id, String nome) {}

    public record Recursos(List<Opcao> tiposMaquina, List<Opcao> fornecedores) {}

    private static Long usuario() {
        return ContextoUsuario.atual().getUsuarioId();
    }

    private static void exigirUmDe(Modulo... modulos) {
        var u = ContextoUsuario.atual();
        for (Modulo m : modulos) if (u.pode(m, Acao.VER)) return;
        throw new AccessDeniedException("Seu perfil não tem acesso a esta consulta.");
    }

    /** Tipos de máquina e fornecedores ativos para os formulários da extrusão. */
    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.VER)
    @GetMapping("/extrusao/recursos")
    public Recursos recursos() {
        return new Recursos(
                tipos.listarTodos().stream().filter(t -> Boolean.TRUE.equals(t.getAtivo())).map(t -> new Opcao(t.getId(), t.getNome())).toList(),
                fornecedores.findByAtivoTrueOrderByNomeAsc().stream().map(f -> new Opcao(f.getId(), f.getNome())).toList());
    }

    // ── Ligas ────────────────────────────────────────────────────────────
    @GetMapping("/extrusao/ligas")
    public List<LigaOut> ligas() {
        exigirUmDe(Modulo.IND_EXTRUSAO, Modulo.IND_ENGENHARIA);
        return ligas.listar();
    }

    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.CRIAR)
    @PostMapping("/extrusao/ligas")
    public LigaOut criarLiga(@RequestBody @Valid LigaIn in) { return ligas.salvar(null, in); }

    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.EDITAR)
    @PutMapping("/extrusao/ligas/{id}")
    public LigaOut editarLiga(@PathVariable Long id, @RequestBody @Valid LigaIn in) { return ligas.salvar(id, in); }

    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.EXCLUIR)
    @PostMapping("/extrusao/ligas/{id}/inativar")
    public ResponseEntity<Void> inativarLiga(@PathVariable Long id) { ligas.ativar(id, false); return ResponseEntity.ok().build(); }

    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.EDITAR)
    @PostMapping("/extrusao/ligas/{id}/reativar")
    public ResponseEntity<Void> reativarLiga(@PathVariable Long id) { ligas.ativar(id, true); return ResponseEntity.ok().build(); }

    // ── Lotes de matéria-prima ───────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.VER)
    @GetMapping("/extrusao/lotes")
    public List<LoteMpOut> lotes(@RequestParam(required = false) Long produtoId, @RequestParam(required = false) String status) {
        return lotes.listar(produtoId, status);
    }

    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.VER)
    @GetMapping("/extrusao/lotes/{id}")
    public LoteMpOut lote(@PathVariable Long id) { return lotes.buscar(id); }

    /** Lotes liberados com saldo de um material (para o consumo no chão de fábrica). */
    @GetMapping("/extrusao/lotes-disponiveis")
    public List<LoteDisponivel> lotesDisponiveis(@RequestParam Long produtoId) {
        exigirUmDe(Modulo.IND_APONTAMENTO, Modulo.IND_EXTRUSAO);
        return lotes.disponiveis(produtoId);
    }

    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.CRIAR)
    @PostMapping("/extrusao/lotes")
    public LoteMpOut criarLote(@RequestBody @Valid LoteMpIn in) { return lotes.salvar(null, in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.EDITAR)
    @PutMapping("/extrusao/lotes/{id}")
    public LoteMpOut editarLote(@PathVariable Long id, @RequestBody @Valid LoteMpIn in) { return lotes.salvar(id, in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.EDITAR)
    @PostMapping("/extrusao/lotes/{id}/status")
    public LoteMpOut statusLote(@PathVariable Long id, @RequestBody @Valid StatusLoteIn in) { return lotes.mudarStatus(id, in, usuario()); }

    // ── Receitas ─────────────────────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.VER)
    @GetMapping("/extrusao/receitas")
    public List<ReceitaOut> receitas(@RequestParam(required = false) Long tipoMaquinaId, @RequestParam(required = false) Long ligaId) {
        return receitas.listar(tipoMaquinaId, ligaId);
    }

    /** Receitas aplicáveis a uma OP numa máquina (liga do produto + tipo da máquina). */
    @GetMapping("/extrusao/receitas-da-op")
    public List<ReceitaOut> receitasDaOp(@RequestParam Long ordemId, @RequestParam(required = false) Long maquinaId) {
        exigirUmDe(Modulo.IND_APONTAMENTO, Modulo.IND_EXTRUSAO);
        return medicoes.receitasPara(ordemId, maquinaId);
    }

    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.CRIAR)
    @PostMapping("/extrusao/receitas")
    public ReceitaOut criarReceita(@RequestBody @Valid ReceitaIn in) { return receitas.salvar(null, in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.EDITAR)
    @PutMapping("/extrusao/receitas/{id}")
    public ReceitaOut editarReceita(@PathVariable Long id, @RequestBody @Valid ReceitaIn in) { return receitas.salvar(id, in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.EXCLUIR)
    @PostMapping("/extrusao/receitas/{id}/inativar")
    public ResponseEntity<Void> inativarReceita(@PathVariable Long id) { receitas.ativar(id, false); return ResponseEntity.ok().build(); }

    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.EDITAR)
    @PostMapping("/extrusao/receitas/{id}/reativar")
    public ResponseEntity<Void> reativarReceita(@PathVariable Long id) { receitas.ativar(id, true); return ResponseEntity.ok().build(); }

    // ── Medições e perdas (chão de fábrica) ──────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.CRIAR)
    @PostMapping("/medicoes")
    public MedicaoOut medir(@RequestBody @Valid MedicaoIn in) { return medicoes.registrar(in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_EXTRUSAO, acao = Acao.VER)
    @GetMapping("/medicoes")
    public List<MedicaoOut> listarMedicoes(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                           @RequestParam(defaultValue = "false") boolean somenteFora) {
        return medicoes.noPeriodo(de, ate, somenteFora);
    }

    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.CRIAR)
    @PostMapping("/perdas")
    public PerdaOut registrarPerda(@RequestBody @Valid PerdaIn in) { return perdas.registrar(in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.EXCLUIR)
    @PostMapping("/perdas/{id}/estornar")
    public PerdaOut estornarPerda(@PathVariable Long id, @RequestBody @Valid EstornoPerdaIn in) { return perdas.estornar(id, in, usuario()); }

    /** Medições, perdas, lotes consumidos e rendimento metálico da OP. */
    @GetMapping("/ordens/{id}/metalurgia")
    public MetalurgiaOP metalurgia(@PathVariable Long id) {
        exigirUmDe(Modulo.IND_APONTAMENTO, Modulo.IND_PCP, Modulo.IND_EXTRUSAO);
        return perdas.metalurgia(id);
    }
}
