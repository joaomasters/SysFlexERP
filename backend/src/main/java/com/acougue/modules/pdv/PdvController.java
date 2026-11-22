package com.acougue.modules.pdv;

import com.acougue.entity.Caixa;
import com.acougue.entity.Modulo;
import com.acougue.entity.Venda;
import com.acougue.modules.pdv.dto.*;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
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

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR, auditoriaPropria = true)
    @PostMapping("/caixa/abrir")
    public ResponseEntity<Caixa> abrirCaixa(
            @RequestParam(required = false) Long operadorId,
            @RequestParam BigDecimal valorAbertura,
            @RequestBody(required = false) @Valid ContagemCedulasDTO contagem) {
        // Abre o caixa para quem está logado — o operadorId enviado pela tela é ignorado.
        // A contagem de cédulas e moedas é opcional.
        return ResponseEntity.ok(pdvService.abrirCaixa(ContextoUsuario.atual().getUsuarioId(), valorAbertura, contagem));
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR, auditoriaPropria = true)
    @PostMapping("/caixa/{id}/fechar")
    public ResponseEntity<Caixa> fecharCaixa(
            @PathVariable Long id,
            @RequestParam BigDecimal valorInformado,
            @RequestBody(required = false) @Valid ContagemCedulasDTO contagem) {
        return ResponseEntity.ok(pdvService.fecharCaixa(id, valorInformado, contagem));
    }

    // Venda

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR)
    @PostMapping("/vendas/abrir")
    public ResponseEntity<Venda> abrirVenda(@RequestBody @Valid AbrirVendaDTO dto) {
        // O vendedor (e portanto a comissão) é quem está logado, nunca o informado pela tela
        dto.setOperadorId(ContextoUsuario.atual().getUsuarioId());
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

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR, auditoriaPropria = true)
    @PostMapping("/vendas/{id}/itens")
    public ResponseEntity<Venda> adicionarItem(
            @PathVariable Long id,
            @RequestBody @Valid ItemVendaDTO dto) {
        return ResponseEntity.ok(pdvService.adicionarItem(id, dto));
    }

    // Excluir item exige a credencial de um administrador (login + senha), conferida no service.
    // Era DELETE sem corpo; virou POST com a autorização no corpo — o endpoint antigo foi removido
    // de propósito, para não existir um caminho que dispensa a senha.
    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR, auditoriaPropria = true)
    @PostMapping("/vendas/{vendaId}/itens/{itemId}/remover")
    public ResponseEntity<Venda> removerItem(
            @PathVariable Long vendaId,
            @PathVariable Long itemId,
            @RequestBody @Valid AutorizacaoOperacaoDTO autorizacao) {
        return ResponseEntity.ok(pdvService.removerItem(vendaId, itemId, autorizacao));
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR, auditoriaPropria = true)
    @PutMapping("/vendas/{vendaId}/itens/{itemId}/quantidade")
    public ResponseEntity<Venda> alterarQuantidadeItem(
            @PathVariable Long vendaId,
            @PathVariable Long itemId,
            @RequestBody @Valid QuantidadeItemDTO dto) {
        return ResponseEntity.ok(pdvService.alterarQuantidadeItem(vendaId, itemId, dto.getQuantidade()));
    }

    // Desconto mexe no valor da venda: exige EDITAR no PDV, que por padrão ADMIN e GESTOR têm
    // e CAIXA não. Para liberar ao caixa, basta marcar a permissão na tela de Perfis.
    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.EDITAR)
    @PutMapping("/vendas/{vendaId}/itens/{itemId}/desconto")
    public ResponseEntity<Venda> aplicarDescontoItem(
            @PathVariable Long vendaId,
            @PathVariable Long itemId,
            @RequestBody @Valid DescontoItemDTO dto) {
        return ResponseEntity.ok(pdvService.aplicarDescontoItem(vendaId, itemId, dto.getValor()));
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR)
    @PostMapping("/vendas/fechar")
    public ResponseEntity<Venda> fecharVenda(@RequestBody @Valid FecharVendaDTO dto) {
        return ResponseEntity.ok(pdvService.fecharVenda(dto));
    }

    // Cancelar uma venda é uma ação sensível: o operador (CAIXA inclusive) pede, mas a venda só é
    // cancelada com a credencial de um administrador (login + senha), conferida no service.
    // Antes dependia de o próprio usuário logado ter EXCLUIR no PDV.
    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR, auditoriaPropria = true)
    @PostMapping("/vendas/{id}/cancelar")
    public ResponseEntity<Venda> cancelarVenda(
            @PathVariable Long id,
            @RequestBody @Valid AutorizacaoOperacaoDTO autorizacao) {
        return ResponseEntity.ok(pdvService.cancelarVenda(id, autorizacao));
    }
}
