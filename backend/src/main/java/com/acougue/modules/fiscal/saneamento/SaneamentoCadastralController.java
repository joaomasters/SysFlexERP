package com.acougue.modules.fiscal.saneamento;

import com.acougue.entity.Modulo;
import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fiscal/saneamento")
@RequiredArgsConstructor
public class SaneamentoCadastralController {

    private final SaneamentoCadastralService saneamento;

    @ExigirPermissao(modulo = Modulo.FISCAL, acao = Acao.VER)
    @GetMapping
    public ResponseEntity<SaneamentoCadastralService.Saneamento> calcular() {
        return ResponseEntity.ok(saneamento.calcular());
    }
}
