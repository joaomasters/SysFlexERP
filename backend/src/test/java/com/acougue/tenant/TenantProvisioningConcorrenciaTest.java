package com.acougue.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Criação de empresa ao mesmo tempo")
class TenantProvisioningConcorrenciaTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TenantRepository repo = mock(TenantRepository.class);
    private final TenantProvisioningService service = new TenantProvisioningService(
            mock(DataSource.class), jdbc, repo, mock(DadosIniciaisEmpresaService.class), mock(PasswordEncoder.class));

    @Test
    @DisplayName("código já pego por outra criação: tenta o próximo, a partir do maior")
    void codigoRepetido() {
        when(repo.maiorCodigo()).thenReturn(4, 5);
        doThrow(new DuplicateKeyException("uk_tenants_codigo"))
                .when(repo).insertInativo(eq("005"), anyString(), anyString(), anyString());

        service.inserirComCodigoLivre("loja", "Loja", "tenant_loja");

        verify(repo).insertInativo("006", "loja", "Loja", "tenant_loja");
    }

    @Test
    @DisplayName("schema já existe (outra criação ou sobra antiga): usa o próximo nome")
    void schemaRepetido() {
        doThrow(new BadSqlGrammarException("create", "CREATE SCHEMA tenant_padaria", new SQLException("already exists")))
                .when(jdbc).execute("CREATE SCHEMA tenant_padaria");

        assertThat(service.reservarSchema("Padaria")).isEqualTo("padaria-2");
        verify(jdbc).execute("CREATE SCHEMA tenant_padaria_2");
    }
}
