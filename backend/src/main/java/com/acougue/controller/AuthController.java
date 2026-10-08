package com.acougue.controller;

import com.acougue.config.JwtUtil;
import com.acougue.entity.PerfilPermissao;
import com.acougue.entity.Usuario;
import com.acougue.repository.PerfilPermissaoRepository;
import com.acougue.repository.UsuarioRepository;
import com.acougue.tenant.TenantContext;
import com.acougue.tenant.TenantRepository;
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
    private final TenantRepository tenantRepository;

    public AuthController(JwtUtil jwtUtil, UsuarioRepository usuarioRepo,
                          PerfilPermissaoRepository permissaoRepo, PasswordEncoder passwordEncoder,
                          TenantRepository tenantRepository) {
        this.jwtUtil = jwtUtil;
        this.usuarioRepo = usuarioRepo;
        this.permissaoRepo = permissaoRepo;
        this.passwordEncoder = passwordEncoder;
        this.tenantRepository = tenantRepository;
    }

    /*
     O código da empresa é obrigatório em todo login, inclusive pro Açougue
     do Zé ("001") — ninguém entra sem informar qual tenant, mesmo que hoje
     só exista um. TenantResolvingFilter (com.acougue.tenant) já rodou ANTES
     de tudo — inclusive antes do Hibernate abrir sessão (Open Session In
     View) — e deixou TenantContext correto a partir desse mesmo código, mas
     quem valida de fato e devolve um 401 limpo pra código errado/ausente é
     este método: sem essa checagem aqui, um código inválido resolveria pra
     um schema inexistente lá no filtro e a query abaixo estouraria com um
     erro feio de banco em vez de uma mensagem normal de login.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,
                                    @RequestHeader(value = "X-Tenant-Codigo", required = false) String codigoEmpresa) {
        String login = body.get("username");
        String senha = body.get("password");

        if (codigoEmpresa == null || codigoEmpresa.isBlank()
                || tenantRepository.findByCodigo(codigoEmpresa.trim()).filter(t -> t.ativo()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Usuário ou senha inválidos"));
        }

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