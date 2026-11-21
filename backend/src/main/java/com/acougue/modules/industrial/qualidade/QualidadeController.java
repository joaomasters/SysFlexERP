package com.acougue.modules.industrial.qualidade;

import com.acougue.entity.Modulo;
import com.acougue.modules.industrial.qualidade.QualidadeDto.*;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.ExigirPermissao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Qualidade e rastreabilidade (módulo IND_QUALIDADE). Lotes, planos de inspeção, inspeções e não conformidades.
 * A consulta de lotes e a rastreabilidade também abrem para quem vê a expedição ou o PCP.
 */
@RestController
@RequestMapping("/industrial")
@RequiredArgsConstructor
public class QualidadeController {

    private final LoteService lotes;
    private final PlanoInspecaoService planos;
    private final InspecaoService inspecoes;
    private final NaoConformidadeService ncs;
    private final RastreabilidadeService rastreio;

    private static Long usuario() {
        return ContextoUsuario.atual().getUsuarioId();
    }

    private static void exigirConsulta() {
        var u = ContextoUsuario.atual();
        if (!u.pode(Modulo.IND_QUALIDADE, Acao.VER) && !u.pode(Modulo.IND_EXPEDICAO, Acao.VER) && !u.pode(Modulo.IND_PCP, Acao.VER)) {
            throw new AccessDeniedException("Seu perfil não tem acesso à qualidade, à expedição nem ao PCP.");
        }
    }

    // ── Lotes ────────────────────────────────────────────────────────────
    @GetMapping("/lotes")
    public List<LoteOut> lotes(@RequestParam(required = false) String situacao, @RequestParam(required = false) Long produtoId) {
        exigirConsulta();
        return lotes.listar(situacao, produtoId);
    }

    @GetMapping("/lotes/{id}/etiqueta")
    public EtiquetaOut etiqueta(@PathVariable Long id) {
        exigirConsulta();
        return rastreio.etiqueta(id);
    }

    @ExigirPermissao(modulo = Modulo.IND_QUALIDADE, acao = Acao.EDITAR)
    @PostMapping("/lotes/{id}/situacao")
    public LoteOut situacao(@PathVariable Long id, @RequestBody @Valid SituacaoIn in) { return lotes.mudarSituacao(id, in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_QUALIDADE, acao = Acao.CRIAR)
    @PostMapping("/lotes/{id}/dividir")
    public List<LoteOut> dividir(@PathVariable Long id, @RequestBody @Valid DivisaoIn in) { return lotes.dividir(id, in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_QUALIDADE, acao = Acao.CRIAR)
    @PostMapping("/lotes/unir")
    public LoteOut unir(@RequestBody @Valid UniaoIn in) { return lotes.unir(in, usuario()); }

    // ── Planos de inspeção ───────────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_QUALIDADE, acao = Acao.VER)
    @GetMapping("/qualidade/planos")
    public List<PlanoOut> planos() { return planos.listar(); }

    @ExigirPermissao(modulo = Modulo.IND_QUALIDADE, acao = Acao.CRIAR)
    @PostMapping("/qualidade/planos")
    public PlanoOut criarPlano(@RequestBody @Valid PlanoIn in) { return planos.salvar(null, in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_QUALIDADE, acao = Acao.EDITAR)
    @PutMapping("/qualidade/planos/{id}")
    public PlanoOut editarPlano(@PathVariable Long id, @RequestBody @Valid PlanoIn in) { return planos.salvar(id, in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_QUALIDADE, acao = Acao.EXCLUIR)
    @PostMapping("/qualidade/planos/{id}/inativar")
    public ResponseEntity<Void> inativarPlano(@PathVariable Long id) { planos.ativar(id, false); return ResponseEntity.ok().build(); }

    @ExigirPermissao(modulo = Modulo.IND_QUALIDADE, acao = Acao.EDITAR)
    @PostMapping("/qualidade/planos/{id}/reativar")
    public ResponseEntity<Void> reativarPlano(@PathVariable Long id) { planos.ativar(id, true); return ResponseEntity.ok().build(); }

    // ── Inspeções ────────────────────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_QUALIDADE, acao = Acao.CRIAR)
    @PostMapping("/qualidade/inspecoes")
    public InspecaoOut inspecionar(@RequestBody @Valid InspecaoIn in) { return inspecoes.registrar(in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_QUALIDADE, acao = Acao.VER)
    @GetMapping("/lotes/{id}/inspecoes")
    public List<InspecaoOut> inspecoesDoLote(@PathVariable Long id) { return inspecoes.doLote(id); }

    // ── Não conformidades ────────────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_QUALIDADE, acao = Acao.VER)
    @GetMapping("/qualidade/nao-conformidades")
    public List<NcOut> ncs(@RequestParam(required = false) String status) { return ncs.listar(status); }

    @ExigirPermissao(modulo = Modulo.IND_QUALIDADE, acao = Acao.CRIAR)
    @PostMapping("/qualidade/nao-conformidades")
    public NcOut abrirNc(@RequestBody @Valid NcIn in) { return ncs.abrir(in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_QUALIDADE, acao = Acao.EDITAR)
    @PostMapping("/qualidade/nao-conformidades/{id}/encerrar")
    public NcOut encerrarNc(@PathVariable Long id, @RequestBody @Valid EncerrarNcIn in) { return ncs.encerrar(id, in, usuario()); }

    // ── Rastreabilidade ──────────────────────────────────────────────────
    @GetMapping("/rastreabilidade/busca")
    public List<ResultadoBusca> buscar(@RequestParam String texto) {
        exigirConsulta();
        return rastreio.buscar(texto);
    }

    @GetMapping("/rastreabilidade/lote/{codigo}")
    public RastreioLote porLote(@PathVariable String codigo) {
        exigirConsulta();
        return rastreio.porLote(codigo);
    }

    @GetMapping("/rastreabilidade/lote-mp/{id}")
    public RastreioLoteMp porLoteMp(@PathVariable Long id) {
        exigirConsulta();
        return rastreio.porLoteMp(id);
    }
}
