package com.acougue.modules.estoque;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.balanca.ItemPendenteBalancaService;
import com.acougue.repository.ProdutoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepo;
    private final ItemPendenteBalancaService itemPendenteBalancaService;

    public List<Produto> listarAtivos() {
        return produtoRepo.findByAtivoTrue();
    }

    public Produto buscarPorId(Long id) {
        return produtoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + id));
    }

    public Produto buscarPorEan13(String ean13) {
        return produtoRepo.findByEan13(ean13)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado para EAN: " + ean13));
    }

    public Produto buscarPorCodigoBalanca(Integer codigo) {
        return produtoRepo.findByCodigoBalanca(codigo)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado para PLU: " + codigo));
    }

    public List<Produto> buscarPorNome(String nome) {
        return produtoRepo.buscarPorNome(nome);
    }

    public List<Produto> listarParaBalanca() {
        return produtoRepo.findByCodigoBalancaIsNotNullAndAtivoTrue();
    }

    // Prazo padrão de validade é opcional, mas se informado precisa fazer sentido
    // (o banco também tem CHECK > 0; aqui devolvemos erro amigável em vez de violação de constraint).
    private void validarValidadePadrao(Integer dias) {
        if (dias != null && dias <= 0) {
            throw new BusinessException("A validade padrão deve ser de pelo menos 1 dia (ou deixe em branco).");
        }
    }

    public List<Produto> alertasEstoqueMinimo() {
        return produtoRepo.findEstoqueAbaixoMinimo();
    }

    @Transactional
    public Produto salvar(Produto produto, Long usuarioId) {
        produto.setCriadoPorId(usuarioId); // autoria vem do token, nunca do corpo da requisição
        validarValidadePadrao(produto.getValidadePadraoDias());
        if (produto.getCodigoInterno() == null || produto.getCodigoInterno().isBlank()) {
            produto.setCodigoInterno(gerarCodigoInterno());
        }
        return produtoRepo.save(produto);
    }

    @Transactional
    public Produto atualizar(Long id, Produto dados) {
        Produto existente = buscarPorId(id);
        BigDecimal precoAnterior = existente.getPrecoVenda();

        existente.setNome(dados.getNome());
        existente.setDescricao(dados.getDescricao());
        existente.setPrecoVenda(dados.getPrecoVenda());
        // precoCusto NÃO é editável manualmente — é calculado automaticamente pelo
        // custo médio ponderado a cada entrada de estoque (EstoqueService.atualizarCustoMedio).
        // Aceitar edição manual aqui corromperia esse cálculo silenciosamente.
        existente.setUnidadeMedida(dados.getUnidadeMedida());
        existente.setTipoProduto(dados.getTipoProduto());
        existente.setMarca(dados.getMarca());
        existente.setFornecedor(dados.getFornecedor());
        existente.setCodigoBalanca(dados.getCodigoBalanca());
        existente.setEan13(dados.getEan13());
        existente.setEstoqueMinimo(dados.getEstoqueMinimo());
        validarValidadePadrao(dados.getValidadePadraoDias());
        existente.setValidadePadraoDias(dados.getValidadePadraoDias());
        existente.setCategoria(dados.getCategoria());
        existente.setAtivo(dados.getAtivo());
        Produto salvo = produtoRepo.save(existente);

        // Gera/atualiza item na fila de carga de balança se o preço mudou e
        // o produto tem PLU cadastrado. Não depende de como a carga chega
        // fisicamente na balança (rede, agente local etc.) — só registra a
        // intenção "esse preço precisa ser sincronizado".
        itemPendenteBalancaService.registrarAlteracaoPreco(salvo, precoAnterior, salvo.getPrecoVenda());

        return salvo;
    }

    @Transactional
    public void inativar(Long id) {
        Produto p = buscarPorId(id);
        p.setAtivo(false);
        produtoRepo.save(p);
    }

    private String gerarCodigoInterno() {
        long count = produtoRepo.count() + 1;
        return String.format("P%06d", count);
    }
}