package com.acougue.modules.pdv;

import com.acougue.entity.Caixa;
import com.acougue.entity.Modulo;
import com.acougue.entity.Venda;
import com.acougue.modules.pdv.dto.*;
import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/pdv")
@RequiredArgsConstructor
public class PdvController {

    private final PdvService pdvService;

    // Caixa

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR)
    @PostMapping("/caixa/abrir")
    public ResponseEntity<Caixa> abrirCaixa(
            @RequestParam Long operadorId,
            @RequestParam BigDecimal valorAbertura) {
        return ResponseEntity.ok(pdvService.abrirCaixa(operadorId, valorAbertura));
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR)
    @PostMapping("/caixa/{id}/fechar")
    public ResponseEntity<Caixa> fecharCaixa(
            @PathVariable Long id,
            @RequestParam BigDecimal valorInformado) {
        return ResponseEntity.ok(pdvService.fecharCaixa(id, valorInformado));
    }

    // Venda

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR)
    @PostMapping("/vendas/abrir")
    public ResponseEntity<Venda> abrirVenda(@RequestBody @Valid AbrirVendaDTO dto) {
        return ResponseEntity.ok(pdvService.abrirVenda(dto));
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.VER)
    @GetMapping("/vendas/abertas")
    public ResponseEntity<List<Venda>> listarVendasAbertas(@RequestParam Long caixaId) {
        return ResponseEntity.ok(pdvService.listarVendasAbertas(caixaId));
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.VER)
    @GetMapping("/vendas/{id}")
    public ResponseEntity<Venda> buscarComanda(@PathVariable Long id) {
        return ResponseEntity.ok(pdvService.buscarComanda(id));
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.VER)
    @GetMapping("/barcode/{ean13}")
    public ResponseEntity<ItemVendaDTO> processarBarcode(@PathVariable String ean13) {
        return ResponseEntity.ok(pdvService.processarBarcode(ean13));
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR)
    @PostMapping("/vendas/{id}/itens")
    public ResponseEntity<Venda> adicionarItem(
            @PathVariable Long id,
            @RequestBody @Valid ItemVendaDTO dto) {
        return ResponseEntity.ok(pdvService.adicionarItem(id, dto));
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR)
    @DeleteMapping("/vendas/{vendaId}/itens/{itemId}")
    public ResponseEntity<Venda> removerItem(
            @PathVariable Long vendaId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(pdvService.removerItem(vendaId, itemId));
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR)
    @PostMapping("/vendas/fechar")
    public ResponseEntity<Venda> fecharVenda(@RequestBody @Valid FecharVendaDTO dto) {
        return ResponseEntity.ok(pdvService.fecharVenda(dto));
    }

    // Cancelar uma venda é uma ação sensível — exige a permissão de EXCLUIR
    // no módulo PDV, que por padrão só ADMIN e SUPER_ADMIN têm (GESTOR e
    // CAIXA não). Isso restringe o cancelamento ao administrador sem
    // precisar de um mecanismo novo: reaproveita a matriz de perfis já
    // existente (ver V9__controle_acesso_perfis.sql).
    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.EXCLUIR)
    @PostMapping("/vendas/{id}/cancelar")
    public ResponseEntity<Venda> cancelarVenda(@PathVariable Long id) {
        return ResponseEntity.ok(pdvService.cancelarVenda(id));
    }
}