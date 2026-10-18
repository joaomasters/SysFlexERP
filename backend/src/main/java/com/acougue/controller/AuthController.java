package com.acougue.controller;

import com.acougue.config.JwtUtil;
import com.acougue.entity.PerfilPermissao;
import com.acougue.entity.Usuario;
import com.acougue.repository.PerfilPermissaoRepository;
import com.acougue.repository.UsuarioRepository;
import com.acougue.security.LimitadorTentativasLogin;
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
    private final LimitadorTentativasLogin limitador;
    /** Hash BCrypt de uma senha aleatória descartada — só para igualar o tempo de resposta. */
    private final String hashFalso;

    public AuthController(JwtUtil jwtUtil, UsuarioRepository usuarioRepo,
                          PerfilPermissaoRepository permissaoRepo, PasswordEncoder passwordEncoder,
                          TenantRepository tenantRepository, LimitadorTentativasLogin limitador) {
        this.jwtUtil = jwtUtil;
        this.usuarioRepo = usuarioRepo;
        this.permissaoRepo = permissaoRepo;
        this.passwordEncoder = passwordEncoder;
        this.tenantRepository = tenantRepository;
        this.limitador = limitador;
        this.hashFalso = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
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

        long minutosBloqueado = limitador.minutosBloqueado(codigoEmpresa, login);
        if (minutosBloqueado > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message",
                    "Muitas tentativas incorretas. Tente novamente em " + minutosBloqueado + " minuto(s)."));
        }

        if (codigoEmpresa == null || codigoEmpresa.isBlank()
                || tenantRepository.findByCodigo(codigoEmpresa.trim()).filter(t -> t.ativo()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Usuário ou senha inválidos"));
        }

        Usuario usuario = login == null ? null : usuarioRepo.findByLogin(login).orElse(null);

        /*
        Mesma mensagem de erro tanto para usuário não existe, quanto para
        senha errada — evitando que alguém descubra por tentativa quais
        logins existem no sistema. O BCrypt roda nos DOIS casos (com um hash
        falso quando o usuário não existe): senão a resposta de "login
        inexistente" volta bem mais rápido e o tempo entrega quais existem.
         */
        boolean senhaConfere = passwordEncoder.matches(senha == null ? "" : senha,
                usuario != null ? usuario.getSenhaHash() : hashFalso);
        if (usuario == null || !Boolean.TRUE.equals(usuario.getAtivo()) || !senhaConfere) {
            limitador.registrarFalha(codigoEmpresa, login);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Usuário ou senha inválidos"));
        }
        limitador.registrarSucesso(codigoEmpresa, login);

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
                // Super admin da empresa principal: único que gerencia empresas (ver TenantAdminController)
                "administradorPlataforma", usuario.getPerfil().isSuperAdmin() && "public".equals(TenantContext.get()),
                "permissoes", permissoesResposta
        ));
    }
}