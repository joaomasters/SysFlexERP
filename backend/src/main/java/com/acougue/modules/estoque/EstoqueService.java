package com.acougue.modules.estoque;

import com.acougue.entity.LoteEstoque;
import com.acougue.entity.MovimentacaoEstoque;
import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.repository.LoteEstoqueRepository;
import com.acougue.repository.MovimentacaoEstoqueRepository;
import com.acougue.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EstoqueService {

    private final ProdutoRepository      produtoRepo;
    private final MovimentacaoEstoqueRepository movRepo;
    private final LoteEstoqueRepository  loteRepo;

    /** Entrada sem validade informada: o lote só existe se o produto tiver validade padrão. */
    @Transactional
    public void entrada(Produto produto, BigDecimal quantidade, BigDecimal custoUnitario,
                        String tipoMov, String docRef, Long usuarioId) {
        entrada(produto, quantidade, custoUnitario, tipoMov, docRef, usuarioId, null);
    }

    /**
     * Entrada com validade explícita (ex: informada no recebimento). Se dataValidade for null,
     * usa a validade padrão do produto (hoje + validadePadraoDias); sem nenhuma das duas, o
     * estoque entra sem lote (validade desconhecida, não rastreada).
     */
    @Transactional
    public void entrada(Produto produto, BigDecimal quantidade, BigDecimal custoUnitario,
                        String tipoMov, String docRef, Long usuarioId, LocalDate dataValidade) {
        if (quantidade.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Quantidade de entrada deve ser positiva.");
        }
        atualizarCustoMedio(produto, quantidade, custoUnitario);
        produto.setEstoqueAtual(produto.getEstoqueAtual().add(quantidade).setScale(4, RoundingMode.HALF_UP));
        produtoRepo.save(produto);
        registrarLote(produto, quantidade, dataValidade, docRef);
        registrar(produto, quantidade, custoUnitario, tipoMov, docRef, usuarioId);
    }

    @Transactional
    public void saida(Produto produto, BigDecimal quantidade,
                      String tipoMov, String docRef, Long usuarioId) {
        if (quantidade.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Quantidade de saída deve ser positiva.");
        }
        if (produto.getEstoqueAtual().compareTo(quantidade) < 0) {
            throw new BusinessException(String.format(
                    "Estoque insuficiente para '%s'. Disponível: %.3f | Solicitado: %.3f",
                    produto.getNome(), produto.getEstoqueAtual(), quantidade));
        }
        produto.setEstoqueAtual(produto.getEstoqueAtual().subtract(quantidade).setScale(4, RoundingMode.HALF_UP));
        produtoRepo.save(produto);
        baixarLotes(produto, quantidade);
        registrar(produto, quantidade, produto.getPrecoCusto(), tipoMov, docRef, usuarioId);
    }

    public List<Produto> listarEstoqueAbaixoMinimo() {
        return produtoRepo.findEstoqueAbaixoMinimo();
    }

    @Transactional
    public void ajuste(Produto produto, BigDecimal quantidade, String tipo, String docRef, Long usuarioId) {
        if ("AJUSTE_POSITIVO".equals(tipo)) {
            produto.setEstoqueAtual(produto.getEstoqueAtual().add(quantidade).setScale(4, RoundingMode.HALF_UP));
            produtoRepo.save(produto);
            registrarLote(produto, quantidade, null, docRef);
        } else {
            produto.setEstoqueAtual(produto.getEstoqueAtual().subtract(quantidade).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP));
            produtoRepo.save(produto);
            baixarLotes(produto, quantidade);
        }
        registrar(produto, quantidade, produto.getPrecoCusto(), tipo, docRef, usuarioId);
    }

    /**
     * Cria o lote da entrada, se houver validade conhecida (explícita ou padrão do produto).
     * Sem validade, o estoque não é rastreado por lote — ver comentário da migration V20.
     */
    private void registrarLote(Produto produto, BigDecimal quantidade, LocalDate validadeInformada, String docRef) {
        LocalDate validade = validadeInformada;
        Integer padrao = produto.getValidadePadraoDias();
        if (validade == null && padrao != null && padrao > 0) {
            validade = LocalDate.now().plusDays(padrao);
        }
        if (validade == null) {
            return;
        }
        BigDecimal qtd = quantidade.setScale(4, RoundingMode.HALF_UP);
        loteRepo.save(LoteEstoque.builder()
                .produto(produto)
                .dataValidade(validade)
                .quantidadeInicial(qtd)
                .quantidadeAtual(qtd)
                .documentoRef(docRef)
                .build());
    }

    /**
     * Baixa FEFO: consome primeiro o lote que vence antes. O que não está em lote (estoque sem
     * validade conhecida) simplesmente não é tocado aqui — por isso a baixa nunca falha por
     * causa de lotes; quem valida saldo é o estoque_atual do produto.
     * Perda por VENCIMENTO cai aqui também: consome o lote mais antigo, que é o vencido.
     */
    private void baixarLotes(Produto produto, BigDecimal quantidade) {
        BigDecimal restante = quantidade;
        for (LoteEstoque lote : loteRepo.findDisponiveisParaBaixa(produto.getId())) {
            if (restante.signum() <= 0) {
                break;
            }
            BigDecimal baixa = lote.getQuantidadeAtual().min(restante);
            lote.setQuantidadeAtual(lote.getQuantidadeAtual().subtract(baixa).setScale(4, RoundingMode.HALF_UP));
            loteRepo.save(lote);
            restante = restante.subtract(baixa);
        }
    }

    private void atualizarCustoMedio(Produto produto, BigDecimal qtdNova, BigDecimal custoNovo) {
        if (custoNovo == null || custoNovo.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal estoqueAnt = produto.getEstoqueAtual();
        BigDecimal custoAnt   = produto.getPrecoCusto() != null ? produto.getPrecoCusto() : BigDecimal.ZERO;
        BigDecimal novoEstoque = estoqueAnt.add(qtdNova);
        if (novoEstoque.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal novoCusto = estoqueAnt.multiply(custoAnt)
                    .add(qtdNova.multiply(custoNovo))
                    .divide(novoEstoque, 4, RoundingMode.HALF_UP);
            produto.setPrecoCusto(novoCusto);
        }
    }

    private void registrar(Produto produto, BigDecimal quantidade, BigDecimal custo,
                           String tipo, String docRef, Long usuarioId) {
        MovimentacaoEstoque mov = MovimentacaoEstoque.builder()
                .produto(produto)
                .tipoMovimentacao(tipo)
                .quantidade(quantidade)
                .custoUnitario(custo)
                .documentoRef(docRef)
                .usuarioId(usuarioId)
                .build();
        movRepo.save(mov);
    }
}
