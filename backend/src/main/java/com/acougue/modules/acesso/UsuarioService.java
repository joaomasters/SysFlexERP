package com.acougue.modules.acesso;

import com.acougue.entity.Modulo;
import com.acougue.entity.Perfil;
import com.acougue.entity.Usuario;
import com.acougue.exception.BusinessException;
import com.acougue.modules.acesso.dto.AtualizarUsuarioDTO;
import com.acougue.modules.acesso.dto.CriarUsuarioDTO;
import com.acougue.modules.acesso.dto.TrocarSenhaDTO;
import com.acougue.repository.PerfilRepository;
import com.acougue.repository.UsuarioRepository;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.UsuarioAutenticado;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepo;
    private final PerfilRepository perfilRepo;
    private final PasswordEncoder passwordEncoder;

    public List<Usuario> listar() {
        ContextoUsuario.exigirPermissao(Modulo.USUARIOS, Acao.VER);
        return usuarioRepo.findAll();
    }

    public Usuario buscarPorId(Long id) {
        ContextoUsuario.exigirPermissao(Modulo.USUARIOS, Acao.VER);
        return usuarioRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + id));
    }

    @Transactional
    public Usuario criar(CriarUsuarioDTO dto) {
        ContextoUsuario.exigirPermissao(Modulo.USUARIOS, Acao.CRIAR);

        if (usuarioRepo.existsByLogin(dto.getLogin())) {
            throw new BusinessException("Já existe um usuário com o login '" + dto.getLogin() + "'.");
        }
        Perfil perfil = perfilRepo.findById(dto.getPerfilId())
                .orElseThrow(() -> new EntityNotFoundException("Perfil não encontrado: " + dto.getPerfilId()));

        return usuarioRepo.save(Usuario.builder()
                .nome(dto.getNome())
                .login(dto.getLogin())
                .senhaHash(passwordEncoder.encode(dto.getSenha()))
                .perfil(perfil)
                .ativo(true)
                .build());
    }

    @Transactional
    public Usuario atualizar(Long id, AtualizarUsuarioDTO dto) {
        ContextoUsuario.exigirPermissao(Modulo.USUARIOS, Acao.EDITAR);

        Usuario usuario = buscarPorId(id);
        Perfil novoPerfil = perfilRepo.findById(dto.getPerfilId())
                .orElseThrow(() -> new EntityNotFoundException("Perfil não encontrado: " + dto.getPerfilId()));

        /*
        Impede deixar o sistema sem nenhum SUPER_ADMIN ativo — trocaria o
        último e ninguém mais conseguiria gerenciar perfis/usuários depois
         */
        if (usuario.getPerfil().isSuperAdmin() && !novoPerfil.isSuperAdmin()
                && contarSuperAdminsAtivos() <= 1) {
            throw new BusinessException(
                    "Este é o único usuário SUPER_ADMIN ativo. Não é possível remover esse perfil dele.");
        }

        usuario.setNome(dto.getNome());
        usuario.setPerfil(novoPerfil);
        return usuarioRepo.save(usuario);
    }

    @Transactional
    public Usuario trocarSenha(Long id, TrocarSenhaDTO dto) {
        // Editar senha de outra pessoa exige EDITAR; trocar a própria senha é sempre permitido.
        UsuarioAutenticado atual = ContextoUsuario.atual();
        boolean propriaSenha = atual.getUsuarioId().equals(id);
        if (!propriaSenha) {
            ContextoUsuario.exigirPermissao(Modulo.USUARIOS, Acao.EDITAR);
        }

        // buscarPorId exige permissão de VER usuários — quem troca a própria senha pode não ter
        Usuario usuario = usuarioRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + id));
        if (propriaSenha && (dto.getSenhaAtual() == null
                || !passwordEncoder.matches(dto.getSenhaAtual(), usuario.getSenhaHash()))) {
            throw new BusinessException("Senha atual incorreta.");
        }
        usuario.setSenhaHash(passwordEncoder.encode(dto.getNovaSenha()));
        return usuarioRepo.save(usuario);
    }

    @Transactional
    public Usuario alterarStatus(Long id, boolean ativo) {
        ContextoUsuario.exigirPermissao(Modulo.USUARIOS, Acao.EXCLUIR); // desativar equivale a "excluir" no espírito da permissão

        UsuarioAutenticado atual = ContextoUsuario.atual();
        if (atual.getUsuarioId().equals(id) && !ativo) {
            throw new BusinessException("Você não pode desativar seu próprio usuário.");
        }

        Usuario usuario = buscarPorId(id);
        if (usuario.getPerfil().isSuperAdmin() && !ativo && contarSuperAdminsAtivos() <= 1) {
            throw new BusinessException(
                    "Este é o único usuário SUPER_ADMIN ativo. Não é possível desativá-lo.");
        }

        usuario.setAtivo(ativo);
        return usuarioRepo.save(usuario);
    }

    private long contarSuperAdminsAtivos() {
        return usuarioRepo.findByAtivoTrue().stream()
                .filter(u -> u.getPerfil().isSuperAdmin())
                .count();
    }
}