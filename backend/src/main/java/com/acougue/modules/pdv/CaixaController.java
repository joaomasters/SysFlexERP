package com.acougue.modules.pdv;

import com.acougue.entity.SangriaCaixa;
import com.acougue.entity.Caixa;
import com.acougue.entity.Modulo;
import com.acougue.modules.pdv.dto.FechamentoCaixaDetalhadoDTO;
import com.acougue.modules.pdv.dto.SangriaDTO;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pdv/caixa")
@RequiredArgsConstructor
public class CaixaController {

    private final CaixaService caixaService;

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.VER)
    @GetMapping("/aberto")
    public ResponseEntity<Caixa> caixaAberto(@RequestParam(required = false) Long operadorId) {
        // O operador é SEMPRE quem está logado; o parâmetro antigo é ignorado
        return ResponseEntity.ok(caixaService.buscarCaixaAbertoDoOperador(ContextoUsuario.atual().getUsuarioId()));
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.VER)
    @GetMapping
    public ResponseEntity<List<Caixa>> listar() {
        return ResponseEntity.ok(caixaService.listarTodos());
    }

    @ExigirPermissao(modulo = Modulo.SANGRIA, acao = Acao.CRIAR)
    @PostMapping("/{id}/sangria")
    public ResponseEntity<SangriaCaixa> sangria(
            @PathVariable Long id,
            @RequestBody SangriaDTO dto) {
        dto.setTipo("SANGRIA");
        dto.setOperadorId(ContextoUsuario.atual().getUsuarioId()); // nunca do corpo da requisição
        return ResponseEntity.ok(caixaService.registrarMovimento(id, dto));
    }

    @ExigirPermissao(modulo = Modulo.SANGRIA, acao = Acao.CRIAR)
    @PostMapping("/{id}/suprimento")
    public ResponseEntity<SangriaCaixa> suprimento(
            @PathVariable Long id,
            @RequestBody SangriaDTO dto) {
        dto.setTipo("SUPRIMENTO");
        dto.setOperadorId(ContextoUsuario.atual().getUsuarioId()); // nunca do corpo da requisição
        return ResponseEntity.ok(caixaService.registrarMovimento(id, dto));
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.VER)
    @GetMapping("/{id}/fechamento")
    public ResponseEntity<FechamentoCaixaDetalhadoDTO> fechamento(@PathVariable Long id) {
        return ResponseEntity.ok(caixaService.calcularFechamento(id));
    }

    @ExigirPermissao(modulo = Modulo.SANGRIA, acao = Acao.VER)
    @GetMapping("/{id}/movimentos")
    public ResponseEntity<List<SangriaCaixa>> movimentos(@PathVariable Long id) {
        return ResponseEntity.ok(caixaService.listarMovimentos(id));
    }
}