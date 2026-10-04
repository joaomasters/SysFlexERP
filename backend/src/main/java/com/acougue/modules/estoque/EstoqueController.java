package com.acougue.modules.estoque;

import com.acougue.entity.*;
import com.acougue.modules.estoque.dto.ExecutarDesossaDTO;
import com.acougue.modules.estoque.dto.FichaDesossaDTO;
import com.acougue.repository.MovimentacaoEstoqueRepository;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.ExigirPermissao;
import com.acougue.security.IdentificacaoUsuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/estoque")
@RequiredArgsConstructor
public class EstoqueController {

    private final ProdutoService                 produtoService;
    private final DesossaService                 desossaService;
    private final MovimentacaoEstoqueRepository  movRepo;
    private final IdentificacaoUsuario           identificacao;

    // Produtos

    @ExigirPermissao(modulo = Modulo.PRODUTOS, acao = Acao.VER)
    @GetMapping("/produtos")
    public ResponseEntity<List<Produto>> listarProdutos(
            @RequestParam(required = false) String nome) {
        if (nome != null && !nome.isBlank()) {
            return ResponseEntity.ok(comNomes(produtoService.buscarPorNome(nome)));
        }
        return ResponseEntity.ok(comNomes(produtoService.listarAtivos()));
    }

    // Só administradores recebem o nome de quem cadastrou o produto — ver IdentificacaoUsuario.
    private List<Produto> comNomes(List<Produto> produtos) {
        identificacao.preencher(produtos, Produto::getCriadoPorId, Produto::setCriadoPorNome);
        return produtos;
    }

    @ExigirPermissao(modulo = Modulo.PRODUTOS, acao = Acao.VER)
    @GetMapping("/produtos/{id}")
    public ResponseEntity<Produto> buscarProduto(@PathVariable Long id) {
        Produto produto = produtoService.buscarPorId(id);
        identificacao.preencherItem(produto, Produto::getCriadoPorId, Produto::setCriadoPorNome);
        return ResponseEntity.ok(produto);
    }

    @ExigirPermissao(modulo = Modulo.PRODUTOS, acao = Acao.CRIAR)
    @PostMapping("/produtos")
    public ResponseEntity<Produto> criarProduto(@RequestBody @Valid Produto produto) {
        // Quem cadastrou é SEMPRE o usuário logado (token).
        return ResponseEntity.ok(produtoService.salvar(produto, ContextoUsuario.atual().getUsuarioId()));
    }

    @ExigirPermissao(modulo = Modulo.PRODUTOS, acao = Acao.EDITAR)
    @PutMapping("/produtos/{id}")
    public ResponseEntity<Produto> atualizarProduto(
            @PathVariable Long id, @RequestBody @Valid Produto produto) {
        return ResponseEntity.ok(produtoService.atualizar(id, produto));
    }

    @ExigirPermissao(modulo = Modulo.PRODUTOS, acao = Acao.EXCLUIR)
    @DeleteMapping("/produtos/{id}")
    public ResponseEntity<Void> inativarProduto(@PathVariable Long id) {
        produtoService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @ExigirPermissao(modulo = Modulo.PRODUTOS, acao = Acao.VER)
    @GetMapping("/produtos/alertas")
    public ResponseEntity<List<Produto>> alertasEstoque() {
        return ResponseEntity.ok(produtoService.alertasEstoqueMinimo());
    }

    // Fichas de Desossa

    @ExigirPermissao(modulo = Modulo.FICHAS_DESOSSA, acao = Acao.VER)
    @GetMapping("/fichas-desossa")
    public ResponseEntity<List<FichaDesossa>> listarFichas(
            @RequestParam(defaultValue = "false") boolean todas) {
        return ResponseEntity.ok(todas
                ? desossaService.listarTodasFichas()
                : desossaService.listarFichas());
    }

    @ExigirPermissao(modulo = Modulo.FICHAS_DESOSSA, acao = Acao.VER)
    @GetMapping("/fichas-desossa/{id}")
    public ResponseEntity<FichaDesossa> buscarFicha(@PathVariable Long id) {
        return ResponseEntity.ok(desossaService.buscarFicha(id));
    }

    @ExigirPermissao(modulo = Modulo.FICHAS_DESOSSA, acao = Acao.CRIAR)
    @PostMapping("/fichas-desossa")
    public ResponseEntity<FichaDesossa> criarFicha(@RequestBody FichaDesossaDTO dto) {
        return ResponseEntity.ok(desossaService.criarFicha(dto));
    }

    @ExigirPermissao(modulo = Modulo.FICHAS_DESOSSA, acao = Acao.EDITAR)
    @PutMapping("/fichas-desossa/{id}")
    public ResponseEntity<FichaDesossa> atualizarFicha(
            @PathVariable Long id, @RequestBody FichaDesossaDTO dto) {
        return ResponseEntity.ok(desossaService.atualizarFicha(id, dto));
    }

    @ExigirPermissao(modulo = Modulo.FICHAS_DESOSSA, acao = Acao.EXCLUIR)
    @DeleteMapping("/fichas-desossa/{id}")
    public ResponseEntity<Void> inativarFicha(@PathVariable Long id) {
        desossaService.inativarFicha(id);
        return ResponseEntity.noContent().build();
    }

    @ExigirPermissao(modulo = Modulo.FICHAS_DESOSSA, acao = Acao.EXCLUIR)
    @PatchMapping("/fichas-desossa/{id}/reativar")
    public ResponseEntity<FichaDesossa> reativarFicha(@PathVariable Long id) {
        desossaService.reativarFicha(id);
        return ResponseEntity.ok(desossaService.buscarFicha(id));
    }

    // Execução de Desossa

    @ExigirPermissao(modulo = Modulo.RATEIO_DESOSSA, acao = Acao.CRIAR)
    @PostMapping("/desossa/executar")
    public ResponseEntity<ProcessoDesossa> executarDesossa(
            @RequestBody @Valid ExecutarDesossaDTO dto) {
        // Quem executou o rateio é SEMPRE o usuário logado — ignora usuarioId do corpo.
        dto.setUsuarioId(ContextoUsuario.atual().getUsuarioId());
        return ResponseEntity.ok(desossaService.executarDesossa(dto));
    }

    @ExigirPermissao(modulo = Modulo.RATEIO_DESOSSA, acao = Acao.VER)
    @GetMapping("/desossa/saldo-nf")
    public ResponseEntity<java.math.BigDecimal> saldoNf(
            @RequestParam Long recebimentoId, @RequestParam Long produtoPaiId) {
        return ResponseEntity.ok(desossaService.saldoDisponivelNf(recebimentoId, produtoPaiId));
    }

    @ExigirPermissao(modulo = Modulo.RATEIO_DESOSSA, acao = Acao.VER)
    @GetMapping("/desossa/historico/{fichaId}")
    public ResponseEntity<List<ProcessoDesossa>> historicoDesossa(@PathVariable Long fichaId) {
        List<ProcessoDesossa> historico = desossaService.listarPorFicha(fichaId);
        identificacao.preencher(historico, ProcessoDesossa::getUsuarioId, ProcessoDesossa::setUsuarioNome);
        return ResponseEntity.ok(historico);
    }

    // Movimentações

    @ExigirPermissao(modulo = Modulo.PRODUTOS, acao = Acao.VER)
    @GetMapping("/movimentacoes")
    public ResponseEntity<List<MovimentacaoEstoque>> listarMovimentacoes(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        LocalDateTime dtInicio = inicio.atStartOfDay();
        LocalDateTime dtFim    = fim.atTime(LocalTime.MAX);
        return ResponseEntity.ok(movRepo.findByPeriodo(dtInicio, dtFim));
    }

    @ExigirPermissao(modulo = Modulo.PRODUTOS, acao = Acao.VER)
    @GetMapping("/movimentacoes/produto/{produtoId}")
    public ResponseEntity<List<MovimentacaoEstoque>> movimentacoesProduto(@PathVariable Long produtoId) {
        return ResponseEntity.ok(movRepo.findByProdutoIdOrderByCreatedAtDesc(produtoId));
    }
}