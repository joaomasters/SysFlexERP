package com.acougue.security;

import com.acougue.config.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Proteções de acesso")
class ProtecoesAcessoTest {

    @Nested
    @DisplayName("Segredo do token (JWT)")
    class SegredoToken {

        @Test
        @DisplayName("não sobe sem segredo ou com segredo curto")
        void exigeSegredo() {
            assertThatThrownBy(() -> new JwtUtil("")).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET");
            assertThatThrownBy(() -> new JwtUtil("curto-demais")).isInstanceOf(IllegalStateException.class);
            assertThatCode(() -> new JwtUtil("x".repeat(48))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("token assinado com outra chave (ex.: a antiga, que era pública) é recusado")
        void recusaTokenDeOutraChave() {
            JwtUtil antigo = new JwtUtil("acougue-erp-jwt-secret-key-2024-min-256bits!!");
            JwtUtil atual = new JwtUtil("segredo-novo-de-producao-com-mais-de-32-caracteres");
            com.acougue.entity.Perfil perfil = com.acougue.entity.Perfil.builder().id(1L).nome("SUPER_ADMIN").build();
            com.acougue.entity.Usuario u = com.acougue.entity.Usuario.builder().id(1L).login("x").nome("X").perfil(perfil).build();
            String forjado = antigo.generateToken(u, List.of(), "public");

            assertThat(atual.isValid(forjado)).isFalse();
            assertThat(antigo.isValid(forjado)).isTrue();
        }
    }

    @Nested
    @DisplayName("Limite de tentativas de login")
    class Limitador {

        @Test
        @DisplayName("bloqueia depois de N erros seguidos e só para aquele login")
        void bloqueia() {
            LimitadorTentativasLogin l = new LimitadorTentativasLogin(3, 15);
            l.registrarFalha("001", "teste");
            l.registrarFalha("001", "teste");
            assertThat(l.minutosBloqueado("001", "teste")).isZero();
            l.registrarFalha("001", "TESTE "); // mesma conta (maiúsculas/espaço)
            assertThat(l.minutosBloqueado("001", "teste")).isPositive();
            assertThat(l.minutosBloqueado("002", "teste")).isZero();   // outra empresa
            assertThat(l.minutosBloqueado("001", "maria")).isZero();   // outro login
        }

        @Test
        @DisplayName("login certo zera a contagem")
        void sucessoZera() {
            LimitadorTentativasLogin l = new LimitadorTentativasLogin(3, 15);
            l.registrarFalha("001", "teste");
            l.registrarFalha("001", "teste");
            l.registrarSucesso("001", "teste");
            l.registrarFalha("001", "teste");
            assertThat(l.minutosBloqueado("001", "teste")).isZero();
        }
    }

    @Nested
    @DisplayName("Revalidação da sessão")
    class Sessao {

        private UsuarioAutenticado usuario(String schema) {
            return new UsuarioAutenticado(7L, "x", "X", "ADMIN", false, Map.of(), schema);
        }

        @Test
        @DisplayName("empresa desativada derruba a sessão")
        void empresaDesativada() {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            when(jdbc.queryForList(contains("public.tenants"), eq(Boolean.class), any())).thenReturn(List.of(false));
            assertThat(new ValidadorSessao(jdbc).sessaoAtiva(usuario("tenant_dinamus_terere"))).isFalse();
        }

        @Test
        @DisplayName("usuário desativado derruba a sessão; ativo segue")
        void usuario() {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            when(jdbc.queryForList(contains("public.tenants"), eq(Boolean.class), any())).thenReturn(List.of(true));
            when(jdbc.queryForList(contains("tenant_a.usuarios"), eq(Boolean.class), any())).thenReturn(List.of(false));
            when(jdbc.queryForList(contains("tenant_b.usuarios"), eq(Boolean.class), any())).thenReturn(List.of(true));
            ValidadorSessao v = new ValidadorSessao(jdbc);
            assertThat(v.sessaoAtiva(usuario("tenant_a"))).isFalse();
            assertThat(v.sessaoAtiva(usuario("tenant_b"))).isTrue();
        }

        @Test
        @DisplayName("schema fora do padrão nem chega a consultar o banco")
        void schemaInvalido() {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            assertThat(new ValidadorSessao(jdbc).sessaoAtiva(usuario("public; DROP TABLE usuarios"))).isFalse();
            verifyNoInteractions(jdbc);
        }

        @Test
        @DisplayName("resultado fica em cache (não consulta o banco a cada requisição)")
        void cache() {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            when(jdbc.queryForList(anyString(), eq(Boolean.class), any())).thenReturn(List.of(true));
            ValidadorSessao v = new ValidadorSessao(jdbc);
            v.sessaoAtiva(usuario("public"));
            v.sessaoAtiva(usuario("public"));
            v.sessaoAtiva(usuario("public"));
            verify(jdbc, times(2)).queryForList(anyString(), eq(Boolean.class), any()); // 1x empresa + 1x usuário
        }
    }
}
