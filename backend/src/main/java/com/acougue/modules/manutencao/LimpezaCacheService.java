package com.acougue.modules.manutencao;

import com.acougue.security.CacheDaEmpresa;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.UsuarioAutenticado;
import com.acougue.tenant.Tenant;
import com.acougue.tenant.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/*
 "Limpar cache" do SUPER_ADMIN, sempre só da empresa dele:
  - navegadores: sobe public.tenants.versao_cache; o JwtAuthFilter devolve a versão em
    toda resposta e o front que estiver com outra descarta os dados e recarrega a tela.
    Ninguém é deslogado.
  - servidor: chama cada CacheDaEmpresa (sessões em cache, bloqueios de login, token da
    Veloce) para descartar só a parte desta empresa.
 */
@Slf4j
@Service
public class LimpezaCacheService {

    private final JdbcTemplate jdbc;
    private final TenantRepository tenantRepository;
    private final List<CacheDaEmpresa> caches;

    public LimpezaCacheService(JdbcTemplate jdbc, TenantRepository tenantRepository, List<CacheDaEmpresa> caches) {
        this.jdbc = jdbc;
        this.tenantRepository = tenantRepository;
        this.caches = caches;
    }

    public record StatusCache(long versao, LocalDateTime limpoEm, String limpoPor) {}

    public StatusCache status() {
        exigirSuperAdmin();
        return ler(ContextoUsuario.atual().getTenantSchema());
    }

    public StatusCache limpar() {
        UsuarioAutenticado usuario = exigirSuperAdmin();
        String schema = usuario.getTenantSchema();
        jdbc.update("UPDATE public.tenants SET versao_cache = versao_cache + 1, cache_limpo_em = now(), "
                + "cache_limpo_por = ? WHERE schema_name = ?", usuario.getNome(), schema);
        String codigo = tenantRepository.listarTodos().stream()
                .filter(t -> t.schemaName().equals(schema))
                .map(Tenant::codigo)
                .findFirst().orElse("");
        caches.forEach(c -> c.limparEmpresa(schema, codigo));
        log.info("Cache da empresa {} limpo por {}", schema, usuario.getLogin());
        return ler(schema);
    }

    private StatusCache ler(String schema) {
        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT versao_cache, cache_limpo_em, cache_limpo_por FROM public.tenants WHERE schema_name = ?", schema);
        Object em = linha.get("cache_limpo_em");
        return new StatusCache(((Number) linha.get("versao_cache")).longValue(),
                em instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : null,
                (String) linha.get("cache_limpo_por"));
    }

    // Cada empresa tem o próprio SUPER_ADMIN; o JARVIS só olha (e já é barrado em POST)
    private static UsuarioAutenticado exigirSuperAdmin() {
        UsuarioAutenticado usuario = ContextoUsuario.atual();
        if (!usuario.isSuperAdmin() || usuario.isJarvis() || usuario.isSomenteLeitura()) {
            throw new AccessDeniedException("Apenas o SUPER_ADMIN da empresa pode limpar o cache.");
        }
        return usuario;
    }
}
