package com.acougue.modules.fiscal.emissao;

import com.acougue.entity.Modulo;
import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Números que nunca viraram nota e o pedido de inutilização deles. */
@RestController
@RequestMapping("/fiscal/inutilizacoes")
@RequiredArgsConstructor
public class InutilizacaoController {

    private final InutilizacaoService service;

    @ExigirPermissao(modulo = Modulo.FISCAL, acao = Acao.VER)
    @GetMapping
    public ResponseEntity<List<Inutilizacao>> historico() {
        return ResponseEntity.ok(service.historico());
    }

    @ExigirPermissao(modulo = Modulo.FISCAL, acao = Acao.VER)
    @GetMapping("/lacunas")
    public ResponseEntity<List<InutilizacaoService.Lacuna>> lacunas(@RequestParam(defaultValue = "65") short modelo) {
        return ResponseEntity.ok(service.lacunas(modelo));
    }

    @ExigirPermissao(modulo = Modulo.FISCAL, acao = Acao.EDITAR)
    @PostMapping
    public ResponseEntity<Inutilizacao> inutilizar(@RequestBody InutilizacaoService.Pedido pedido) {
        return ResponseEntity.ok(service.inutilizar(pedido));
    }
}
