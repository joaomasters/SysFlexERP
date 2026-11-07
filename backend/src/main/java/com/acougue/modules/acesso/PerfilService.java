package com.acougue.modules.acesso;

import com.acougue.entity.Modulo;
import com.acougue.entity.Perfil;
import com.acougue.entity.PerfilPermissao;
import com.acougue.exception.BusinessException;
import com.acougue.modules.acesso.dto.AtualizarPermissoesDTO;
import com.acougue.modules.acesso.dto.CriarPerfilDTO;
import com.acougue.modules.acesso.dto.PermissaoItemDTO;
import com.acougue.repository.PerfilPermissaoRepository;
import com.acougue.repository.PerfilRepository;
import com.acougue.repository.UsuarioRepository;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.UsuarioAutenticado;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private final PerfilRepository perfilRepo;
    private final PerfilPermissaoRepository permissaoRepo;
    private final UsuarioRepository usuarioRepo;

    /*
     Só o SUPER_ADMIN gerencia perfis e permissões — trava aplicada aqui,
     já como segunda camada, além do que a Parte 5 vai reforçar de forma
     genérica em todos os endpoints do sistema.
     */
    private void exigirSuperAdmin() {
        if (!ContextoUsuario.atual().isSuperAdmin()) {
            throw new AccessDeniedException("Apenas o SUPER_ADMIN pode gerenciar perfis e permissões.");
        }
    }

    /** Ver perfis e permissões: quem cuida de Perfis ou de Usuários (que escolhe o perfil de cada um). */
    public void exigirVerPerfis() {
        UsuarioAutenticado u = ContextoUsuario.atual();
        if (!u.pode(Modulo.PERFIS, Acao.VER) && !u.pode(Modulo.USUARIOS, Acao.VER)) {
            throw new AccessDeniedException("Sem permissão para ver perfis.");
        }
    }

    public List<Perfil> listar() {
        exigirVerPerfis();
        return perfilRepo.findAll();
    }

    public Perfil buscarPorId(Long id) {
        exigirVerPerfis();
        return perfilRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Perfil não encontrado: " + id));
    }

    @Transactional
    public Perfil criar(CriarPerfilDTO dto) {
        exigirSuperAdmin();

        if (perfilRepo.findByNome(dto.getNome()).isPresent()) {
            throw new BusinessException("Já existe um perfil com o nome '" + dto.getNome() + "'.");
        }

        Perfil perfil = perfilRepo.save(Perfil.builder()
                .nome(dto.getNome())
                .descricao(dto.getDescricao())
                .protegido(false)
                .ativo(true)
                .build());

        /*
        Já cria uma linha por módulo, tudo negado por padrão — assim a matriz
        sempre tem todas as linhas prontas pra tela marcar, sem buracos
         */
        for (Modulo modulo : Modulo.values()) {
            permissaoRepo.save(PerfilPermissao.builder()
                    .perfil(perfil)
                    .modulo(modulo)
                    .podeVer(false).podeCriar(false).podeEditar(false).podeExcluir(false)
                    .build());
        }

        return perfil;
    }

    @Transactional
    public Perfil atualizarPermissoes(Long perfilId, AtualizarPermissoesDTO dto) {
        exigirSuperAdmin();

        Perfil perfil = buscarPorId(perfilId);
        if (perfil.isSuperAdmin()) {
            throw new BusinessException(
                    "A permissão do SUPER_ADMIN é sempre total e não pode ser alterada.");
        }

        for (PermissaoItemDTO item : dto.getPermissoes()) {
            PerfilPermissao permissao = permissaoRepo.findByPerfilIdAndModulo(perfilId, item.getModulo())
                    .orElseGet(() -> PerfilPermissao.builder().perfil(perfil).modulo(item.getModulo()).build());
            permissao.setPodeVer(item.isVer());
            permissao.setPodeCriar(item.isCriar());
            permissao.setPodeEditar(item.isEditar());
            permissao.setPodeExcluir(item.isExcluir());
            permissaoRepo.save(permissao);
        }

        return perfil;
    }

    @Transactional
    public void excluir(Long perfilId) {
        exigirSuperAdmin();

        Perfil perfil = buscarPorId(perfilId);
        if (Boolean.TRUE.equals(perfil.getProtegido())) {
            throw new BusinessException("O perfil '" + perfil.getNome() + "' é protegido e não pode ser excluído.");
        }
        if (!usuarioRepo.findAll().stream()
                .filter(u -> u.getPerfil().getId().equals(perfilId)).toList().isEmpty()) {
            throw new BusinessException(
                    "Existem usuários vinculados a este perfil. Mude o perfil deles antes de excluir.");
        }
        perfilRepo.deleteById(perfilId);
    }
}