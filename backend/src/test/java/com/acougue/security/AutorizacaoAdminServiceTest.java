package com.acougue.security;

import com.acougue.entity.Modulo;
import com.acougue.entity.Perfil;
import com.acougue.entity.PerfilPermissao;
import com.acougue.entity.Usuario;
import com.acougue.exception.BusinessException;
import com.acougue.repository.PerfilPermissaoRepository;
import com.acougue.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutorizacaoAdminService")
class AutorizacaoAdminServiceTest {

    @Mock UsuarioRepository usuarioRepo;
    @Mock PerfilPermissaoRepository permissaoRepo;

    // BCrypt de verdade (custo baixo) e limitador de verdade: 3 erros bloqueiam
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private LimitadorTentativasLogin limitador;
    private AutorizacaoAdminService service;

    @BeforeEach
    void setUp() {
        limitador = new LimitadorTentativasLogin(3, 10);
        service = new AutorizacaoAdminService(usuarioRepo, permissaoRepo, encoder, limitador);
    }

    private Usuario usuario(String login, String senha, String perfilNome, boolean ativo) {
        Perfil perfil = Perfil.builder().id(2L).nome(perfilNome).build();
        return Usuario.builder().id(99L).nome("Fulano").login(login)
                .senhaHash(encoder.encode(senha)).perfil(perfil).ativo(ativo).build();
    }

    @Test
    @DisplayName("SUPER_ADMIN com senha correta autoriza")
    void superAdminAutoriza() {
        Usuario admin = usuario("admin", "segredo", Perfil.SUPER_ADMIN, true);
        when(usuarioRepo.findByLogin("admin")).thenReturn(Optional.of(admin));

        assertThat(service.validar(10L, "admin", "segredo")).isSameAs(admin);
    }

    @Test
    @DisplayName("perfil que pode ver Auditoria (ex: ADMIN) também autoriza")
    void perfilComAuditoriaAutoriza() {
        Usuario gerente = usuario("gerente", "segredo", "ADMIN", true);
        when(usuarioRepo.findByLogin("gerente")).thenReturn(Optional.of(gerente));
        when(permissaoRepo.findByPerfilIdAndModulo(2L, Modulo.AUDITORIA))
                .thenReturn(Optional.of(PerfilPermissao.builder().modulo(Modulo.AUDITORIA).podeVer(true).build()));

        assertThat(service.validar(10L, "gerente", "segredo")).isSameAs(gerente);
    }

    @Test
    @DisplayName("senha correta de quem NÃO é administrador é recusada")
    void naoAdministrador() {
        Usuario caixa = usuario("maria", "segredo", "CAIXA", true);
        when(usuarioRepo.findByLogin("maria")).thenReturn(Optional.of(caixa));
        when(permissaoRepo.findByPerfilIdAndModulo(2L, Modulo.AUDITORIA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validar(10L, "maria", "segredo"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não é administrador");
    }

    @Test
    @DisplayName("perfil com Auditoria sem permissão de ver não conta como administrador")
    void auditoriaSemVer() {
        Usuario u = usuario("joao", "segredo", "FISCAL", true);
        when(usuarioRepo.findByLogin("joao")).thenReturn(Optional.of(u));
        when(permissaoRepo.findByPerfilIdAndModulo(2L, Modulo.AUDITORIA))
                .thenReturn(Optional.of(PerfilPermissao.builder().modulo(Modulo.AUDITORIA).podeVer(false).build()));

        assertThatThrownBy(() -> service.validar(10L, "joao", "segredo"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("senha errada: mensagem genérica")
    void senhaErrada() {
        when(usuarioRepo.findByLogin("admin")).thenReturn(Optional.of(usuario("admin", "segredo", Perfil.SUPER_ADMIN, true)));

        assertThatThrownBy(() -> service.validar(10L, "admin", "outra"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Login ou senha de administrador inválidos.");
    }

    @Test
    @DisplayName("usuário inexistente: mesma mensagem da senha errada")
    void usuarioInexistente() {
        when(usuarioRepo.findByLogin("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validar(10L, "fantasma", "x"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Login ou senha de administrador inválidos.");
    }

    @Test
    @DisplayName("usuário inativo: recusado mesmo com a senha certa")
    void usuarioInativo() {
        when(usuarioRepo.findByLogin("admin")).thenReturn(Optional.of(usuario("admin", "segredo", Perfil.SUPER_ADMIN, false)));

        assertThatThrownBy(() -> service.validar(10L, "admin", "segredo"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Login ou senha de administrador inválidos.");
    }

    @Test
    @DisplayName("login e senha em branco são recusados sem consultar o banco")
    void emBranco() {
        assertThatThrownBy(() -> service.validar(10L, "  ", ""))
                .isInstanceOf(BusinessException.class);
        verify(usuarioRepo, never()).findByLogin(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("depois de 3 erros o operador fica bloqueado, mesmo digitando a senha certa")
    void bloqueioPorTentativas() {
        when(usuarioRepo.findByLogin("admin")).thenReturn(Optional.of(usuario("admin", "segredo", Perfil.SUPER_ADMIN, true)));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.validar(10L, "admin", "errada")).isInstanceOf(BusinessException.class);
        }

        assertThatThrownBy(() -> service.validar(10L, "admin", "segredo"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Muitas tentativas");
    }

    @Test
    @DisplayName("o bloqueio de um operador não atinge outro operador")
    void bloqueioEPorOperador() {
        Usuario admin = usuario("admin", "segredo", Perfil.SUPER_ADMIN, true);
        when(usuarioRepo.findByLogin("admin")).thenReturn(Optional.of(admin));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.validar(10L, "admin", "errada")).isInstanceOf(BusinessException.class);
        }

        assertThat(service.validar(11L, "admin", "segredo")).isSameAs(admin);
    }

    @Test
    @DisplayName("errar aqui não bloqueia o LOGIN do administrador (espaços de chaves separados)")
    void naoBloqueiaOLogin() {
        when(usuarioRepo.findByLogin("admin")).thenReturn(Optional.of(usuario("admin", "segredo", Perfil.SUPER_ADMIN, true)));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.validar(10L, "admin", "errada")).isInstanceOf(BusinessException.class);
        }

        assertThat(limitador.minutosBloqueado("public", "admin")).isZero();
    }

    @Test
    @DisplayName("acerto zera a contagem de erros")
    void acertoZeraContagem() {
        Usuario admin = usuario("admin", "segredo", Perfil.SUPER_ADMIN, true);
        when(usuarioRepo.findByLogin("admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.validar(10L, "admin", "errada")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.validar(10L, "admin", "errada")).isInstanceOf(BusinessException.class);
        service.validar(10L, "admin", "segredo");
        // se não zerasse, este seria o 3º erro e o próximo já bloquearia
        assertThatThrownBy(() -> service.validar(10L, "admin", "errada")).isInstanceOf(BusinessException.class);
        assertThat(service.validar(10L, "admin", "segredo")).isSameAs(admin);
    }
}