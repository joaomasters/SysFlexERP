package com.acougue.modules.estoque;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Sem @ExigirPermissao: a trava é ser SUPER_ADMIN, conferida no ImportacaoFiscalProdutoService. */
@RestController
@RequestMapping("/estoque/produtos/fiscal")
@RequiredArgsConstructor
public class ImportacaoFiscalProdutoController {

    private final ImportacaoFiscalProdutoService service;

    public record PedidoImportacao(List<ImportacaoFiscalProdutoService.LinhaPlanilha> linhas, boolean aplicar) {}

    @PostMapping("/importar")
    public ResponseEntity<ImportacaoFiscalProdutoService.Resultado> importar(@RequestBody PedidoImportacao pedido) {
        return ResponseEntity.ok(service.importar(pedido.linhas(), pedido.aplicar()));
    }
}
