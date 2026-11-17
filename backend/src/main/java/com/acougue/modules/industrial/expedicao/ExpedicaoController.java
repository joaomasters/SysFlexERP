package com.acougue.modules.industrial.expedicao;

import com.acougue.entity.Modulo;
import com.acougue.modules.industrial.expedicao.ExpedicaoDto.*;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.ExigirPermissao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Expedição (módulo IND_EXPEDICAO). Criar: pedido e reservas. Editar: conferência, carregamento e nota.
 * Excluir: cancelar a expedição.
 */
@RestController
@RequestMapping("/industrial/expedicoes")
@RequiredArgsConstructor
public class ExpedicaoController {

    private final ExpedicaoService expedicoes;

    private static Long usuario() {
        return ContextoUsuario.atual().getUsuarioId();
    }

    @ExigirPermissao(modulo = Modulo.IND_EXPEDICAO, acao = Acao.VER)
    @GetMapping("/clientes")
    public List<ClienteOpcao> clientes() { return expedicoes.clientes(); }

    @ExigirPermissao(modulo = Modulo.IND_EXPEDICAO, acao = Acao.VER)
    @GetMapping
    public List<ExpedicaoResumo> listar(@RequestParam(required = false) String status) { return expedicoes.listar(status); }

    @ExigirPermissao(modulo = Modulo.IND_EXPEDICAO, acao = Acao.VER)
    @GetMapping("/{id}")
    public ExpedicaoOut buscar(@PathVariable Long id) { return expedicoes.buscar(id); }

    @ExigirPermissao(modulo = Modulo.IND_EXPEDICAO, acao = Acao.CRIAR)
    @PostMapping
    public ExpedicaoOut criar(@RequestBody @Valid ExpedicaoIn in) { return expedicoes.criar(in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_EXPEDICAO, acao = Acao.EDITAR)
    @PutMapping("/{id}")
    public ExpedicaoOut editar(@PathVariable Long id, @RequestBody @Valid ExpedicaoIn in) { return expedicoes.editar(id, in); }

    @ExigirPermissao(modulo = Modulo.IND_EXPEDICAO, acao = Acao.CRIAR)
    @PostMapping("/{id}/reservas")
    public ExpedicaoOut reservar(@PathVariable Long id, @RequestBody @Valid ReservaIn in) { return expedicoes.reservar(id, in); }

    @ExigirPermissao(modulo = Modulo.IND_EXPEDICAO, acao = Acao.CRIAR)
    @PostMapping("/{id}/reservar-automatico")
    public ExpedicaoOut reservarAutomatico(@PathVariable Long id) { return expedicoes.reservarAutomatico(id); }

    @ExigirPermissao(modulo = Modulo.IND_EXPEDICAO, acao = Acao.CRIAR)
    @DeleteMapping("/{id}/reservas/{reservaId}")
    public ExpedicaoOut removerReserva(@PathVariable Long id, @PathVariable Long reservaId) { return expedicoes.removerReserva(id, reservaId); }

    @ExigirPermissao(modulo = Modulo.IND_EXPEDICAO, acao = Acao.EDITAR)
    @PostMapping("/{id}/conferencia")
    public ExpedicaoOut conferir(@PathVariable Long id, @RequestBody @Valid ConferenciaIn in) { return expedicoes.conferir(id, in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_EXPEDICAO, acao = Acao.EDITAR)
    @PostMapping("/{id}/concluir-conferencia")
    public ExpedicaoOut concluirConferencia(@PathVariable Long id, @RequestBody @Valid VersaoIn in) { return expedicoes.concluirConferencia(id, in); }

    @ExigirPermissao(modulo = Modulo.IND_EXPEDICAO, acao = Acao.EDITAR)
    @PostMapping("/{id}/reabrir")
    public ExpedicaoOut reabrir(@PathVariable Long id, @RequestBody @Valid VersaoIn in) { return expedicoes.reabrir(id, in); }

    @ExigirPermissao(modulo = Modulo.IND_EXPEDICAO, acao = Acao.EDITAR)
    @PostMapping("/{id}/carregar")
    public ExpedicaoOut carregar(@PathVariable Long id, @RequestBody @Valid VersaoIn in) { return expedicoes.carregar(id, in, usuario()); }

    @ExigirPermissao(modulo = Modulo.IND_EXPEDICAO, acao = Acao.EXCLUIR)
    @PostMapping("/{id}/cancelar")
    public ExpedicaoOut cancelar(@PathVariable Long id, @RequestBody @Valid CancelarIn in) { return expedicoes.cancelar(id, in); }

    @ExigirPermissao(modulo = Modulo.IND_EXPEDICAO, acao = Acao.EDITAR)
    @PostMapping("/{id}/nota-fiscal")
    public NotaGerada gerarNota(@PathVariable Long id) { return expedicoes.gerarNotaFiscal(id, usuario()); }
}
