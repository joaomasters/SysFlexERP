package com.acougue.config;

import com.acougue.entity.Modulo;
import com.acougue.entity.PerfilPermissao;
import com.acougue.entity.Usuario;
import com.acougue.security.UsuarioAutenticado;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JwtUtil {

    private static final String SECRET = "acougue-erp-jwt-secret-key-2024-min-256bits!!";
    private static final long EXPIRATION_MS = 24L * 60 * 60 * 1000; // 24h
    private final Key key = Keys.hmacShaKeyFor(SECRET.getBytes());

    /**
     Gera o token carregando identidade completa: usuário, perfil e a
     matriz de permissões daquele perfil. Isso evita ter que consultar o
     banco a cada requisição só para saber, o que esse usuário pode fazer

     Efeito colateral aceito: se a matriz de permissões do perfil mudar,
     usuários já logados só veem a mudança no próximo login (token válido
     por até 24h). Trade-off normal de JWT stateless
     */
    public String generateToken(Usuario usuario, List<PerfilPermissao> permissoes, String tenantSchema) {
        boolean superAdmin = usuario.getPerfil().isSuperAdmin();

        Map<String, Object> permissoesClaim = new HashMap<>();
        for (PerfilPermissao p : permissoes) {
            Map<String, Boolean> acoes = new HashMap<>();
            acoes.put("ver",     Boolean.TRUE.equals(p.getPodeVer()));
            acoes.put("criar",   Boolean.TRUE.equals(p.getPodeCriar()));
            acoes.put("editar",  Boolean.TRUE.equals(p.getPodeEditar()));
            acoes.put("excluir", Boolean.TRUE.equals(p.getPodeExcluir()));
            permissoesClaim.put(p.getModulo().name(), acoes);
        }

        return Jwts.builder()
                .setSubject(usuario.getLogin())
                .claim("usuarioId", usuario.getId())
                .claim("nome", usuario.getNome())
                .claim("perfil", usuario.getPerfil().getNome())
                .claim("superAdmin", superAdmin)
                .claim("permissoes", permissoesClaim)
                .claim("tenantSchema", tenantSchema)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    //Reconstrói a identidade completa a partir das claims do token — usado pelo JwtAuthFilter
    @SuppressWarnings("unchecked")
    public UsuarioAutenticado extractUsuarioAutenticado(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();

        Map<Modulo, UsuarioAutenticado.Permissoes> permissoes = new HashMap<>();
        Object rawPermissoes = claims.get("permissoes");
        if (rawPermissoes instanceof Map<?, ?> mapa) {
            for (Map.Entry<?, ?> entry : mapa.entrySet()) {
                try {
                    Modulo modulo = Modulo.valueOf((String) entry.getKey());
                    Map<String, Boolean> acoes = (Map<String, Boolean>) entry.getValue();
                    permissoes.put(modulo, new UsuarioAutenticado.Permissoes(
                            Boolean.TRUE.equals(acoes.get("ver")),
                            Boolean.TRUE.equals(acoes.get("criar")),
                            Boolean.TRUE.equals(acoes.get("editar")),
                            Boolean.TRUE.equals(acoes.get("excluir"))
                    ));
                } catch (IllegalArgumentException ignored) {
                    // módulo desconhecido gravado num token antigo — ignora
                }
            }
        }

        // Fallback "public": tokens emitidos antes da claim tenantSchema existir
        // continuam válidos até expirar (24h), resolvendo pro único tenant de hoje.
        Object tenantSchemaClaim = claims.get("tenantSchema");
        String tenantSchema = tenantSchemaClaim != null ? (String) tenantSchemaClaim : "public";

        return new UsuarioAutenticado(
                ((Number) claims.get("usuarioId")).longValue(),
                claims.getSubject(),
                (String) claims.get("nome"),
                (String) claims.get("perfil"),
                Boolean.TRUE.equals(claims.get("superAdmin")),
                permissoes,
                tenantSchema
        );
    }
}