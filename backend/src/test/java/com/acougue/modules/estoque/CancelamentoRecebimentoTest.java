package com.acougue.modules.estoque;

import com.acougue.entity.Produto;
import com.acougue.entity.RecebimentoItem;
import com.acougue.entity.RecebimentoMercadoria;
import com.acougue.exception.BusinessException;
import com.acougue.repository.ProdutoRepository;
import com.acougue.repository.RecebimentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Cancelamento de entrada de mercadoria")
class CancelamentoRecebimentoTest {

    @Mock RecebimentoRepository recebimentoRepo;
    @Mock ProdutoRepository produtoRepo;
    @Mock EstoqueService estoqueService;
    @InjectMocks RecebimentoService service;

    private Produto erva;

    @BeforeEach
    void setUp() {
        erva = Produto.builder().id(9L).nome("Erva Trots").estoqueAtual(new BigDecimal("10")).build();
    }

    private RecebimentoMercadoria recebimento(LocalDateTime quando, BigDecimal qtd) {
        RecebimentoMercadoria rec = RecebimentoMercadoria.builder()
                .id(5L).numeroNf("123").status("CONFERIDO").dataRecebimento(quando).build();
        rec.getItens().add(RecebimentoItem.builder().recebimento(rec).produto(erva)
                .quantidade(qtd).custoUnitario(new BigDecimal("39.90")).build());
        return rec;
    }

    @Test
    @DisplayName("dentro de 7 dias: estorna cada item e marca CANCELADO com quem, quando e motivo")
    void cancelaDentroDoPrazo() {
        RecebimentoMercadoria rec = recebimento(LocalDateTime.now().minusDays(6), new BigDecimal("3"));
        when(recebimentoRepo.findById(5L)).thenReturn(Optional.of(rec));
        when(recebimentoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecebimentoMercadoria r = service.cancelar(5L, "Entrada sem data de validade", 7L);

        assertThat(r.getStatus()).isEqualTo("CANCELADO");
        assertThat(r.getCanceladoPorId()).isEqualTo(7L);
        assertThat(r.getMotivoCancelamento()).isEqualTo("Entrada sem data de validade");
        assertThat(r.getCanceladoEm()).isNotNull();
        assertThat(r.getCancelavelAte()).isNull();
        // procura o lote pelo documento novo (único) e pelo antigo
        verify(estoqueService).estornarEntrada(eq(erva), eq(new BigDecimal("3")), eq(new BigDecimal("39.90")),
                eq(List.of("NF123/REC5", "NF123")), eq("CANC-REC5"), eq(7L));
    }

    @Test
    @DisplayName("depois de 7 dias: recusa, sem tocar no estoque")
    void foraDoPrazo() {
        when(recebimentoRepo.findById(5L)).thenReturn(Optional.of(
                recebimento(LocalDateTime.now().minusDays(8), new BigDecimal("3"))));

        assertThatThrownBy(() -> service.cancelar(5L, "Entrada sem validade", 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Prazo de cancelamento encerrado");
        verifyNoInteractions(estoqueService);
    }

    @Test
    @DisplayName("se parte já saiu do estoque, recusa e diz qual produto")
    void semSaldo() {
        when(recebimentoRepo.findById(5L)).thenReturn(Optional.of(
                recebimento(LocalDateTime.now().minusDays(1), new BigDecimal("15"))));

        assertThatThrownBy(() -> service.cancelar(5L, "Entrada sem validade", 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Erva Trots");
        verifyNoInteractions(estoqueService);
    }

    @Test
    @DisplayName("exige motivo e não cancela duas vezes")
    void motivoEDuplicidade() {
        RecebimentoMercadoria rec = recebimento(LocalDateTime.now(), BigDecimal.ONE);
        when(recebimentoRepo.findById(5L)).thenReturn(Optional.of(rec));
        assertThatThrownBy(() -> service.cancelar(5L, " ", 7L)).hasMessageContaining("motivo");

        rec.setStatus("CANCELADO");
        assertThatThrownBy(() -> service.cancelar(5L, "Entrada sem validade", 7L)).hasMessageContaining("já foi cancelada");
        verifyNoInteractions(estoqueService);
    }

    @Test
    @DisplayName("custo médio: tira o peso da entrada cancelada")
    void custoMedio() {
        // tinha 10 un a R$ 10 e entraram 10 un a R$ 20 → médio R$ 15 com 20 un.
        // Cancelando a entrada de 10 a R$ 20, volta a R$ 10.
        assertThat(EstoqueService.custoMedioSemEntrada(new BigDecimal("20"), new BigDecimal("15"),
                new BigDecimal("10"), new BigDecimal("20"))).isEqualByComparingTo("10");
        // estoque zera com o cancelamento: mantém o último custo conhecido
        assertThat(EstoqueService.custoMedioSemEntrada(new BigDecimal("10"), new BigDecimal("20"),
                new BigDecimal("10"), new BigDecimal("20"))).isEqualByComparingTo("20");
        // entrada sem custo informado não mexe no custo
        assertThat(EstoqueService.custoMedioSemEntrada(new BigDecimal("20"), new BigDecimal("15"),
                new BigDecimal("10"), BigDecimal.ZERO)).isEqualByComparingTo("15");
    }
}
