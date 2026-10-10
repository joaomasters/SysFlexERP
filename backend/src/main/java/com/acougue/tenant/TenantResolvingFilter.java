package com.acougue.tenant;

import com.acougue.config.JwtUtil;
import com.acougue.security.UsuarioAutenticado;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/*
 Precisa ser o PRIMEIRO filtro da aplicação — registrado com Ordered.
 HIGHEST_PRECEDENCE em MultiTenancyConfig, rodando antes até do Open
 Session In View do Spring. Motivo: o Hibernate resolve o tenant da sessão
 NO MOMENTO em que ela é aberta, e com OSIV isso acontece bem no início da
 cadeia de filtros — antes até da cadeia de autenticação do Spring Security
 (onde fica o JwtAuthFilter). Se o TenantContext só fosse setado ali dentro,
 toda sessão já teria sido aberta presa no schema "public" (default), e a
 troca de tenant simplesmente não teria efeito nenhum nas queries.

 Faz a mesma extração de JWT que o JwtAuthFilter (não autentica nada, só lê
 o tenant), porque os dois rodam em momentos diferentes da cadeia e com
 objetivos diferentes: este aqui só prepara o TenantContext; a autenticação
 de verdade continua sendo responsabilidade exclusiva do JwtAuthFilter.
 */
public class TenantResolvingFilter extends OncePerRequestFilter {

    public static final String HEADER_TENANT_CODIGO = "X-Tenant-Codigo";

    // Schema garantidamente inexistente — nunca cai em public por engano
    // quando o código informado está errado (ver AuthController, que é
    // quem efetivamente barra o login com 401 antes de qualquer query rodar
    // contra este schema fantasma).
    private static final String SCHEMA_CODIGO_INVALIDO = "__codigo_invalido__";

    private final JwtUtil jwtUtil;
    private final TenantRepository tenantRepository;

    public TenantResolvingFilter(JwtUtil jwtUtil, TenantRepository tenantRepository) {
        this.jwtUtil = jwtUtil;
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            TenantContext.set(resolverSchema(req));
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolverSchema(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.isValid(token)) {
                try {
                    UsuarioAutenticado usuario = jwtUtil.extractUsuarioAutenticado(token);
                    return usuario.getTenantSchema();
                } catch (Exception ignored) {
                    // token antigo/inválido — JwtAuthFilter trata o 401 depois
                }
            }
        }

        // Sem token (ex: /auth/login): tenant vem do header, não do corpo —
        // o corpo da requisição não está disponível nesta altura da cadeia
        // sem consumir o InputStream, e o OSIV já abre sessão antes do
        // AuthController rodar.
        String codigo = req.getHeader(HEADER_TENANT_CODIGO);
        if (codigo != null && !codigo.isBlank()) {
            return tenantRepository.findByCodigo(codigo.trim())
                    .filter(Tenant::ativo)
                    .map(Tenant::schemaName)
                    .orElse(SCHEMA_CODIGO_INVALIDO);
        }

        // Nenhum código: endpoints que não envolvem tenant (healthcheck,
        // webhook do PIX) continuam resolvendo pro schema public. O
        // /auth/login em si EXIGE o código — quem barra isso com um 401
        // limpo é o AuthController, não este filtro.
        return "public";
    }
}
