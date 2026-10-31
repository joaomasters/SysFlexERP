package com.acougue.tenant;

import com.acougue.security.UsuarioAutenticado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Gestão de empresas — quem pode e onde a limpeza pode rodar")
class GestaoEmpresasTest {

    private UsuarioAutenticado usuario(boolean superAdmin, String schema) {
        return new UsuarioAutenticado(1L, "x", "X", superAdmin ? "SUPER_ADMIN" : "ADMIN", superAdmin, Map.of(), schema);
    }

    @Test
    @DisplayName("só o super admin da empresa principal administra a plataforma")
    void administradorPlataforma() {
        assertThat(TenantAdminController.ehAdministradorPlataforma(usuario(true, "public"))).isTrue();
        // dono de outra empresa (o SUPER_ADMIN dela) NÃO gerencia as empresas dos outros
        assertThat(TenantAdminController.ehAdministradorPlataforma(usuario(true, "tenant_dinamus_terere"))).isFalse();
        assertThat(TenantAdminController.ehAdministradorPlataforma(usuario(false, "public"))).isFalse();
    }

    @Test
    @DisplayName("limpeza nunca roda na empresa principal nem em schema fora do padrão")
    void schemaProtegido() {
        assertThatThrownBy(() -> DadosIniciaisEmpresaService.validarSchema("public"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DadosIniciaisEmpresaService.validarSchema(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DadosIniciaisEmpresaService.validarSchema("tenant_x; DROP SCHEMA public"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DadosIniciaisEmpresaService.validarSchema("pg_catalog"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> DadosIniciaisEmpresaService.validarSchema("tenant_dinamus_terere"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("renomear: tira espaços sobrando e recusa nome vazio ou longo demais")
    void normalizarNomeEmpresa() {
        assertThat(TenantAdminController.normalizarNome("  Açougue   do  Zé ")).isEqualTo("Açougue do Zé");
        assertThatThrownBy(() -> TenantAdminController.normalizarNome("   "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Informe o nome");
        assertThatThrownBy(() -> TenantAdminController.normalizarNome(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantAdminController.normalizarNome("x".repeat(121)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("120");
        assertThat(TenantAdminController.normalizarNome("x".repeat(120))).hasSize(120);
    }

    @Test
    @DisplayName("usuários, perfis e controle de migrations nunca são apagados")
    void tabelasMantidas() {
        assertThat(DadosIniciaisEmpresaService.TABELAS_MANTIDAS)
                .contains("usuarios", "perfis", "perfil_permissoes", "flyway_schema_history")
                .doesNotContain("produtos", "vendas", "clientes", "caixa");
    }
}