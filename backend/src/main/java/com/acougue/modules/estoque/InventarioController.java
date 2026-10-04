package com.acougue.modules.estoque;

import com.acougue.entity.InventarioFisico;
import com.acougue.entity.InventarioFisicoItem;
import com.acougue.entity.Modulo;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.ExigirPermissao;
import com.acougue.security.IdentificacaoUsuario;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/estoque/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final InventarioService inventarioService;
    private final IdentificacaoUsuario identificacao;

    @ExigirPermissao(modulo = Modulo.INVENTARIO, acao = Acao.CRIAR)
    @PostMapping("/abrir")
    public ResponseEntity<InventarioFisico> abrir(@RequestBody AbrirInventarioRequest req) {
        // Quem abriu é SEMPRE o usuário logado — o corpo da requisição não define autoria.
        return ResponseEntity.ok(inventarioService.abrirInventario(
                ContextoUsuario.atual().getUsuarioId(), req.getObservacao()));
    }

    @ExigirPermissao(modulo = Modulo.INVENTARIO, acao = Acao.VER)
    @GetMapping
    public ResponseEntity<List<InventarioFisico>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        List<InventarioFisico> inventarios = (inicio == null || fim == null)
                ? inventarioService.listarTodos()
                : inventarioService.listarPorPeriodo(inicio.atStartOfDay(), fim.atTime(LocalTime.MAX));
        preencherNomes(inventarios);
        return ResponseEntity.ok(inventarios);
    }

    @ExigirPermissao(modulo = Modulo.INVENTARIO, acao = Acao.VER)
    @GetMapping("/{id}")
    public ResponseEntity<InventarioFisico> buscar(@PathVariable Long id) {
        InventarioFisico inventario = inventarioService.buscarPorId(id);
        preencherNomes(List.of(inventario));
        return ResponseEntity.ok(inventario);
    }

    @ExigirPermissao(modulo = Modulo.INVENTARIO, acao = Acao.VER)
    @GetMapping("/{id}/itens")
    public ResponseEntity<List<InventarioFisicoItem>> itens(@PathVariable Long id) {
        return ResponseEntity.ok(inventarioService.listarItens(id));
    }

    @ExigirPermissao(modulo = Modulo.INVENTARIO, acao = Acao.EDITAR)
    @PatchMapping("/{inventarioId}/itens/{produtoId}")
    public ResponseEntity<InventarioFisicoItem> contar(
            @PathVariable Long inventarioId,
            @PathVariable Long produtoId,
            @RequestBody Map<String, BigDecimal> body) {
        return ResponseEntity.ok(inventarioService.contarItem(inventarioId, produtoId, body.get("saldoContado")));
    }

    @ExigirPermissao(modulo = Modulo.INVENTARIO, acao = Acao.EDITAR)
    @PostMapping("/{id}/finalizar")
    public ResponseEntity<InventarioFisico> finalizar(@PathVariable Long id) {
        return ResponseEntity.ok(inventarioService.finalizarInventario(id, ContextoUsuario.atual().getUsuarioId()));
    }

    @ExigirPermissao(modulo = Modulo.INVENTARIO, acao = Acao.EXCLUIR)
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<InventarioFisico> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(inventarioService.cancelarInventario(id, ContextoUsuario.atual().getUsuarioId()));
    }

    // Só administradores recebem os nomes (quem abriu / quem fechou) — ver IdentificacaoUsuario.
    private void preencherNomes(List<InventarioFisico> inventarios) {
        identificacao.preencher(inventarios, InventarioFisico::getUsuarioId, InventarioFisico::setUsuarioNome);
        identificacao.preencher(inventarios, InventarioFisico::getFechadoPorId, InventarioFisico::setFechadoPorNome);
    }

    @Data
    static class AbrirInventarioRequest {
        // usuarioId não existe mais aqui: a autoria vem do token. Se um cliente antigo
        // ainda enviar o campo, o Jackson o ignora (FAIL_ON_UNKNOWN_PROPERTIES desligado no Spring Boot).
        private String observacao;
    }
}