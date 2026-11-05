package com.acougue.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/*
 O token de login vale horas e carrega a identidade inteira (stateless). Sem
 esta checagem, desativar um usuário — ou uma EMPRESA inteira — não tinha
 efeito até o token expirar: quem já estava logado continuava usando.

 A cada requisição conferimos se a empresa e o usuário do token continuam
 ativos. O resultado fica em cache por poucos segundos para não consultar o
 banco em toda chamada do PDV; desativar passa a valer em até CACHE_MS.
 */
@Component
public class ValidadorSessao implements CacheDaEmpresa {

    static final long CACHE_MS = 30_000;
    private static final Pattern SCHEMA_VALIDO = Pattern.compile("^(public|tenant_[a-z0-9_]+)$");

    private final JdbcTemplate jdbc;
    // chave -> [ativo(1/0), expiraEm, versaoCache]. A versão vem na mesma consulta, sem custo extra.
    private final Map<String, long[]> cache = new ConcurrentHashMap<>();

    @Value("${jarvis.login:}")
    private String loginJarvis = "";

    public ValidadorSessao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean sessaoAtiva(UsuarioAutenticado usuario) {
        String schema = usuario.getTenantSchema();
        Long usuarioId = usuario.getUsuarioId();
        if (schema == null || usuarioId == null || !SCHEMA_VALIDO.matcher(schema).matches()) {
            return false;
        }
        if (usuario.isJarvis() && (loginJarvis.isBlank() || !loginJarvis.trim().equals(usuario.getLogin()))) {
            return false; // tirar JARVIS_LOGIN do ambiente corta as sessões abertas
        }
        String chave = schema + ":" + usuarioId;
        long agora = System.currentTimeMillis();
        long[] emCache = cache.get(chave);
        if (emCache != null && emCache[1] > agora) {
            return emCache[0] == 1;
        }
        long[] r = usuario.isJarvis() ? empresaExiste(schema) : consultar(schema, usuarioId);
        cache.put(chave, new long[]{r[0], agora + CACHE_MS, r[1]});
        return r[0] == 1;
    }

    /** Versão do cache da empresa lida na última checagem desta sessão (ver JwtAuthFilter). */
    public long versaoCache(UsuarioAutenticado usuario) {
        long[] emCache = cache.get(usuario.getTenantSchema() + ":" + usuario.getUsuarioId());
        return emCache != null ? emCache[2] : 0;
    }

    /** Próxima requisição de cada usuário da empresa relê o banco e já pega a versão nova. */
    @Override
    public void limparEmpresa(String schema, String codigo) {
        cache.keySet().removeIf(chave -> chave.startsWith(schema + ":"));
    }

    // O JARVIS não tem linha em usuarios e também vê empresa inativa, para dar suporte
    private long[] empresaExiste(String schema) {
        try {
            List<Long> versao = jdbc.queryForList(
                    "SELECT versao_cache FROM public.tenants WHERE schema_name = ?", Long.class, schema);
            return versao.isEmpty() ? new long[]{0, 0} : new long[]{1, versao.get(0)};
        } catch (Exception e) {
            return new long[]{0, 0}; // na dúvida, não autentica
        }
    }

    private long[] consultar(String schema, Long usuarioId) {
        try {
            List<Map<String, Object>> empresa = jdbc.queryForList(
                    "SELECT ativo, versao_cache FROM public.tenants WHERE schema_name = ?", schema);
            if (empresa.isEmpty() || !Boolean.TRUE.equals(empresa.get(0).get("ativo"))) {
                return new long[]{0, 0};
            }
            long versao = ((Number) empresa.get(0).get("versao_cache")).longValue();
            // schema validado pelo padrão acima (e vem de um token assinado)
            List<Boolean> usuario = jdbc.queryForList(
                    "SELECT ativo FROM " + schema + ".usuarios WHERE id = ?", Boolean.class, usuarioId);
            boolean ativo = !usuario.isEmpty() && Boolean.TRUE.equals(usuario.get(0));
            return new long[]{ativo ? 1 : 0, versao};
        } catch (Exception e) {
            return new long[]{0, 0}; // na dúvida, não autentica
        }
    }
}
