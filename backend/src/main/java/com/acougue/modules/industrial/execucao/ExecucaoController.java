package com.acougue.modules.industrial.execucao;

import com.acougue.entity.Modulo;
import com.acougue.modules.industrial.execucao.ExecucaoDto.*;
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
 * Execução da produção (módulo IND_APONTAMENTO).
 * Ver: fila e andamento. Criar: apontar, pausar, finalizar, parar máquina, consumir.
 * Editar: mexer no apontamento de outro operador. Excluir: estornar apontamento e consumo.
 * Motivos de parada são cadastro da estrutura (IND_ESTRUTURA).
 */
@RestController
@RequestMapping("/industrial")
@RequiredArgsConstructor
public class ExecucaoController {

    private final ApontamentoService apontamentos;
    private final ConsumoService consumos;
    private final ParadaService paradas;
    private final ExecucaoConsultaService consultas;

    private static Long usuario() {
        return ContextoUsuario.atual().getUsuarioId();
    }

    private static boolean supervisor() {
        return ContextoUsuario.atual().pode(Modulo.IND_APONTAMENTO, Acao.EDITAR);
    }

    private static void exigirUmDe(Modulo a, Modulo b) {
        var u = ContextoUsuario.atual();
        if (!u.pode(a, Acao.VER) && !u.pode(b, Acao.VER)) {
            throw new AccessDeniedException("Seu perfil não tem acesso a esta consulta.");
        }
    }

    // ── Chão de fábrica ──────────────────────────────────────────────────
    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.VER)
    @GetMapping("/chao/recursos")
    public ChaoRecursos recursos() { return consultas.recursos(); }

    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.VER)
    @GetMapping("/chao/fila")
    public List<FilaItem> fila(@RequestParam Long centroId) { return consultas.fila(centroId); }

    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.VER)
    @GetMapping("/chao/meu-apontamento")
    public ResponseEntity<ApontamentoOut> meuApontamento() {
        return consultas.meuApontamento(usuario()).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.CRIAR)
    @PostMapping("/apontamentos")
    public ApontamentoOut iniciar(@RequestBody @Valid IniciarIn in) { return apontamentos.iniciar(in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.CRIAR)
    @PostMapping("/apontamentos/{id}/pausar")
    public ApontamentoOut pausar(@PathVariable Long id, @RequestBody @Valid PausarIn in) {
        return apontamentos.pausar(id, in, usuario(), supervisor());
    }

    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.CRIAR)
    @PostMapping("/apontamentos/{id}/retomar")
    public ApontamentoOut retomar(@PathVariable Long id) { return apontamentos.retomar(id, usuario(), supervisor()); }

    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.CRIAR)
    @PostMapping("/apontamentos/{id}/finalizar")
    public ApontamentoOut finalizar(@PathVariable Long id, @RequestBody @Valid FinalizarIn in) {
        return apontamentos.finalizar(id, in, usuario(), supervisor());
    }

    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.EXCLUIR)
    @PostMapping("/apontamentos/{id}/estornar")
    public ApontamentoOut estornarApontamento(@PathVariable Long id, @RequestBody @Valid EstornoIn in) {
        return apontamentos.estornar(id, in, usuario());
    }

    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.CRIAR)
    @PostMapping("/paradas")
    public ParadaOut parar(@RequestBody @Valid ParadaIn in) { return paradas.parar(in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.CRIAR)
    @PostMapping("/paradas/{id}/encerrar")
    public ParadaOut encerrarParada(@PathVariable Long id) { return paradas.encerrar(id, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.VER)
    @GetMapping("/paradas")
    public List<ParadaOut> listarParadas(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                         @RequestParam(required = false) Long maquinaId) {
        return paradas.listar(de, ate, maquinaId);
    }

    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.CRIAR)
    @PostMapping("/ordens/{id}/consumos")
    public ConsumoOut consumir(@PathVariable Long id, @RequestBody @Valid ConsumoIn in) { return consumos.consumir(id, in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_APONTAMENTO, acao = Acao.EXCLUIR)
    @PostMapping("/consumos/{id}/estornar")
    public ConsumoOut estornarConsumo(@PathVariable Long id, @RequestBody @Valid EstornoIn in) {
        return consumos.estornar(id, in, usuario());
    }

    /** Andamento da OP: para o operador (chão) e para o planejador (PCP). */
    @GetMapping("/ordens/{id}/execucao")
    public ExecucaoOut execucao(@PathVariable Long id) {
        exigirUmDe(Modulo.IND_APONTAMENTO, Modulo.IND_PCP);
        return consultas.execucao(id);
    }

    // ── Motivos de parada (cadastro) ─────────────────────────────────────
    @GetMapping("/motivos-parada")
    public List<MotivoOut> motivos() {
        exigirUmDe(Modulo.IND_ESTRUTURA, Modulo.IND_APONTAMENTO);
        return paradas.motivos();
    }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.CRIAR)
    @PostMapping("/motivos-parada")
    public MotivoOut criarMotivo(@RequestBody @Valid MotivoIn in) { return paradas.salvarMotivo(null, in); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PutMapping("/motivos-parada/{id}")
    public MotivoOut editarMotivo(@PathVariable Long id, @RequestBody @Valid MotivoIn in) { return paradas.salvarMotivo(id, in); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EXCLUIR)
    @PostMapping("/motivos-parada/{id}/inativar")
    public ResponseEntity<Void> inativarMotivo(@PathVariable Long id) { paradas.ativarMotivo(id, false); return ResponseEntity.ok().build(); }

    @ExigirPermissao(modulo = Modulo.IND_ESTRUTURA, acao = Acao.EDITAR)
    @PostMapping("/motivos-parada/{id}/reativar")
    public ResponseEntity<Void> reativarMotivo(@PathVariable Long id) { paradas.ativarMotivo(id, true); return ResponseEntity.ok().build(); }
}
