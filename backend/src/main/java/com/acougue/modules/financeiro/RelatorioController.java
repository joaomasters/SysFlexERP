package com.acougue.modules.financeiro;

import com.acougue.entity.Modulo;
import com.acougue.modules.financeiro.dto.*;
import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/financeiro/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

    private final RelatorioService relatorioService;
    private final IndicadoresService indicadores;

    @ExigirPermissao(modulo = Modulo.RELATORIOS, acao = Acao.VER)
    @GetMapping("/vendas")
    public ResponseEntity<RelatorioVendasDTO> vendas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(relatorioService.relatorioVendas(
                inicio.atStartOfDay(), fim.atTime(LocalTime.MAX)));
    }

    @ExigirPermissao(modulo = Modulo.RELATORIOS, acao = Acao.VER)
    @GetMapping("/fluxo-caixa")
    public ResponseEntity<FluxoCaixaDTO> fluxoCaixa(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(relatorioService.fluxoCaixa(inicio, fim));
    }

    @ExigirPermissao(modulo = Modulo.RELATORIOS, acao = Acao.VER)
    @GetMapping("/perdas")
    public ResponseEntity<RelatorioPerdasDTO> perdas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(relatorioService.relatorioPerdas(
                inicio.atStartOfDay(), fim.atTime(LocalTime.MAX)));
    }

    @ExigirPermissao(modulo = Modulo.RELATORIOS, acao = Acao.VER)
    @GetMapping("/estoque")
    public ResponseEntity<RelatorioEstoqueDTO> estoque() {
        return ResponseEntity.ok(relatorioService.relatorioEstoque());
    }

    @ExigirPermissao(modulo = Modulo.RELATORIOS, acao = Acao.VER)
    @GetMapping("/contas-a-pagar")
    public ResponseEntity<RelatorioContasPagarDTO> contasPagar() {
        return ResponseEntity.ok(relatorioService.relatorioContasPagar(LocalDate.now()));
    }

    // ── Indicadores (IndicadoresService) ───────────────────────

    @ExigirPermissao(modulo = Modulo.RELATORIOS, acao = Acao.VER)
    @GetMapping("/horarios")
    public ResponseEntity<IndicadoresDTO.Horarios> horarios(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(indicadores.horarios(inicio.atStartOfDay(), fim.atTime(LocalTime.MAX)));
    }

    @ExigirPermissao(modulo = Modulo.RELATORIOS, acao = Acao.VER)
    @GetMapping("/curva-abc")
    public ResponseEntity<List<IndicadoresDTO.ProdutoAbc>> curvaAbc(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(indicadores.curvaAbc(inicio.atStartOfDay(), fim.atTime(LocalTime.MAX)));
    }

    @ExigirPermissao(modulo = Modulo.RELATORIOS, acao = Acao.VER)
    @GetMapping("/margem-tipo")
    public ResponseEntity<List<IndicadoresDTO.MargemTipo>> margemPorTipo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(indicadores.margemPorTipo(inicio.atStartOfDay(), fim.atTime(LocalTime.MAX)));
    }

    /** Nomes dos operadores: só para quem vê a identificação dos usuários (checado no serviço). */
    @ExigirPermissao(modulo = Modulo.RELATORIOS, acao = Acao.VER)
    @GetMapping("/operadores")
    public ResponseEntity<List<IndicadoresDTO.Operador>> operadores(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(indicadores.operadores(inicio.atStartOfDay(), fim.atTime(LocalTime.MAX)));
    }

    @ExigirPermissao(modulo = Modulo.RELATORIOS, acao = Acao.VER)
    @GetMapping("/fechamentos-caixa")
    public ResponseEntity<List<IndicadoresDTO.FechamentoCaixa>> fechamentosCaixa(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(indicadores.fechamentosCaixa(inicio.atStartOfDay(), fim.atTime(LocalTime.MAX)));
    }

    @ExigirPermissao(modulo = Modulo.RELATORIOS, acao = Acao.VER)
    @GetMapping("/inadimplencia")
    public ResponseEntity<IndicadoresDTO.Inadimplencia> inadimplencia() {
        return ResponseEntity.ok(indicadores.inadimplencia(LocalDate.now()));
    }
}