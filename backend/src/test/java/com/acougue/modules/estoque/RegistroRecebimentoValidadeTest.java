package com.acougue.modules.estoque;

import com.acougue.exception.BusinessException;
import com.acougue.modules.estoque.dto.RecebimentoDTO;
import com.acougue.repository.ProdutoRepository;
import com.acougue.repository.RecebimentoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Registro de entrada: limite de 3 anos na validade")
class RegistroRecebimentoValidadeTest {

    @Mock RecebimentoRepository recebimentoRepo;
    @Mock ProdutoRepository produtoRepo;
    @Mock EstoqueService estoqueService;
    @InjectMocks RecebimentoService service;

    private static RecebimentoDTO entradaComItens(RecebimentoDTO.ItemDTO... itens) {
        return new RecebimentoDTO("Fornecedor Teste", "123", "1", null, null, null, null, null, List.of(itens));
    }

    private static RecebimentoDTO.ItemDTO item(long produtoId, LocalDate validade) {
        return new RecebimentoDTO.ItemDTO(produtoId, new BigDecimal("2"), new BigDecimal("10"), validade);
    }

    @Test
    @DisplayName("validade acima de 3 anos é recusada e nada é gravado")
    void recusaValidadeAcimaDoLimite() {
        RecebimentoDTO dto = entradaComItens(item(1L, LocalDate.of(2500, 12, 12)));

        assertThatThrownBy(() -> service.registrar(dto, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("3 anos");

        verifyNoInteractions(recebimentoRepo, produtoRepo, estoqueService);
    }

    @Test
    @DisplayName("basta um item fora do limite para barrar a entrada inteira")
    void umItemRuimBarraTudo() {
        RecebimentoDTO dto = entradaComItens(
                item(1L, LocalDate.now().plusMonths(6)),
                item(2L, LocalDate.now().plusYears(3).plusDays(1)));

        assertThatThrownBy(() -> service.registrar(dto, 1L))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(recebimentoRepo, produtoRepo, estoqueService);
    }
}