package com.acougue.security;

import com.acougue.entity.Modulo;
import com.acougue.entity.PagamentoPix;
import com.acougue.exception.BusinessException;
import com.acougue.modules.acesso.PerfilService;
import com.acougue.modules.pagamento.CredenciaisPagamentoService;
import com.acougue.modules.pdv.PixService;
import com.acougue.repository.PagamentoPixRepository;
import com.acougue.repository.PerfilPermissaoRepository;
import com.acougue.repository.PerfilRepository;
import com.acougue.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@DisplayName("Brechas fechadas")
class BrechasFechadasTest {

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private static void logar(Map<Modulo, UsuarioAutenticado.Permissoes> permissoes) {
        UsuarioAutenticado u = new UsuarioAutenticado(9L, "caixa", "Caixa", "CAIXA", false, permissoes, "tenant_a");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u, null));
    }

    @Nested
    @DisplayName("Limite de requisições")
    class Limite {

        private MockHttpServletResponse chamar(LimitadorRequisicoes f, String caminho) throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", caminho);
            MockHttpServletResponse res = new MockHttpServletResponse();
            f.doFilter(req, res, new MockFilterChain());
            return res;
        }

        @Test
        @DisplayName("usuário logado passa do limite e recebe 429; a janela seguinte libera")
        void porUsuario() throws Exception {
            LimitadorRequisicoes f = new LimitadorRequisicoes(2, 100);
            logar(Map.of());
            assertThat(chamar(f, "/pdv/vendas").getStatus()).isEqualTo(200);
            assertThat(chamar(f, "/pdv/vendas").getStatus()).isEqualTo(200);
            MockHttpServletResponse bloqueada = chamar(f, "/pdv/vendas");
            assertThat(bloqueada.getStatus()).isEqualTo(429);
            assertThat(bloqueada.getContentAsString()).contains("Aguarde");

            long depois = System.currentTimeMillis() + LimitadorRequisicoes.JANELA_MS + 1;
            assertThat(f.permitir("u:tenant_a:9", 2, depois)).isTrue();
        }

        @Test
        @DisplayName("webhook sem login tem teto por rota; outras rotas sem login não contam")
        void webhook() throws Exception {
            LimitadorRequisicoes f = new LimitadorRequisicoes(100, 1);
            assertThat(chamar(f, "/pagamento/webhook/veloce").getStatus()).isEqualTo(200);
            assertThat(chamar(f, "/pagamento/webhook/veloce").getStatus()).isEqualTo(429);
            assertThat(chamar(f, "/pagamento/webhook/stone").getStatus()).isEqualTo(200);
            assertThat(chamar(f, "/auth/login").getStatus()).isEqualTo(200);
            assertThat(chamar(f, "/auth/login").getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("PIX do Mercado Pago")
    class Pix {

        private final PagamentoPixRepository repo = mock(PagamentoPixRepository.class);
        private final CredenciaisPagamentoService credenciais = mock(CredenciaisPagamentoService.class);
        private final PixService pix = new PixService(repo, new ObjectMapper(), credenciais);

        private PagamentoPix cobranca(String status) {
            return PagamentoPix.builder().mpPaymentId(555L).vendaId(7L).valor(new BigDecimal("10.00")).status(status).build();
        }

        @Test
        @DisplayName("id fora do formato ou de outra empresa nem chega ao Mercado Pago")
        void idInvalido() {
            assertThat(pix.verificarStatus("../users/me")).containsEntry("status", "ERRO");
            when(repo.findByMpPaymentId(anyLong())).thenReturn(Optional.empty());
            assertThat(pix.verificarStatus("123")).containsEntry("status", "ERRO");
            verifyNoInteractions(credenciais); // o token da loja nem é lido
        }

        @Test
        @DisplayName("fechamento só aceita o PIX desta venda, pago e do mesmo valor")
        void fechamento() {
            assertThatThrownBy(() -> pix.validarParaPagamento(null, 7L, BigDecimal.TEN))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Gere o QR");

            when(repo.findByMpPaymentId(555L)).thenReturn(Optional.of(cobranca("APROVADO")));
            assertThatThrownBy(() -> pix.validarParaPagamento("555", 8L, BigDecimal.TEN))
                    .hasMessageContaining("outra venda");
            assertThatThrownBy(() -> pix.validarParaPagamento("555", 7L, new BigDecimal("12.00")))
                    .hasMessageContaining("difere");
            assertThatCode(() -> pix.validarParaPagamento("555", 7L, new BigDecimal("10.00")))
                    .doesNotThrowAnyException();

            when(repo.findByMpPaymentId(555L)).thenReturn(Optional.of(cobranca("PENDENTE")));
            when(credenciais.tokenMercadoPago()).thenReturn(Optional.empty()); // sem token: não consulta
            assertThatThrownBy(() -> pix.validarParaPagamento("555", 7L, new BigDecimal("10.00")))
                    .hasMessageContaining("ainda não foi pago");
        }
    }

    @Test
    @DisplayName("perfis e permissões só para quem cuida de Perfis ou de Usuários")
    void perfis() {
        PerfilService service = new PerfilService(mock(PerfilRepository.class), mock(PerfilPermissaoRepository.class),
                mock(UsuarioRepository.class));
        logar(Map.of());
        assertThatThrownBy(service::listar).isInstanceOf(AccessDeniedException.class);

        logar(Map.of(Modulo.USUARIOS, new UsuarioAutenticado.Permissoes(true, false, false, false)));
        assertThatCode(service::listar).doesNotThrowAnyException();
    }
}
