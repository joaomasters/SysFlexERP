package com.acougue.modules.industrial.engenharia;

import com.acougue.entity.Modulo;
import com.acougue.modules.industrial.engenharia.EngenhariaDto.*;
import com.acougue.modules.industrial.estrutura.CentroTrabalhoRepository;
import com.acougue.modules.industrial.estrutura.TipoMaquinaRepository;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.ExigirPermissao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Engenharia de produto (módulo IND_ENGENHARIA): ficha técnica, estrutura (BOM) e roteiro.
 * Ativar e tornar obsoleto pedem permissão de editar; excluir rascunho pede permissão de excluir.
 */
@RestController
@RequestMapping("/industrial")
@RequiredArgsConstructor
public class EngenhariaController {

    private final FichaTecnicaService fichas;
    private final BomService boms;
    private final RoteiroService roteiros;
    private final CentroTrabalhoRepository centros;
    private final TipoMaquinaRepository tipos;

    private static Long usuario() {
        return ContextoUsuario.atual().getUsuarioId();
    }

    /** Produtos ativos para as telas industriais: basta ver a engenharia ou o PCP (não exige o módulo Produtos). */
    @GetMapping("/produtos")
    public List<ProdutoOpcao> produtos() {
        var u = ContextoUsuario.atual();
        if (!u.pode(Modulo.IND_ENGENHARIA, Acao.VER) && !u.pode(Modulo.IND_PCP, Acao.VER)) {
            throw new AccessDeniedException("Seu perfil não tem acesso à engenharia nem ao PCP.");
        }
        return fichas.produtos();
    }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.VER)
    @GetMapping("/engenharia/recursos")
    public Recursos recursos() {
        return new Recursos(
                centros.listarTodos().stream().filter(c -> Boolean.TRUE.equals(c.getAtivo())).map(c -> new Recurso(c.getId(), c.getCodigo(), c.getNome())).toList(),
                tipos.listarTodos().stream().filter(t -> Boolean.TRUE.equals(t.getAtivo())).map(t -> new Recurso(t.getId(), t.getCodigo(), t.getNome())).toList());
    }

    // ── Ficha técnica ────────────────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.VER)
    @GetMapping("/engenharia/fichas")
    public List<FichaOut> fichas() { return fichas.listar(); }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.EDITAR)
    @PutMapping("/engenharia/fichas/{produtoId}")
    public FichaOut salvarFicha(@PathVariable Long produtoId, @RequestBody @Valid FichaIn in) {
        return fichas.salvar(produtoId, in);
    }

    // ── Estrutura de produto (BOM) ───────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.VER)
    @GetMapping("/engenharia/boms")
    public List<BomResumo> boms(@RequestParam(required = false) Long produtoId) { return boms.listar(produtoId); }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.VER)
    @GetMapping("/engenharia/boms/{id}")
    public BomOut bom(@PathVariable Long id) { return boms.buscar(id); }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.VER)
    @GetMapping("/engenharia/boms/{id}/explosao")
    public List<ExplosaoLinha> explosao(@PathVariable Long id, @RequestParam(required = false) BigDecimal quantidade) {
        return boms.explosao(id, quantidade);
    }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.CRIAR)
    @PostMapping("/engenharia/boms")
    public BomOut criarBom(@RequestBody @Valid BomIn in) { return boms.criar(in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.EDITAR)
    @PutMapping("/engenharia/boms/{id}")
    public BomOut editarBom(@PathVariable Long id, @RequestBody @Valid BomIn in) { return boms.editar(id, in); }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.EXCLUIR)
    @DeleteMapping("/engenharia/boms/{id}")
    public ResponseEntity<Void> excluirBom(@PathVariable Long id) { boms.excluir(id); return ResponseEntity.noContent().build(); }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.EDITAR)
    @PostMapping("/engenharia/boms/{id}/ativar")
    public BomOut ativarBom(@PathVariable Long id) { return boms.ativar(id, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.EDITAR)
    @PostMapping("/engenharia/boms/{id}/obsoletar")
    public BomOut obsoletarBom(@PathVariable Long id) { return boms.obsoletar(id); }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.CRIAR)
    @PostMapping("/engenharia/boms/{id}/nova-versao")
    public BomOut novaVersaoBom(@PathVariable Long id) { return boms.novaVersao(id, usuario()); }

    // ── Roteiro ──────────────────────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.VER)
    @GetMapping("/engenharia/roteiros")
    public List<RoteiroResumo> roteiros(@RequestParam(required = false) Long produtoId) { return roteiros.listar(produtoId); }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.VER)
    @GetMapping("/engenharia/roteiros/{id}")
    public RoteiroOut roteiro(@PathVariable Long id) { return roteiros.buscar(id); }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.CRIAR)
    @PostMapping("/engenharia/roteiros")
    public RoteiroOut criarRoteiro(@RequestBody @Valid RoteiroIn in) { return roteiros.criar(in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.EDITAR)
    @PutMapping("/engenharia/roteiros/{id}")
    public RoteiroOut editarRoteiro(@PathVariable Long id, @RequestBody @Valid RoteiroIn in) { return roteiros.editar(id, in); }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.EXCLUIR)
    @DeleteMapping("/engenharia/roteiros/{id}")
    public ResponseEntity<Void> excluirRoteiro(@PathVariable Long id) { roteiros.excluir(id); return ResponseEntity.noContent().build(); }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.EDITAR)
    @PostMapping("/engenharia/roteiros/{id}/ativar")
    public RoteiroOut ativarRoteiro(@PathVariable Long id) { return roteiros.ativar(id, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.EDITAR)
    @PostMapping("/engenharia/roteiros/{id}/obsoletar")
    public RoteiroOut obsoletarRoteiro(@PathVariable Long id) { return roteiros.obsoletar(id); }

    @ExigirPermissao(modulo = Modulo.IND_ENGENHARIA, acao = Acao.CRIAR)
    @PostMapping("/engenharia/roteiros/{id}/nova-versao")
    public RoteiroOut novaVersaoRoteiro(@PathVariable Long id) { return roteiros.novaVersao(id, usuario()); }
}
