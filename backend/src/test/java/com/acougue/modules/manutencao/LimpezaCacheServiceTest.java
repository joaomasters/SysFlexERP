package com.acougue.modules.manutencao;

import com.acougue.security.CacheDaEmpresa;
import com.acougue.security.UsuarioAutenticado;
import com.acougue.tenant.Tenant;
import com.acougue.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Limpar cache da empresa")
class LimpezaCacheServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final CacheDaEmpresa cacheA = mock(CacheDaEmpresa.class);
    private final CacheDaEmpresa cacheB = mock(CacheDaEmpresa.class);
    private final LimpezaCacheService service = new LimpezaCacheService(jdbc, tenants, List.of(cacheA, cacheB));

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void logar(boolean superAdmin, boolean jarvis) {
        UsuarioAutenticado u = new UsuarioAutenticado(5L, "dono", "Dono", "SUPER_ADMIN", superAdmin, Map.of(),
                "tenant_loja", jarvis, jarvis);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u, null));
    }

    @Test
    @DisplayName("SUPER_ADMIN sobe a versão da própria empresa e limpa todos os caches dela")
    void limpa() {
        logar(true, false);
        when(tenants.listarTodos()).thenReturn(List.of(
                new Tenant(1L, "001", "principal", "Principal", "public", true),
                new Tenant(2L, "002", "loja", "Loja", "tenant_loja", true)));
        Map<String, Object> linha = new HashMap<>();
        linha.put("versao_cache", 4L);
        linha.put("cache_limpo_em", null);
        linha.put("cache_limpo_por", "Dono");
        when(jdbc.queryForMap(contains("public.tenants"), eq("tenant_loja"))).thenReturn(linha);

        LimpezaCacheService.StatusCache status = service.limpar();

        verify(jdbc).update(contains("versao_cache = versao_cache + 1"), eq("Dono"), eq("tenant_loja"));
        verify(cacheA).limparEmpresa("tenant_loja", "002");
        verify(cacheB).limparEmpresa("tenant_loja", "002");
        assertThat(status.versao()).isEqualTo(4);
        assertThat(status.limpoPor()).isEqualTo("Dono");
    }

    @Test
    @DisplayName("quem não é SUPER_ADMIN, e o JARVIS, não limpam")
    void soSuperAdmin() {
        logar(false, false);
        assertThatThrownBy(service::limpar).isInstanceOf(AccessDeniedException.class);

        logar(true, true);
        assertThatThrownBy(service::limpar).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(jdbc, cacheA, cacheB);
    }
}
