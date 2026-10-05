package com.acougue.modules.estoque;

import com.acougue.entity.LoteEstoque;
import com.acougue.entity.Produto;
import com.acougue.modules.estoque.dto.LoteValidadeDTO;
import com.acougue.repository.LoteEstoqueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidadeService")
class ValidadeServiceTest {

    @Mock LoteEstoqueRepository loteRepo;

    private static LoteEstoque lote(long id, int diasParaVencer) {
        Produto p = Produto.builder().id(id).nome("Produto " + id).unidadeMedida("KG").build();
        return LoteEstoque.builder().id(id).produto(p)
                .dataValidade(LocalDate.now().plusDays(diasParaVencer))
                .quantidadeInicial(new BigDecimal("5")).quantidadeAtual(new BigDecimal("5"))
                .build();
    }

    @Test
    @DisplayName("classifica: vencido, vence hoje, no limite da janela, fora da janela")
    void classificaPelaJanelaDeAlerta() {
        ValidadeService service = new ValidadeService(loteRepo, 3);
        when(loteRepo.findAllDisponiveis()).thenReturn(List.of(
                lote(1, -2),   // venceu há 2 dias
                lote(2, 0),    // vence hoje
                lote(3, 3),    // exatamente no limite da janela
                lote(4, 4)));  // fora da janela

        List<LoteValidadeDTO> todos = service.listar();

        assertThat(todos).extracting(LoteValidadeDTO::situacao)
                .containsExactly("VENCIDO", "VENCE_EM_BREVE", "VENCE_EM_BREVE", "OK");
        assertThat(todos).extracting(LoteValidadeDTO::diasRestantes)
                .containsExactly(-2L, 0L, 3L, 4L);
    }

    @Test
    @DisplayName("alertas traz só vencidos e vencendo em breve")
    void alertasExcluiOk() {
        ValidadeService service = new ValidadeService(loteRepo, 3);
        when(loteRepo.findAllDisponiveis()).thenReturn(List.of(lote(1, -1), lote(2, 2), lote(3, 30)));

        assertThat(service.alertas()).extracting(LoteValidadeDTO::loteId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("a janela de alerta é configurável")
    void janelaConfiguravel() {
        ValidadeService service = new ValidadeService(loteRepo, 10);
        when(loteRepo.findAllDisponiveis()).thenReturn(List.of(lote(1, 8), lote(2, 11)));

        assertThat(service.alertas()).extracting(LoteValidadeDTO::loteId).containsExactly(1L);
    }
}
