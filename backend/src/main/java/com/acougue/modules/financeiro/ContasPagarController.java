package com.acougue.modules.financeiro;

import com.acougue.entity.ContasPagar;
import com.acougue.entity.Modulo;
import com.acougue.modules.financeiro.dto.ContasPagarDTO;
import com.acougue.modules.financeiro.dto.HistoricoContaDTO;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.ExigirPermissao;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/financeiro/contas-pagar")
@RequiredArgsConstructor
public class ContasPagarController {

    private final ContasPagarService contasPagarService;
    private final HistoricoPagamentosService historicoPagamentosService;

    @ExigirPermissao(modulo = Modulo.CONTAS_PAGAR, acao = Acao.CRIAR)
    @PostMapping
    public ResponseEntity<ContasPagar> criar(@RequestBody ContasPagarDTO dto) {
        return ResponseEntity.ok(contasPagarService.criar(dto));
    }

    @ExigirPermissao(modulo = Modulo.CONTAS_PAGAR, acao = Acao.VER)
    @GetMapping
    public ResponseEntity<List<ContasPagar>> listar(
            @RequestParam(required = false) String status) {
        if (status != null) return ResponseEntity.ok(contasPagarService.listarPorStatus(status));
        return ResponseEntity.ok(contasPagarService.listarPorStatus("ABERTO"));
    }

    @ExigirPermissao(modulo = Modulo.CONTAS_PAGAR, acao = Acao.VER)
    @GetMapping("/vencidas")
    public ResponseEntity<List<ContasPagar>> vencidas() {
        return ResponseEntity.ok(contasPagarService.listarVencidas());
    }

    @ExigirPermissao(modulo = Modulo.CONTAS_PAGAR, acao = Acao.VER)
    @GetMapping("/periodo")
    public ResponseEntity<List<ContasPagar>> porPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(contasPagarService.listarPorPeriodo(inicio, fim));
    }

    @ExigirPermissao(modulo = Modulo.CONTAS_PAGAR, acao = Acao.EDITAR)
    @PostMapping("/{id}/pagar")
    public ResponseEntity<ContasPagar> pagar(
            @PathVariable Long id,
            @RequestBody Map<String, BigDecimal> body) {
        // Quem pagou é SEMPRE o usuário logado (token), nunca um valor vindo do cliente.
        return ResponseEntity.ok(contasPagarService.pagar(
                id, body.get("valor"), ContextoUsuario.atual().getUsuarioId()));
    }

    /**
     * Histórico detalhado de pagamentos (data, valor, usuário, saldo antes/depois),
     * agrupado por conta. Restrito a administradores (checado no service).
     * Filtros opcionais: conta, fornecedor (busca parcial) e período.
     */
    @ExigirPermissao(modulo = Modulo.CONTAS_PAGAR, acao = Acao.VER)
    @GetMapping("/historico")
    public ResponseEntity<List<HistoricoContaDTO>> historicoPagamentos(
            @RequestParam(required = false) Long contaId,
            @RequestParam(required = false) String fornecedor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(historicoPagamentosService.historicoPagamentos(contaId, fornecedor, inicio, fim));
    }

    @ExigirPermissao(modulo = Modulo.CONTAS_PAGAR, acao = Acao.EXCLUIR)
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        contasPagarService.cancelar(id);
        return ResponseEntity.noContent().build();
    }
}