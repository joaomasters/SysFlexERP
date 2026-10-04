package com.acougue.modules.financeiro;

import com.acougue.entity.Cliente;
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

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;
    private final IdentificacaoUsuario identificacao;

    @ExigirPermissao(modulo = Modulo.CLIENTES, acao = Acao.VER)
    @GetMapping
    public ResponseEntity<List<Cliente>> listar(
            @RequestParam(required = false) String nome) {
        List<Cliente> clientes = (nome != null && !nome.isBlank())
                ? clienteService.buscarPorNome(nome)
                : clienteService.listarAtivos();
        identificacao.preencher(clientes, Cliente::getCriadoPorId, Cliente::setCriadoPorNome);
        return ResponseEntity.ok(clientes);
    }

    @ExigirPermissao(modulo = Modulo.CLIENTES, acao = Acao.VER)
    @GetMapping("/faturaveis")
    public ResponseEntity<List<Cliente>> listarFaturaveis() {
        return ResponseEntity.ok(clienteService.listarFaturaveis());
    }

    @ExigirPermissao(modulo = Modulo.CLIENTES, acao = Acao.VER)
    @GetMapping("/{id}")
    public ResponseEntity<Cliente> buscarPorId(@PathVariable Long id) {
        Cliente cliente = clienteService.buscarPorId(id);
        identificacao.preencherItem(cliente, Cliente::getCriadoPorId, Cliente::setCriadoPorNome);
        return ResponseEntity.ok(cliente);
    }

    @ExigirPermissao(modulo = Modulo.CLIENTES, acao = Acao.CRIAR)
    @PostMapping
    public ResponseEntity<Cliente> criar(@RequestBody @Valid Cliente cliente) {
        return ResponseEntity.ok(clienteService.criar(cliente, ContextoUsuario.atual().getUsuarioId()));
    }

    @ExigirPermissao(modulo = Modulo.CLIENTES, acao = Acao.EDITAR)
    @PutMapping("/{id}")
    public ResponseEntity<Cliente> atualizar(@PathVariable Long id, @RequestBody @Valid Cliente cliente) {
        return ResponseEntity.ok(clienteService.atualizar(id, cliente));
    }

    @ExigirPermissao(modulo = Modulo.CLIENTES, acao = Acao.EXCLUIR)
    @PostMapping("/{id}/inativar")
    public ResponseEntity<Void> inativar(@PathVariable Long id) {
        clienteService.inativar(id);
        return ResponseEntity.ok().build();
    }

    @ExigirPermissao(modulo = Modulo.CLIENTES, acao = Acao.EDITAR)
    @PostMapping("/{id}/reativar")
    public ResponseEntity<Void> reativar(@PathVariable Long id) {
        clienteService.reativar(id);
        return ResponseEntity.ok().build();
    }
}