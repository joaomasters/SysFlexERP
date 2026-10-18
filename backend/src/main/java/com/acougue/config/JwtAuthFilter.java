package com.acougue.config;

import com.acougue.security.UsuarioAutenticado;
import com.acougue.security.ValidadorSessao;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

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
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            usuario, null, List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(req, res);
    }
}