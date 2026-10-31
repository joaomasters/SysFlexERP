package com.acougue.modules.estoque;

import com.acougue.entity.Fornecedor;
import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.balanca.ItemPendenteBalancaService;
import com.acougue.modules.estoque.dto.OpcaoFornecedorDTO;
import com.acougue.repository.FornecedorRepository;
import com.acougue.repository.ProdutoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepo;
    private final FornecedorRepository fornecedorRepo;
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

    private static String normalizarEan(String ean13) {
        return ean13 == null || ean13.isBlank() ? null : ean13.trim();
    }

    // Só bloqueia contra produto ativo: inativos não aparecem na tela, e um código preso num
    // produto inativado impediria recadastrar o item sem o usuário ter como resolver.
    private void validarEanUnico(String ean13, Long idProduto) {
        if (ean13 == null) return;
        produtoRepo.findFirstByEan13AndAtivoTrueAndIdNot(ean13, idProduto == null ? 0L : idProduto)
                .ifPresent(outro -> {
                    throw new BusinessException("Código de barras " + ean13
                            + " já cadastrado no produto \"" + outro.getNome() + "\".");
                });
    }

    /** Fornecedores ativos para o dropdown do cadastro de produto. */
    public List<OpcaoFornecedorDTO> opcoesFornecedor() {
        return fornecedorRepo.findByAtivoTrueOrderByNomeAsc().stream()
                .map(f -> new OpcaoFornecedorDTO(f.getId(), f.getNome()))
                .toList();
    }

    /**
     * Mantém produto.fornecedor (texto) coerente com produto.fornecedorId. Com id, o nome vem
     * SEMPRE do cadastro — o texto enviado pelo cliente é ignorado. Só aceita fornecedor ativo,
     * ou o mesmo que o produto já tinha (inativar um fornecedor não impede editar produtos dele).
     * Sem id, o vínculo é removido e o texto livre é limpo.
     */
    private void aplicarFornecedor(Produto produto, Long fornecedorAtualId) {
        Long id = produto.getFornecedorId();
        if (id == null) {
            produto.setFornecedor(null);
            return;
        }
        Fornecedor f = fornecedorRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Fornecedor não encontrado."));
        if (!Boolean.TRUE.equals(f.getAtivo()) && !id.equals(fornecedorAtualId)) {
            throw new BusinessException("O fornecedor \"" + f.getNome() + "\" está inativo.");
        }
        produto.setFornecedor(f.getNome());
    }

    public List<Produto> alertasEstoqueMinimo() {
        return produtoRepo.findEstoqueAbaixoMinimo();
    }

    @Transactional
    public Produto salvar(Produto produto, Long usuarioId) {
        // Cadastro SEMPRE cria: com um "id" no corpo, o save() sobrescrevia um produto
        // existente — quem só tinha permissão de criar alterava preço/estoque de outros.
        produto.setId(null);
        produto.setCriadoPorId(usuarioId); // autoria vem do token, nunca do corpo da requisição
        validarValidadePadrao(produto.getValidadePadraoDias());
        produto.setEan13(normalizarEan(produto.getEan13()));
        validarEanUnico(produto.getEan13(), null);
        aplicarFornecedor(produto, null);
        if (produto.getCodigoInterno() == null || produto.getCodigoInterno().isBlank()) {
            produto.setCodigoInterno(gerarCodigoInterno());
        }
        return produtoRepo.save(produto);
    }

    @Transactional
    public Produto atualizar(Long id, Produto dados) {
        Produto existente = buscarPorId(id);
        Long fornecedorAnteriorId = existente.getFornecedorId();
        BigDecimal precoAnterior = existente.getPrecoVenda();

        // Só confere quando o código muda ou o produto volta a ser ativo: duplicados antigos
        // não podem impedir de editar outros campos, como o preço.
        String ean13 = normalizarEan(dados.getEan13());
        boolean reativando = !Boolean.TRUE.equals(existente.getAtivo()) && Boolean.TRUE.equals(dados.getAtivo());
        if (reativando || !Objects.equals(ean13, normalizarEan(existente.getEan13()))) {
            validarEanUnico(ean13, id);
        }

        existente.setNome(dados.getNome());
        existente.setDescricao(dados.getDescricao());
        existente.setPrecoVenda(dados.getPrecoVenda());
        // precoCusto NÃO é editável manualmente — é calculado automaticamente pelo
        // custo médio ponderado a cada entrada de estoque (EstoqueService.atualizarCustoMedio).
        // Aceitar edição manual aqui corromperia esse cálculo silenciosamente.
        existente.setUnidadeMedida(dados.getUnidadeMedida());
        existente.setTipoProduto(dados.getTipoProduto());
        existente.setMarca(dados.getMarca());
        existente.setFornecedorId(dados.getFornecedorId());
        aplicarFornecedor(existente, fornecedorAnteriorId);
        existente.setCodigoBalanca(dados.getCodigoBalanca());
        existente.setEan13(ean13);
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