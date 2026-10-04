package com.acougue.security;

import com.acougue.entity.Modulo;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class ContextoUsuario {

    private ContextoUsuario() {}

    public static UsuarioAutenticado atual() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UsuarioAutenticado usuario) {
            return usuario;
        }
        throw new IllegalStateException("Nenhum usuário autenticado no contexto atual.");
    }

    /**
     * true somente se há um usuário autenticado E ele pode ver a identificação
     * de outros usuários (ver {@link UsuarioAutenticado#podeVerIdentificacaoUsuarios()}).
     * Seguro por padrão: sem autenticação (job, consumer Kafka, teste) devolve
     * false em vez de lançar exceção — nesses contextos nada de identificação
     * deve vazar.
     */
    public static boolean podeVerIdentificacaoUsuarios() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof UsuarioAutenticado usuario
                && usuario.podeVerIdentificacaoUsuarios();
    }

    // Lança 403 se o usuário logado não puder ver a identificação de outros usuários
    public static void exigirVisualizacaoIdentificacaoUsuarios() {
        if (!podeVerIdentificacaoUsuarios()) {
            throw new AccessDeniedException(
                    "Somente administradores podem ver a identificação dos usuários.");
        }
    }

    // Lança 403 se o usuário logado não tiver a permissão exigida naquele módulo
    public static void exigirPermissao(Modulo modulo, Acao acao) {
        if (!atual().pode(modulo, acao)) {
            throw new AccessDeniedException(
                    "Seu perfil não tem permissão de " + acao.name().toLowerCase() +
                            " no módulo " + modulo.getRotulo() + ".");
        }
    }
}