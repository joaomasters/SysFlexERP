package com.acougue.security;

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

    public ValidadorSessao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean sessaoAtiva(UsuarioAutenticado usuario) {
        String schema = usuario.getTenantSchema();
        Long usuarioId = usuario.getUsuarioId();
        if (schema == null || usuarioId == null || !SCHEMA_VALIDO.matcher(schema).matches()) {
            return false;
        }
        String chave = schema + ":" + usuarioId;
        long agora = System.currentTimeMillis();
        long[] emCache = cache.get(chave);
        if (emCache != null && emCache[1] > agora) {
            return emCache[0] == 1;
        }
        boolean ativa = consultar(schema, usuarioId);
        cache.put(chave, new long[]{ativa ? 1 : 0, agora + CACHE_MS});
        return ativa;
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
