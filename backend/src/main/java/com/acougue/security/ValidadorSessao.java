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
public class ValidadorSessao {

    static final long CACHE_MS = 30_000;
    private static final Pattern SCHEMA_VALIDO = Pattern.compile("^(public|tenant_[a-z0-9_]+)$");

    private final JdbcTemplate jdbc;
    private final Map<String, long[]> cache = new ConcurrentHashMap<>(); // chave -> [ativo(1/0), expiraEm]

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
        boolean ativa = usuario.isJarvis() ? empresaExiste(schema) : consultar(schema, usuarioId);
        cache.put(chave, new long[]{ativa ? 1 : 0, agora + CACHE_MS});
        return ativa;
    }

    // O JARVIS não tem linha em usuarios e também vê empresa inativa, para dar suporte
    private boolean empresaExiste(String schema) {
        try {
            Integer total = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM public.tenants WHERE schema_name = ?", Integer.class, schema);
            return total != null && total > 0;
        } catch (Exception e) {
            return false; // na dúvida, não autentica
        }
    }

    private boolean consultar(String schema, Long usuarioId) {
        try {
            List<Boolean> empresa = jdbc.queryForList(
                    "SELECT ativo FROM public.tenants WHERE schema_name = ?", Boolean.class, schema);
            if (empresa.isEmpty() || !Boolean.TRUE.equals(empresa.get(0))) {
                return false;
            }
            // schema validado pelo padrão acima (e vem de um token assinado)
            List<Boolean> usuario = jdbc.queryForList(
                    "SELECT ativo FROM " + schema + ".usuarios WHERE id = ?", Boolean.class, usuarioId);
            return !usuario.isEmpty() && Boolean.TRUE.equals(usuario.get(0));
        } catch (Exception e) {
            return false; // na dúvida, não autentica
        }
    }
}
