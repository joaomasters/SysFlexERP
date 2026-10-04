package com.acougue.security;

import com.acougue.entity.Modulo;
import com.acougue.entity.Usuario;
import com.acougue.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdentificacaoUsuario (regra: só administrador vê quem fez)")
class IdentificacaoUsuarioTest {

    @Mock UsuarioRepository usuarioRepo;

    private IdentificacaoUsuario identificacao;

    /** Item de teste: um registro com autoria e campo de nome (como as entidades reais). */
    static class Registro {
        Long usuarioId; String usuarioNome;
        Registro(Long usuarioId) { this.usuarioId = usuarioId; }
    }

    @BeforeEach
    void setUp() { identificacao = new IdentificacaoUsuario(usuarioRepo); }

    @AfterEach
    void limpar() { SecurityContextHolder.clearContext(); }

    private void autenticar(boolean superAdmin, Map<Modulo, UsuarioAutenticado.Permissoes> permissoes) {
        UsuarioAutenticado u = new UsuarioAutenticado(1L, "login", "Fulano",
                superAdmin ? "SUPER_ADMIN" : "OUTRO", superAdmin, permissoes);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u, null));
    }

    @Test
    @DisplayName("super admin: nomes preenchidos, com UMA consulta para vários registros")
    void superAdminRecebeNomes() {
        autenticar(true, Collections.emptyMap());
        when(usuarioRepo.findAllById(any())).thenReturn(List.of(
                Usuario.builder().id(5L).nome("Maria").build(),
                Usuario.builder().id(6L).nome("José").build()));

        List<Registro> itens = new ArrayList<>(List.of(new Registro(5L), new Registro(6L), new Registro(5L), new Registro(null)));
        identificacao.preencher(itens, r -> r.usuarioId, (r, n) -> r.usuarioNome = n);

        assertThat(itens).extracting(r -> r.usuarioNome).containsExactly("Maria", "José", "Maria", null);
        verify(usuarioRepo, times(1)).findAllById(any());
    }

    @Test
    @DisplayName("perfil com AUDITORIA.VER (ex: ADMIN) também recebe os nomes")
    void perfilComAuditoriaRecebeNomes() {
        autenticar(false, Map.of(Modulo.AUDITORIA, new UsuarioAutenticado.Permissoes(true, false, false, false)));
        when(usuarioRepo.findAllById(any())).thenReturn(List.of(Usuario.builder().id(5L).nome("Maria").build()));

        Registro r = new Registro(5L);
        identificacao.preencherItem(r, x -> x.usuarioId, (x, n) -> x.usuarioNome = n);

        assertThat(r.usuarioNome).isEqualTo("Maria");
    }

    @Test
    @DisplayName("sem permissão de auditoria: nada é preenchido e o banco nem é consultado")
    void naoAdministradorNaoRecebeNada() {
        autenticar(false, Map.of(Modulo.RECEBIMENTO, new UsuarioAutenticado.Permissoes(true, true, true, true)));

        Registro r = new Registro(5L);
        identificacao.preencher(List.of(r), x -> x.usuarioId, (x, n) -> x.usuarioNome = n);

        assertThat(r.usuarioNome).isNull();
        verifyNoInteractions(usuarioRepo);
    }

    @Test
    @DisplayName("sem usuário autenticado (job, consumer): nada vaza")
    void semAutenticacaoNaoPreenche() {
        Registro r = new Registro(5L);
        identificacao.preencher(List.of(r), x -> x.usuarioId, (x, n) -> x.usuarioNome = n);

        assertThat(r.usuarioNome).isNull();
        verifyNoInteractions(usuarioRepo);
    }

    @Test
    @DisplayName("id sem usuário correspondente vira rótulo genérico, não quebra")
    void usuarioInexistenteViraRotulo() {
        autenticar(true, Collections.emptyMap());
        when(usuarioRepo.findAllById(any())).thenReturn(List.of());

        Registro r = new Registro(99L);
        identificacao.preencherItem(r, x -> x.usuarioId, (x, n) -> x.usuarioNome = n);

        assertThat(r.usuarioNome).isEqualTo("Usuário #99");
    }
}
