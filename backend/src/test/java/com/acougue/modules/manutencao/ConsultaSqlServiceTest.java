package com.acougue.modules.manutencao;

import com.acougue.exception.BusinessException;
import com.acougue.security.UsuarioAutenticado;
import com.acougue.security.auditoria.AuditoriaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** O isolamento real (usuário do Postgres por empresa) é coberto no E2E consulta-sql.spec, contra o banco. */
@DisplayName("Consulta SQL do SUPER_ADMIN")
class ConsultaSqlServiceTest {

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("aceita SELECT e WITH, com comentário antes e ponto e vírgula no fim")
    void aceita() {
        assertThat(ConsultaSqlService.conferirTexto("  select * from produtos;  ")).isEqualTo("select * from produtos");
        assertThat(ConsultaSqlService.conferirTexto("WITH x AS (SELECT 1) SELECT * FROM x")).startsWith("WITH");
        assertThat(ConsultaSqlService.conferirTexto("-- sem NCM\nSELECT id FROM produtos WHERE ncm IS NULL")).contains("ncm IS NULL");
    }

    @Test
    @DisplayName("recusa o que não é consulta, mais de uma consulta e texto vazio")
    void recusa() {
        for (String sql : new String[]{"update produtos set ncm = null", "delete from produtos", "drop table produtos",
                "/* select */ insert into x values (1)", "select 1; delete from produtos", "", "   "}) {
            assertThatThrownBy(() -> ConsultaSqlService.conferirTexto(sql)).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> ConsultaSqlService.conferirTexto("select '" + "x".repeat(10_001) + "'"))
                .hasMessageContaining("muito longa");
    }

    @Test
    @DisplayName("colunas de segredo ficam de fora; as fiscais e a chave da nota, não")
    void colunasSecretas() {
        for (String c : List.of("senha", "senha_hash", "provedor_token_producao_cripto", "certificado_pfx_cripto", "totp_segredo", "client_secret")) {
            assertThat(ConsultaSqlService.COLUNA_SECRETA.matcher(c).matches()).as(c).isTrue();
        }
        for (String c : List.of("ncm", "chave", "nome", "login", "csosn", "cfop")) {
            assertThat(ConsultaSqlService.COLUNA_SECRETA.matcher(c).matches()).as(c).isFalse();
        }
        assertThat(ConsultaSqlService.TABELAS_FORA).contains("tenants", "flyway_schema_history");
    }

    @Test
    @DisplayName("um usuário do banco por empresa, com nome curto e estável")
    void papel() {
        String a = ConsultaSqlService.papelDaEmpresa("tenant_empresa_fbc198cd_72d5_45e4_9950_ab17509b04a6");
        assertThat(a).matches("sysflex_consulta_[0-9a-f]{16}").isEqualTo(
                ConsultaSqlService.papelDaEmpresa("tenant_empresa_fbc198cd_72d5_45e4_9950_ab17509b04a6"));
        assertThat(a).isNotEqualTo(ConsultaSqlService.papelDaEmpresa("public"));
    }

    @Test
    @DisplayName("erros do banco viram mensagem clara")
    void traduz() {
        assertThat(ConsultaSqlService.traduzir(new SQLException("permission denied for table usuarios", "42501")))
                .startsWith("Sem acesso");
        assertThat(ConsultaSqlService.traduzir(new SQLException("canceling statement", "57014"))).contains("15 segundos");
    }

    @Test
    @DisplayName("só o SUPER_ADMIN: os demais são barrados antes de tocar no banco")
    void permissao() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConsultaSqlService service = new ConsultaSqlService(jdbc, mock(DataSource.class), mock(AuditoriaService.class));
        UsuarioAutenticado admin = new UsuarioAutenticado(1L, "admin", "Admin", "ADMIN", false, Map.of(), "public");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, null, List.of()));

        assertThatThrownBy(() -> service.consultar("select 1")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc);
    }
}
