package com.acougue.security.auditoria;

import com.acougue.entity.LogAuditoria;
import com.acougue.repository.LogAuditoriaRepository;
import com.acougue.security.UsuarioAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditoriaService")
class AuditoriaServiceTest {

    @Mock LogAuditoriaRepository repo;

    private AuditoriaService service;

    @AfterEach
    void limparContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(Long id, String nome, String perfil) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(id, "login.teste", nome, perfil, false, Collections.emptyMap());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(usuario, null));
    }

    @Test
    @DisplayName("registrar: grava usuário, perfil, módulo, ação e descrição do usuário autenticado no contexto")
    void registrarGravaDadosDoUsuarioAtual() {
        service = new AuditoriaService(repo);
        autenticarComo(7L, "Maria Operadora", "CAIXA");

        service.registrar("CLIENTES", "EDITAR", "atualizar #42");

        ArgumentCaptor<LogAuditoria> captor = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(repo).save(captor.capture());
        LogAuditoria salvo = captor.getValue();
        assertThat(salvo.getUsuarioId()).isEqualTo(7L);
        assertThat(salvo.getNomeUsuario()).isEqualTo("Maria Operadora");
        assertThat(salvo.getPerfilUsuario()).isEqualTo("CAIXA");
        assertThat(salvo.getModulo()).isEqualTo("CLIENTES");
        assertThat(salvo.getAcao()).isEqualTo("EDITAR");
        assertThat(salvo.getDescricao()).isEqualTo("atualizar #42");
    }

    @Test
    @DisplayName("registrar: sem usuário autenticado no contexto, engole o erro e não salva nada")
    void registrarSemUsuarioAutenticadoNaoQuebra() {
        service = new AuditoriaService(repo);
        // Propositalmente NÃO autentica ninguém no SecurityContextHolder.

        service.registrar("CLIENTES", "EDITAR", "atualizar #42");

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("registrar: uma falha ao salvar no banco não propaga pra quem chamou")
    void registrarFalhaAoSalvarNaoPropaga() {
        service = new AuditoriaService(repo);
        autenticarComo(1L, "Admin", "ADMIN");
        when(repo.save(any())).thenThrow(new RuntimeException("banco fora do ar"));

        service.registrar("USUARIOS", "EXCLUIR", "inativar #3");
        // Se chegou até aqui sem lançar, o teste passou.
    }

    @Test
    @DisplayName("listar: sem período informado, usa os últimos 7 dias até agora")
    void listarUsaPeriodoPadraoDeSeteDias() {
        service = new AuditoriaService(repo);
        when(repo.buscarComFiltros(any(), any(), any(), any(), any())).thenReturn(List.of());

        service.listar(null, null, null, null, null);

        ArgumentCaptor<LocalDateTime> inicioCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> fimCaptor    = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repo).buscarComFiltros(inicioCaptor.capture(), fimCaptor.capture(), isNull(), isNull(), isNull());

        assertThat(inicioCaptor.getValue().toLocalDate()).isEqualTo(LocalDate.now().minusDays(7));
        assertThat(fimCaptor.getValue()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    @DisplayName("listar: filtros em branco viram null pra não restringir a busca")
    void listarTransformaFiltrosEmBrancoEmNull() {
        service = new AuditoriaService(repo);
        when(repo.buscarComFiltros(any(), any(), any(), any(), any())).thenReturn(List.of());

        service.listar(LocalDate.now(), LocalDate.now(), "  ", "", 5L);

        verify(repo).buscarComFiltros(any(), any(), isNull(), isNull(), eq(5L));
    }
}