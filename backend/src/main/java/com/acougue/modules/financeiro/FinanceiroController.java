package com.acougue.modules.financeiro;

import com.acougue.entity.Cliente;
import com.acougue.entity.ContasAReceber;
import com.acougue.entity.FaturamentoCliente;
import com.acougue.entity.Modulo;
import com.acougue.modules.financeiro.dto.DreDTO;
import com.acougue.modules.financeiro.dto.HistoricoContaDTO;
import com.acougue.repository.ClienteRepository;
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

@RestController
@RequestMapping("/financeiro")
@RequiredArgsConstructor
public class FinanceiroController {

    private final FaturamentoService  faturamentoService;
    private final HistoricoPagamentosService historicoPagamentosService;
    private final DreService          dreService;
    private final ClienteRepository   clienteRepository;

    @ExigirPermissao(modulo = Modulo.CLIENTES, acao = Acao.VER)
    @GetMapping("/clientes")
    public ResponseEntity<List<Cliente>> listarClientes() {
        // Exclui o cliente genérico "CONSUMIDOR" (VAREJO) — esse endpoint alimenta
        // o seletor de "Gerar Fechamento", que é só pra clientes faturáveis
        // (atacado/restaurante/conveniado), não pra venda de balcão avulsa.
        return ResponseEntity.ok(clienteRepository.findByAtivoTrueAndTipoClienteNot("VAREJO"));
    }

    @ExigirPermissao(modulo = Modulo.FATURAMENTO, acao = Acao.CRIAR)
    @PostMapping("/faturamento/fechar")
    public ResponseEntity<FaturamentoCliente> gerarFechamento(
            @RequestParam Long clienteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(faturamentoService.gerarFechamento(clienteId, inicio, fim));
    }

    @ExigirPermissao(modulo = Modulo.FATURAMENTO, acao = Acao.VER)
    @GetMapping("/faturamento/abertos")
    public ResponseEntity<List<FaturamentoCliente>> listarAbertos() {
        return ResponseEntity.ok(faturamentoService.listarFaturamentosAbertos());
    }

    @ExigirPermissao(modulo = Modulo.CONTAS_RECEBER, acao = Acao.VER)
    @GetMapping("/contas-receber")
    public ResponseEntity<List<ContasAReceber>> listarContasReceber(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(faturamentoService.listarPorStatus(status != null ? status : "ABERTO"));
    }

    @ExigirPermissao(modulo = Modulo.CONTAS_RECEBER, acao = Acao.VER)
    @GetMapping("/contas-receber/cliente/{clienteId}")
    public ResponseEntity<List<ContasAReceber>> contasCliente(@PathVariable Long clienteId) {
        return ResponseEntity.ok(faturamentoService.listarContasCliente(clienteId));
    }

    @ExigirPermissao(modulo = Modulo.CONTAS_RECEBER, acao = Acao.VER)
    @GetMapping("/contas-receber/saldo/{clienteId}")
    public ResponseEntity<BigDecimal> saldoCliente(@PathVariable Long clienteId) {
        return ResponseEntity.ok(faturamentoService.saldoAbertoCliente(clienteId));
    }

    @ExigirPermissao(modulo = Modulo.CONTAS_RECEBER, acao = Acao.EDITAR)
    @PostMapping("/contas-receber/{contaId}/pagar")
    public ResponseEntity<ContasAReceber> registrarPagamento(
            @PathVariable Long contaId,
            @RequestParam BigDecimal valor) {
        // Quem recebeu é SEMPRE o usuário logado (token), nunca um valor vindo do cliente.
        return ResponseEntity.ok(faturamentoService.registrarPagamento(
                contaId, valor, ContextoUsuario.atual().getUsuarioId()));
    }

    /**
     * Histórico detalhado de recebimentos (data, valor, usuário, saldo antes/depois),
     * agrupado por conta. Restrito a administradores (checado no service).
     * Filtros opcionais: cliente, conta e período.
     */
    @ExigirPermissao(modulo = Modulo.CONTAS_RECEBER, acao = Acao.VER)
    @GetMapping("/contas-receber/historico")
    public ResponseEntity<List<HistoricoContaDTO>> historicoRecebimentos(
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long contaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(historicoPagamentosService.historicoRecebimentos(clienteId, contaId, inicio, fim));
    }

    @ExigirPermissao(modulo = Modulo.DRE, acao = Acao.VER)
    @GetMapping("/dre")
    public ResponseEntity<DreDTO> calcularDre(
            @RequestParam int ano,
            @RequestParam int mes,
            @RequestParam(defaultValue = "0") BigDecimal custosOperacionais) {
        return ResponseEntity.ok(dreService.calcular(ano, mes, custosOperacionais));
    }
}