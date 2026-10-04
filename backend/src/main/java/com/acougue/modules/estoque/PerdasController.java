package com.acougue.modules.estoque;

import com.acougue.entity.Modulo;
import com.acougue.entity.PerdasEstoque;
import com.acougue.modules.estoque.dto.LancarPerdaDTO;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.ExigirPermissao;
import com.acougue.security.IdentificacaoUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/estoque/perdas")
@RequiredArgsConstructor
public class PerdasController {

    private final PerdasService perdasService;
    private final IdentificacaoUsuario identificacao;

    @ExigirPermissao(modulo = Modulo.PERDAS, acao = Acao.CRIAR)
    @PostMapping
    public ResponseEntity<PerdasEstoque> lancar(@RequestBody LancarPerdaDTO dto) {
        // Quem lançou é SEMPRE o usuário logado — ignora qualquer usuarioId vindo no corpo.
        dto.setUsuarioId(ContextoUsuario.atual().getUsuarioId());
        return ResponseEntity.ok(perdasService.lancarPerda(dto));
    }

    @ExigirPermissao(modulo = Modulo.PERDAS, acao = Acao.VER)
    @GetMapping
    public ResponseEntity<List<PerdasEstoque>> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        List<PerdasEstoque> perdas = perdasService.listarPorPeriodo(
                inicio.atStartOfDay(), fim.atTime(LocalTime.MAX));
        identificacao.preencher(perdas, PerdasEstoque::getUsuarioId, PerdasEstoque::setUsuarioNome);
        return ResponseEntity.ok(perdas);
    }

    @ExigirPermissao(modulo = Modulo.PERDAS, acao = Acao.VER)
    @GetMapping("/produto/{produtoId}")
    public ResponseEntity<List<PerdasEstoque>> listarPorProduto(@PathVariable Long produtoId) {
        List<PerdasEstoque> perdas = perdasService.listarPorProduto(produtoId);
        identificacao.preencher(perdas, PerdasEstoque::getUsuarioId, PerdasEstoque::setUsuarioNome);
        return ResponseEntity.ok(perdas);
    }
}