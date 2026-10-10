package com.acougue.modules.comissao;

import com.acougue.entity.Usuario;
import com.acougue.exception.BusinessException;
import com.acougue.modules.comissao.dto.ComissaoFuncionarioDTO;
import com.acougue.repository.UsuarioRepository;
import com.acougue.repository.VendaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ComissaoService")
class ComissaoServiceTest {

    @Mock UsuarioRepository usuarioRepo;
    @Mock VendaRepository   vendaRepo;

    @InjectMocks ComissaoService service;

    private static final LocalDate INICIO = LocalDate.of(2026, 10, 1);
    private static final LocalDate FIM    = LocalDate.of(2026, 10, 31);

    private Usuario usuario(long id, String nome, String percentual) {
        return Usuario.builder().id(id).nome(nome).login(nome.toLowerCase())
                .ativo(true).percentualComissao(new BigDecimal(percentual)).build();
    }

    @Test
    @DisplayName("apurar: soma vendas e comissão por funcionário, ordenando pela maior comissão")
    void apurar_somaPorFuncionario() {
        Usuario ana   = usuario(1L, "Ana", "2.00");
        Usuario bruno = usuario(2L, "Bruno", "5.00");
        when(usuarioRepo.findAll()).thenReturn(List.of(ana, bruno));
        when(vendaRepo.resumirComissoesPorOperador(any(), any())).thenReturn(List.of(
                new Object[]{1L, 3L, new BigDecimal("300.00"), new BigDecimal("6.00")},
                new Object[]{2L, 2L, new BigDecimal("200.00"), new BigDecimal("10.00")}));

        List<ComissaoFuncionarioDTO> linhas = service.apurar(INICIO, FIM);

        assertThat(linhas).extracting(ComissaoFuncionarioDTO::getNome).containsExactly("Bruno", "Ana");
        assertThat(linhas.get(0).getQuantidadeVendas()).isEqualTo(2);
        assertThat(linhas.get(0).getTotalComissao()).isEqualByComparingTo("10.00");
        assertThat(linhas.get(1).getTotalVendido()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("apurar: inclui comissionado sem vendas e omite quem não vendeu nem é comissionado")
    void apurar_filtraFuncionarios() {
        Usuario comissionadoSemVenda = usuario(1L, "Carla", "3.00");
        Usuario semNada              = usuario(2L, "Davi", "0");
        when(usuarioRepo.findAll()).thenReturn(List.of(comissionadoSemVenda, semNada));
        when(vendaRepo.resumirComissoesPorOperador(any(), any())).thenReturn(List.of());

        List<ComissaoFuncionarioDTO> linhas = service.apurar(INICIO, FIM);

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getNome()).isEqualTo("Carla");
        assertThat(linhas.get(0).getTotalComissao()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("apurar: período invertido lança BusinessException")
    void apurar_periodoInvertido() {
        assertThatThrownBy(() -> service.apurar(FIM, INICIO))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(vendaRepo);
    }

    @Test
    @DisplayName("definirPercentual: atualiza o percentual do usuário")
    void definirPercentual_atualiza() {
        Usuario ana = usuario(1L, "Ana", "0");
        when(usuarioRepo.findById(1L)).thenReturn(java.util.Optional.of(ana));
        when(usuarioRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Usuario resultado = service.definirPercentual(1L, new BigDecimal("4.50"));

        assertThat(resultado.getPercentualComissao()).isEqualByComparingTo("4.50");
    }
}
