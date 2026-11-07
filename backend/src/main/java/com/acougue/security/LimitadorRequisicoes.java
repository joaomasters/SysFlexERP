package com.acougue.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 Limite de requisições por minuto (429 ao passar):
  - logado: por usuário (empresa + id), que vem do token assinado e não dá para forjar.
    Segura um token roubado ou um script martelando a API; o PDV normal usa uma fração.
  - webhooks das adquirentes (sem login): um teto geral por rota. O IP não serve aqui:
    tudo chega pelo proxy do Vercel com o mesmo IP, e o X-Forwarded-For é forjável.
 O login já tem o próprio limite por empresa + usuário (LimitadorTentativasLogin).

 Em memória e por instância, como o LimitadorTentativasLogin. Janela fixa de 1 minuto.
 Não é @Component: é criado no SecurityConfig e roda só dentro da cadeia de segurança,
 depois do JwtAuthFilter.
 */
public class LimitadorRequisicoes extends OncePerRequestFilter {

    static final long JANELA_MS = 60_000;
    private static final int MAX_CHAVES = 50_000;

    private final int porUsuario;
    private final int porWebhook;
    private final Map<String, long[]> contagem = new ConcurrentHashMap<>(); // chave -> [inicioJanela, total]

    public LimitadorRequisicoes(int porUsuarioMinuto, int porWebhookMinuto) {
        this.porUsuario = porUsuarioMinuto;
        this.porWebhook = porWebhookMinuto;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String caminho = req.getRequestURI().substring(req.getContextPath().length());
        String chave = null;
        int limite = 0;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u) {
            chave = "u:" + u.getTenantSchema() + ":" + u.getUsuarioId() + (u.isJarvis() ? ":jarvis" : "");
            limite = porUsuario;
        } else if (caminho.startsWith("/pagamento/webhook/") || caminho.equals("/pagamento/pix/webhook")) {
            chave = "w:" + caminho;
            limite = porWebhook;
        }
        if (chave != null && !permitir(chave, limite, System.currentTimeMillis())) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
            res.getWriter().write("{\"message\":\"Muitas requisições em pouco tempo. Aguarde um minuto e tente de novo.\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    boolean permitir(String chave, int limite, long agora) {
        if (contagem.size() > MAX_CHAVES) {
            contagem.entrySet().removeIf(e -> e.getValue()[0] + JANELA_MS < agora);
        }
        long[] janela = contagem.compute(chave, (k, atual) -> {
            if (atual == null || atual[0] + JANELA_MS <= agora) return new long[]{agora, 1};
            atual[1]++;
            return atual;
        });
        return janela[1] <= limite;
    }
}
