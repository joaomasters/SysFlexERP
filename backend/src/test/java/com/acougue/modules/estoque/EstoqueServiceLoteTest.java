package com.acougue.modules.estoque;

import com.acougue.entity.LoteEstoque;
import com.acougue.entity.Produto;
import com.acougue.repository.LoteEstoqueRepository;
import com.acougue.repository.MovimentacaoEstoqueRepository;
import com.acougue.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EstoqueService — lotes e validade (FEFO)")
class EstoqueServiceLoteTest {

    @Mock ProdutoRepository             produtoRepo;
    @Mock MovimentacaoEstoqueRepository movRepo;
    @Mock LoteEstoqueRepository         loteRepo;

    @InjectMocks EstoqueService service;

    private Produto produto;

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @BeforeEach
    void setUp() {
        produto = Produto.builder()
                .id(1L).nome("Picanha").unidadeMedida("KG")
                .estoqueAtual(bd("10.0000")).precoCusto(bd("50.0000"))
                .build();
    }

    private static LoteEstoque lote(long id, String saldo, LocalDate validade) {
        return LoteEstoque.builder().id(id).dataValidade(validade)
                .quantidadeInicial(bd(saldo)).quantidadeAtual(bd(saldo)).build();
    }

    // ───────────── entrada → cria lote ─────────────

    @Test
    @DisplayName("entrada com validade informada cria lote com a quantidade e a validade")
    void entradaComValidadeCriaLote() {
        LocalDate validade = LocalDate.now().plusDays(7);

        service.entrada(produto, bd("5"), bd("50"), "ENTRADA_COMPRA", "NF1", 7L, validade);

        ArgumentCaptor<LoteEstoque> captor = ArgumentCaptor.forClass(LoteEstoque.class);
        verify(loteRepo).save(captor.capture());
        LoteEstoque l = captor.getValue();
        assertThat(l.getDataValidade()).isEqualTo(validade);
        assertThat(l.getQuantidadeInicial()).isEqualByComparingTo("5");
        assertThat(l.getQuantidadeAtual()).isEqualByComparingTo("5");
        assertThat(l.getDocumentoRef()).isEqualTo("NF1");
        assertThat(l.getProduto()).isSameAs(produto);
    }

    @Test
    @DisplayName("entrada sem validade usa a validade padrão do produto (hoje + dias)")
    void entradaSemValidadeUsaPadraoDoProduto() {
        produto.setValidadePadraoDias(30);

        service.entrada(produto, bd("5"), bd("50"), "ENTRADA_DESOSSA", "DES#1", 7L);

        ArgumentCaptor<LoteEstoque> captor = ArgumentCaptor.forClass(LoteEstoque.class);
        verify(loteRepo).save(captor.capture());
        assertThat(captor.getValue().getDataValidade()).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    @DisplayName("validade explícita tem prioridade sobre a padrão do produto")
    void validadeExplicitaVenceAPadrao() {
        produto.setValidadePadraoDias(30);
        LocalDate explicita = LocalDate.now().plusDays(2);

        service.entrada(produto, bd("5"), bd("50"), "ENTRADA_COMPRA", "NF1", 7L, explicita);

        ArgumentCaptor<LoteEstoque> captor = ArgumentCaptor.forClass(LoteEstoque.class);
        verify(loteRepo).save(captor.capture());
        assertThat(captor.getValue().getDataValidade()).isEqualTo(explicita);
    }

    @Test
    @DisplayName("sem validade informada e sem padrão: o estoque entra, mas sem lote")
    void entradaSemNenhumaValidadeNaoCriaLote() {
        service.entrada(produto, bd("5"), bd("50"), "ENTRADA_COMPRA", "NF1", 7L);

        assertThat(produto.getEstoqueAtual()).isEqualByComparingTo("15");
        verify(loteRepo, never()).save(any());
    }

    // ───────────── saída → baixa FEFO ─────────────

    @Test
    @DisplayName("saída consome primeiro o lote que vence antes, e passa ao próximo quando esgota")
    void saidaConsomeEmOrdemFefo() {
        LoteEstoque venceLogo   = lote(1L, "3", LocalDate.now().plusDays(1));
        LoteEstoque venceDepois = lote(2L, "5", LocalDate.now().plusDays(10));
        // o repositório já devolve na ordem FEFO (ORDER BY validade, id)
        when(loteRepo.findDisponiveisParaBaixa(1L)).thenReturn(List.of(venceLogo, venceDepois));

        service.saida(produto, bd("4"), "SAIDA_VENDA", "V1", 7L);

        assertThat(venceLogo.getQuantidadeAtual()).isEqualByComparingTo("0");   // esgotou os 3
        assertThat(venceDepois.getQuantidadeAtual()).isEqualByComparingTo("4"); // levou o 1 restante
        assertThat(produto.getEstoqueAtual()).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("saída maior que os lotes consome tudo dos lotes e o resto vem do estoque sem lote")
    void saidaMaiorQueOsLotesNaoFalha() {
        LoteEstoque unico = lote(1L, "2", LocalDate.now().plusDays(3));
        when(loteRepo.findDisponiveisParaBaixa(1L)).thenReturn(List.of(unico));

        service.saida(produto, bd("6"), "SAIDA_VENDA", "V1", 7L);   // estoque total era 10

        assertThat(unico.getQuantidadeAtual()).isEqualByComparingTo("0");
        assertThat(produto.getEstoqueAtual()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("saída sem nenhum lote só mexe no estoque (comportamento anterior preservado)")
    void saidaSemLotes() {
        when(loteRepo.findDisponiveisParaBaixa(1L)).thenReturn(List.of());

        service.saida(produto, bd("4"), "SAIDA_VENDA", "V1", 7L);

        assertThat(produto.getEstoqueAtual()).isEqualByComparingTo("6");
        verify(loteRepo, never()).save(any());
    }

    @Test
    @DisplayName("ajuste negativo (inventário) também baixa lotes; ajuste positivo cria lote só com validade padrão")
    void ajustesMexemNosLotes() {
        LoteEstoque l = lote(1L, "5", LocalDate.now().plusDays(3));
        when(loteRepo.findDisponiveisParaBaixa(1L)).thenReturn(List.of(l));

        service.ajuste(produto, bd("2"), "AJUSTE_NEGATIVO", "INV#1", 7L);
        assertThat(l.getQuantidadeAtual()).isEqualByComparingTo("3");

        service.ajuste(produto, bd("1"), "AJUSTE_POSITIVO", "INV#1", 7L);   // produto sem validade padrão
        // só houve UM save: o do lote existente na baixa. O ajuste positivo não criou lote novo.
        verify(loteRepo, times(1)).save(any(LoteEstoque.class));
    }
}
