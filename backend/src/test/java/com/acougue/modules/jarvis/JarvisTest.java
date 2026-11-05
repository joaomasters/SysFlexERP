package com.acougue.modules.jarvis;

import com.acougue.config.JwtUtil;
import com.acougue.entity.Modulo;
import com.acougue.security.Acao;
import com.acougue.security.RestricaoJarvis;
import com.acougue.security.UsuarioAutenticado;
import com.acougue.security.ValidadorSessao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("JARVIS")
class JarvisTest {

    // Segredo do apêndice B da RFC 6238, "12345678901234567890" em Base32
    private static final String SEGREDO = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private final JwtUtil jwt = new JwtUtil("x".repeat(48));
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private UsuarioAutenticado painel() {
        return jwt.extractUsuarioAutenticado(jwt.generateTokenJarvis("tony", "public", false));
    }

    private UsuarioAutenticado visualizacao() {
        return jwt.extractUsuarioAutenticado(jwt.generateTokenJarvis("tony", "tenant_loja", true));
    }

    private String codigoAtual() {
        return Totp.gerar(Totp.decodificarBase32(SEGREDO), System.currentTimeMillis() / 1000 / 30);
    }

    @Nested
    @DisplayName("Código de verificação")
    class CodigoVerificacao {

        @Test
        @DisplayName("gera o valor do vetor de teste da RFC 6238")
        void vetorRfc() {
            assertThat(Totp.gerar("12345678901234567890".getBytes(StandardCharsets.US_ASCII), 59 / 30)).isEqualTo("287082");
            assertThat(Totp.decodificarBase32(SEGREDO)).isEqualTo("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        }

        @Test
        @DisplayName("aceita um passo de diferença no relógio e recusa dois")
        void toleranciaDoRelogio() {
            assertThat(Totp.valido(SEGREDO, "287082", 59)).isTrue();
            assertThat(Totp.valido(SEGREDO, "287082", 59 + 30)).isTrue();
            assertThat(Totp.valido(SEGREDO, "287082", 59 + 60)).isFalse();
        }

        @Test
        @DisplayName("recusa código fora do formato")
        void formato() {
            assertThat(Totp.valido(SEGREDO, null, 59)).isFalse();
            assertThat(Totp.valido(SEGREDO, "28708", 59)).isFalse();
            assertThat(Totp.valido(SEGREDO, "abcdef", 59)).isFalse();
            assertThatThrownBy(() -> Totp.decodificarBase32("1!")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Login com o código 000")
    class Login {

        private JarvisAutenticacao autenticacao(String login, String senha, String segredo) {
            return new JarvisAutenticacao(jwt, encoder, login, senha, segredo);
        }

        @Test
        @DisplayName("exige login, senha e código de verificação")
        void exigeOsTres() {
            JarvisAutenticacao a = autenticacao("tony", "senha-forte", SEGREDO);

            assertThat(a.autenticar("tony", "senha-forte", codigoAtual())).isTrue();
            assertThat(a.autenticar("outro", "senha-forte", codigoAtual())).isFalse();
            assertThat(a.autenticar("tony", "errada", codigoAtual())).isFalse();
            assertThat(a.autenticar("tony", "senha-forte", "000000")).isFalse();
            assertThat(a.autenticar("tony", "senha-forte", null)).isFalse();
        }

        @Test
        @DisplayName("fica desligado sem as variáveis ou com segredo inválido")
        void desligado() {
            assertThat(autenticacao("", "senha-forte", SEGREDO).autenticar("", "senha-forte", codigoAtual())).isFalse();
            assertThat(autenticacao("tony", "", SEGREDO).autenticar("tony", "", codigoAtual())).isFalse();
            assertThat(autenticacao("tony", "senha-forte", "").autenticar("tony", "senha-forte", codigoAtual())).isFalse();
            assertThat(autenticacao("tony", "senha-forte", "GEZDGNBV").autenticar("tony", "senha-forte", codigoAtual())).isFalse();
        }
    }

    @Nested
    @DisplayName("Token")
    class Token {

        @Test
        @DisplayName("visualização pode ver e não pode alterar, mesmo com superAdmin")
        void visualizacaoSoVe() {
            UsuarioAutenticado u = visualizacao();

            assertThat(u.isJarvis()).isTrue();
            assertThat(u.isSomenteLeitura()).isTrue();
            assertThat(u.getTenantSchema()).isEqualTo("tenant_loja");
            assertThat(u.pode(Modulo.PDV, Acao.VER)).isTrue();
            assertThat(u.pode(Modulo.PDV, Acao.CRIAR)).isFalse();
            assertThat(u.pode(Modulo.PDV, Acao.EDITAR)).isFalse();
            assertThat(u.pode(Modulo.PDV, Acao.EXCLUIR)).isFalse();
        }

        @Test
        @DisplayName("painel não tem poder nenhum sobre as telas das empresas")
        void painelSemPermissao() {
            UsuarioAutenticado u = painel();

            assertThat(u.isSuperAdmin()).isFalse();
            assertThat(u.pode(Modulo.PDV, Acao.VER)).isFalse();
        }
    }

    @Nested
    @DisplayName("Rotas permitidas")
    class Rotas {

        @Test
        @DisplayName("painel só alcança /jarvis")
        void painel() {
            UsuarioAutenticado u = JarvisTest.this.painel();

            assertThat(RestricaoJarvis.permitido(u, "GET", "/jarvis/empresas")).isTrue();
            assertThat(RestricaoJarvis.permitido(u, "POST", "/jarvis/empresas/2/visualizar")).isTrue();
            assertThat(RestricaoJarvis.permitido(u, "GET", "/estoque/produtos")).isFalse();
            assertThat(RestricaoJarvis.permitido(u, "GET", "/admin/tenants")).isFalse();
        }

        @Test
        @DisplayName("visualização só faz GET e não alcança o que é da plataforma")
        void visualizacao() {
            UsuarioAutenticado u = JarvisTest.this.visualizacao();

            assertThat(RestricaoJarvis.permitido(u, "GET", "/estoque/produtos")).isTrue();
            assertThat(RestricaoJarvis.permitido(u, "GET", "/maquininhas/provedores")).isTrue();
            for (String metodo : new String[]{"POST", "PUT", "PATCH", "DELETE"}) {
                assertThat(RestricaoJarvis.permitido(u, metodo, "/estoque/produtos")).isFalse();
            }
            assertThat(RestricaoJarvis.permitido(u, "GET", "/admin/tenants")).isFalse();
            assertThat(RestricaoJarvis.permitido(u, "GET", "/jarvis/empresas")).isFalse();
            assertThat(RestricaoJarvis.permitido(u, "GET", "/maquininhas/provedores/STONE/dispositivos")).isFalse();
            assertThat(RestricaoJarvis.permitido(u, "GET", "/balanca/agente/carga-pendente")).isFalse();
        }

        @Test
        @DisplayName("usuário de empresa não é afetado pela restrição")
        void usuarioNormal() {
            UsuarioAutenticado u = new UsuarioAutenticado(1L, "x", "X", "ADMIN", false, Map.of(), "public");

            assertThat(RestricaoJarvis.permitido(u, "POST", "/estoque/produtos")).isTrue();
        }
    }

    @Nested
    @DisplayName("Validação da sessão")
    class Sessao {

        @Test
        @DisplayName("vale para empresa inativa, sem procurar usuário no schema da empresa")
        void naoProcuraUsuario() {
            JdbcTemplate db = mock(JdbcTemplate.class);
            when(db.queryForList(contains("public.tenants"), eq(Long.class), any())).thenReturn(List.of(0L));
            ValidadorSessao validador = new ValidadorSessao(db);
            ReflectionTestUtils.setField(validador, "loginJarvis", "tony");

            assertThat(validador.sessaoAtiva(visualizacao())).isTrue();
            verify(db, never()).queryForList(contains(".usuarios"), eq(Boolean.class), any());
        }

        @Test
        @DisplayName("cai quando o acesso sai do ambiente ou o login não confere")
        void cortaSessao() {
            ValidadorSessao validador = new ValidadorSessao(mock(JdbcTemplate.class));

            assertThat(validador.sessaoAtiva(visualizacao())).isFalse();

            ReflectionTestUtils.setField(validador, "loginJarvis", "outro");
            assertThat(validador.sessaoAtiva(visualizacao())).isFalse();
        }
    }
}
