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

    public static final String HEADER_TENANT_SLUG = "X-Tenant-Slug";

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
        String slug = req.getHeader(HEADER_TENANT_SLUG);
        if (slug != null && !slug.isBlank()) {
            return tenantRepository.findBySlug(slug.trim().toLowerCase())
                    .filter(Tenant::ativo)
                    .map(Tenant::schemaName)
                    .orElse("public");
        }

        return "public";
    }
}
