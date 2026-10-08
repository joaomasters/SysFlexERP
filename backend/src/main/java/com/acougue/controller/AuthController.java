package com.acougue.controller;

import com.acougue.config.JwtUtil;
import com.acougue.entity.PerfilPermissao;
import com.acougue.entity.Usuario;
import com.acougue.repository.PerfilPermissaoRepository;
import com.acougue.repository.UsuarioRepository;
import com.acougue.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UsuarioRepository usuarioRepo;
    private final PerfilPermissaoRepository permissaoRepo;
    private final PasswordEncoder passwordEncoder;

    public AuthController(JwtUtil jwtUtil, UsuarioRepository usuarioRepo,
                          PerfilPermissaoRepository permissaoRepo, PasswordEncoder passwordEncoder) {
        this.jwtUtil = jwtUtil;
        this.usuarioRepo = usuarioRepo;
        this.permissaoRepo = permissaoRepo;
        this.passwordEncoder = passwordEncoder;
    }

    /*
     O tenant já está resolvido quando este método roda: TenantResolvingFilter
     (com.acougue.tenant) roda ANTES de tudo — inclusive antes do Hibernate
     abrir sessão (Open Session In View) — e já deixou TenantContext correto,
     a partir do header X-Tenant-Slug (login num tenant != public) ou "public"
     por padrão. usuarioRepo/permissaoRepo abaixo já consultam o schema certo
     sem este controller precisar saber disso.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String login = body.get("username");
        String senha = body.get("password");

        Usuario usuario = usuarioRepo.findByLogin(login).orElse(null);

        /*
        Mesma mensagem de erro tanto para usuário não existe, quanto para
        senha errada — evitando que alguém descubra por tentativa quais
        logins existem no sistema.
         */
        if (usuario == null || !Boolean.TRUE.equals(usuario.getAtivo())
                || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Usuário ou senha inválidos"));
        }

        List<PerfilPermissao> permissoes = permissaoRepo.findByPerfilId(usuario.getPerfil().getId());
        String token = jwtUtil.generateToken(usuario, permissoes, TenantContext.get());

        List<Map<String, Object>> permissoesResposta = permissoes.stream()
                .map(p -> Map.<String, Object>of(
                        "modulo", p.getModulo().name(),
                        "rotulo", p.getModulo().getRotulo(),
                        "ver", Boolean.TRUE.equals(p.getPodeVer()),
                        "criar", Boolean.TRUE.equals(p.getPodeCriar()),
                        "editar", Boolean.TRUE.equals(p.getPodeEditar()),
                        "excluir", Boolean.TRUE.equals(p.getPodeExcluir())
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "token",      token,
                "usuarioId",  usuario.getId(),
                "username",   usuario.getLogin(),
                "nome",       usuario.getNome(),
                "perfil",     usuario.getPerfil().getNome(),
                "superAdmin", usuario.getPerfil().isSuperAdmin(),
                "permissoes", permissoesResposta
        ));
    }
}