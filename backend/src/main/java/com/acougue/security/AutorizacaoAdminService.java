package com.acougue.security;

import com.acougue.entity.Modulo;
import com.acougue.entity.Perfil;
import com.acougue.entity.Usuario;
import com.acougue.exception.BusinessException;
import com.acougue.repository.PerfilPermissaoRepository;
import com.acougue.repository.UsuarioRepository;
import com.acougue.tenant.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Confere a credencial (login + senha) de um administrador para autorizar uma exceção no
 * balcão — hoje, vender no fiado acima do limite de crédito do cliente.
 *
 * "Administrador" segue a mesma regra do resto do sistema (ver
 * {@link UsuarioAutenticado#podeVerIdentificacaoUsuarios()}): SUPER_ADMIN, ou perfil que pode ver
 * o módulo AUDITORIA. Quem autoriza pode ser o próprio operador logado (se for administrador) ou
 * outro administrador que digita a credencial na hora — a senha só trafega nesta chamada e nunca
 * é gravada nem logada.
 *
 * Contra adivinhação de senha reaproveita o {@link LimitadorTentativasLogin}, mas em um espaço de
 * chaves próprio ("autorizacao:" + empresa, operador + administrador alvo): errar aqui NÃO
 * bloqueia o login do administrador (senão qualquer operador conseguiria travar a conta dele), e
 * o bloqueio de um operador não afeta os outros.
 */
@Service
public class AutorizacaoAdminService {

    private final UsuarioRepository usuarioRepo;
    private final PerfilPermissaoRepository permissaoRepo;
    private final PasswordEncoder passwordEncoder;
    private final LimitadorTentativasLogin limitador;
    /** Hash BCrypt de uma senha aleatória descartada — só para igualar o tempo de resposta. */
    private final String hashFalso;

    public AutorizacaoAdminService(UsuarioRepository usuarioRepo, PerfilPermissaoRepository permissaoRepo,
                                   PasswordEncoder passwordEncoder, LimitadorTentativasLogin limitador) {
        this.usuarioRepo = usuarioRepo;
        this.permissaoRepo = permissaoRepo;
        this.passwordEncoder = passwordEncoder;
        this.limitador = limitador;
        this.hashFalso = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    /**
     * @param operadorId quem está no PDV pedindo a autorização (para o controle de tentativas)
     * @return o administrador que autorizou
     * @throws BusinessException credencial inválida, usuário inativo, sem perfil de administrador
     *                           ou bloqueado por tentativas demais
     */
    public Usuario validar(Long operadorId, String login, String senha) {
        String empresa = "autorizacao:" + TenantContext.get();
        String chaveTentativas = operadorId + ">" + (login == null ? "" : login.trim().toLowerCase(Locale.ROOT));

        long minutos = limitador.minutosBloqueado(empresa, chaveTentativas);
        if (minutos > 0) {
            throw new BusinessException("Muitas tentativas incorretas de autorização. Tente novamente em "
                    + minutos + " minuto(s).");
        }

        Usuario usuario = (login == null || login.isBlank())
                ? null : usuarioRepo.findByLogin(login.trim()).orElse(null);

        // Mesma mensagem para "não existe", "inativo" e "senha errada", e o BCrypt roda nos três
        // casos — para ninguém descobrir quais logins existem pela resposta ou pelo tempo.
        boolean senhaConfere = passwordEncoder.matches(senha == null ? "" : senha,
                usuario != null ? usuario.getSenhaHash() : hashFalso);
        if (usuario == null || !Boolean.TRUE.equals(usuario.getAtivo()) || !senhaConfere) {
            limitador.registrarFalha(empresa, chaveTentativas);
            throw new BusinessException("Login ou senha de administrador inválidos.");
        }

        if (!ehAdministrador(usuario)) {
            throw new BusinessException("O usuário informado não é administrador e não pode autorizar esta venda.");
        }

        limitador.registrarSucesso(empresa, chaveTentativas);
        return usuario;
    }

    private boolean ehAdministrador(Usuario usuario) {
        Perfil perfil = usuario.getPerfil();
        if (perfil == null) return false;
        if (perfil.isSuperAdmin()) return true;
        return permissaoRepo.findByPerfilIdAndModulo(perfil.getId(), Modulo.AUDITORIA)
                .map(p -> Boolean.TRUE.equals(p.getPodeVer()))
                .orElse(false);
    }
}