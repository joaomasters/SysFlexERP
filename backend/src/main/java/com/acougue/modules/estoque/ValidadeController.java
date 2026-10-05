package com.acougue.modules.estoque;

import com.acougue.entity.Modulo;
import com.acougue.modules.estoque.dto.LoteValidadeDTO;
import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/estoque/validade")
@RequiredArgsConstructor
public class ValidadeController {

    private final ValidadeService validadeService;

    // Tela de Controle de Validade: todos os lotes com saldo
    @ExigirPermissao(modulo = Modulo.PRODUTOS, acao = Acao.VER)
    @GetMapping
    public ResponseEntity<List<LoteValidadeDTO>> listar() {
        return ResponseEntity.ok(validadeService.listar());
    }

    // Sino de notificações: só vencidos e vencendo em breve
    @ExigirPermissao(modulo = Modulo.PRODUTOS, acao = Acao.VER)
    @GetMapping("/alertas")
    public ResponseEntity<List<LoteValidadeDTO>> alertas() {
        return ResponseEntity.ok(validadeService.alertas());
    }
}
