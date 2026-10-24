package com.acougue.modules.estoque;

import com.acougue.entity.Fornecedor;
import com.acougue.entity.Modulo;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.ExigirPermissao;
import com.acougue.security.IdentificacaoUsuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Cadastro de fornecedores — módulo FORNECEDORES. */
@RestController
@RequestMapping("/estoque/fornecedores")
@RequiredArgsConstructor
public class FornecedorController {

    private final FornecedorService fornecedorService;
    private final IdentificacaoUsuario identificacao;

    @ExigirPermissao(modulo = Modulo.FORNECEDORES, acao = Acao.VER)
    @GetMapping
    public ResponseEntity<List<Fornecedor>> listar(@RequestParam(defaultValue = "false") boolean incluirInativos) {
        List<Fornecedor> lista = fornecedorService.listar(incluirInativos);
        identificacao.preencher(lista, Fornecedor::getCriadoPorId, Fornecedor::setCriadoPorNome);
        return ResponseEntity.ok(lista);
    }

    @ExigirPermissao(modulo = Modulo.FORNECEDORES, acao = Acao.CRIAR)
    @PostMapping
    public ResponseEntity<Fornecedor> criar(@RequestBody @Valid Fornecedor fornecedor) {
        return ResponseEntity.ok(fornecedorService.criar(fornecedor, ContextoUsuario.atual().getUsuarioId()));
    }

    @ExigirPermissao(modulo = Modulo.FORNECEDORES, acao = Acao.EDITAR)
    @PutMapping("/{id}")
    public ResponseEntity<Fornecedor> atualizar(@PathVariable Long id, @RequestBody @Valid Fornecedor fornecedor) {
        return ResponseEntity.ok(fornecedorService.atualizar(id, fornecedor));
    }

    @ExigirPermissao(modulo = Modulo.FORNECEDORES, acao = Acao.EXCLUIR)
    @PostMapping("/{id}/inativar")
    public ResponseEntity<Void> inativar(@PathVariable Long id) {
        fornecedorService.inativar(id);
        return ResponseEntity.ok().build();
    }

    @ExigirPermissao(modulo = Modulo.FORNECEDORES, acao = Acao.EDITAR)
    @PostMapping("/{id}/reativar")
    public ResponseEntity<Void> reativar(@PathVariable Long id) {
        fornecedorService.reativar(id);
        return ResponseEntity.ok().build();
    }
}