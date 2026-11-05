package com.acougue.config;

import com.acougue.security.RestricaoJarvis;
import com.acougue.security.UsuarioAutenticado;
import com.acougue.security.ValidadorSessao;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String CABECALHO_VERSAO_CACHE = "X-Versao-Cache";

    private final JwtUtil jwtUtil;
    private final ValidadorSessao validadorSessao;

    public JwtAuthFilter(JwtUtil jwtUtil, ValidadorSessao validadorSessao) {
        this.jwtUtil = jwtUtil;
        this.validadorSessao = validadorSessao;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.isValid(token)) {
                UsuarioAutenticado usuario = null;
                try {
                    usuario = jwtUtil.extractUsuarioAutenticado(token);
                } catch (Exception ignored) {
                    // token antigo — segue sem autenticar, cai no 401
                }
                // Usuário ou empresa desativados depois do login perdem o acesso (ver ValidadorSessao)
                if (usuario != null && validadorSessao.sessaoAtiva(usuario)) {
                    String caminho = req.getRequestURI().substring(req.getContextPath().length());
                    if (!RestricaoJarvis.permitido(usuario, req.getMethod(), caminho)) {
                        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                        res.getWriter().write("{\"message\":\"O JARVIS é somente leitura: esta ação não está disponível.\"}");
                        return;
                    }
                    // O front compara com a versão que guardou; se o SUPER_ADMIN limpou o cache, recarrega
                    res.setHeader(CABECALHO_VERSAO_CACHE, String.valueOf(validadorSessao.versaoCache(usuario)));
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            usuario, null, List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(req, res);
    }
}