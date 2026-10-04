package com.acougue.modules.financeiro;

import com.acougue.entity.Cliente;
import com.acougue.entity.ContasAReceber;
import com.acougue.entity.ContasAReceberPagamento;
import com.acougue.entity.Usuario;
import com.acougue.modules.financeiro.dto.HistoricoContaDTO;
import com.acougue.repository.ContasAReceberPagamentoRepository;
import com.acougue.repository.ContasPagarPagamentoRepository;
import com.acougue.repository.UsuarioRepository;
import com.acougue.security.IdentificacaoUsuario;
import com.acougue.security.UsuarioAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HistoricoPagamentosService")
class HistoricoPagamentosServiceTest {

    @Mock ContasAReceberPagamentoRepository receberRepo;
    @Mock ContasPagarPagamentoRepository    pagarRepo;
    @Mock UsuarioRepository                 usuarioRepo;

    private HistoricoPagamentosService service;

    @BeforeEach
    void setUp() {
        service = new HistoricoPagamentosService(receberRepo, pagarRepo, new IdentificacaoUsuario(usuarioRepo));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(boolean superAdmin) {
        UsuarioAutenticado u = new UsuarioAutenticado(1L, "login", "Fulano",
                superAdmin ? "SUPER_ADMIN" : "CAIXA", superAdmin, Collections.emptyMap());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u, null));
    }

    @Test
    @DisplayName("quem não é administrador recebe 403 e o banco nem é consultado (receber e pagar)")
    void naoAdministradorNaoVeHistorico() {
        autenticar(false);

        assertThatThrownBy(() -> service.historicoRecebimentos(10L, null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.historicoPagamentos(null, "x", null, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(receberRepo, pagarRepo, usuarioRepo);
    }

    @Test
    @DisplayName("sem autenticação nenhuma também é barrado")
    void semAutenticacaoEBarrado() {
        assertThatThrownBy(() -> service.historicoRecebimentos(null, null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(receberRepo);
    }

    @Test
    @DisplayName("administrador: agrupa por conta, traz saldo antes/depois e nome do usuário; migração fica sem nome")
    void administradorVeHistoricoAgrupado() {
        autenticar(true);

        ContasAReceber conta = ContasAReceber.builder()
                .id(1L).descricao("Fiado João")
                .cliente(Cliente.builder().id(10L).nome("João").build())
                .valor(new BigDecimal("5000.00")).valorPago(new BigDecimal("5000.00")).status("PAGO")
                .build();

        when(receberRepo.buscarHistorico(any(LocalDateTime.class), any(LocalDateTime.class), eq(10L), isNull()))
                .thenReturn(List.of(
                        pag(conta, 1L, "1000", "5000", "4000", null, "MIGRACAO"),
                        pag(conta, 2L, "3000", "4000", "1000", 5L, "PAGAMENTO"),
                        pag(conta, 3L, "1000", "1000", "0",    5L, "PAGAMENTO")));
        when(usuarioRepo.findAllById(any())).thenReturn(
                List.of(Usuario.builder().id(5L).nome("Maria").build()));

        List<HistoricoContaDTO> resultado = service.historicoRecebimentos(10L, null, null, null);

        assertThat(resultado).hasSize(1);
        HistoricoContaDTO c = resultado.get(0);
        assertThat(c.contraparte()).isEqualTo("João");
        assertThat(c.valor()).isEqualByComparingTo("5000");
        assertThat(c.totalPago()).isEqualByComparingTo("5000");
        assertThat(c.saldo()).isEqualByComparingTo("0");
        assertThat(c.pagamentos()).hasSize(3);
        assertThat(c.pagamentos().get(0).usuarioNome()).isNull();
        assertThat(c.pagamentos().get(0).origem()).isEqualTo("MIGRACAO");
        assertThat(c.pagamentos().get(1).usuarioNome()).isEqualTo("Maria");
        assertThat(c.pagamentos().get(2).saldoPosterior()).isEqualByComparingTo("0");
    }

    private static ContasAReceberPagamento pag(ContasAReceber conta, Long id, String valor,
                                               String antes, String depois, Long usuarioId, String origem) {
        return ContasAReceberPagamento.builder()
                .id(id).conta(conta).dataPagamento(LocalDateTime.now())
                .valor(new BigDecimal(valor))
                .saldoAnterior(new BigDecimal(antes)).saldoPosterior(new BigDecimal(depois))
                .usuarioId(usuarioId).origem(origem).build();
    }
}
