package com.acougue.modules.jarvis;

import com.acougue.config.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 JARVIS é o acesso da plataforma: vê todas as empresas, sem alterar nada.

 Entra pela tela de login normal com o código 000, que nunca é de uma empresa
 (os códigos gerados começam em 001). O operador é único e vem de variáveis de
 ambiente, então nenhuma empresa tem como chegar nele pelo banco. Sem as três
 variáveis o código 000 fica desligado e se comporta como código inexistente.
 */
@Slf4j
@Service
public class JarvisAutenticacao {

    public static final String CODIGO_ACESSO = "000";
    public static final String NOME = "JARVIS";

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final String login;
    private final String senhaHash;
    private final String segredoTotp;
    private final String hashFalso;
    private final boolean habilitado;

    public JarvisAutenticacao(JwtUtil jwtUtil, PasswordEncoder passwordEncoder,
                              @Value("${jarvis.login:}") String login,
                              @Value("${jarvis.senha:}") String senha,
                              @Value("${jarvis.totp-segredo:}") String segredoTotp) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.login = login.trim();
        this.segredoTotp = segredoTotp;
        this.hashFalso = passwordEncoder.encode(UUID.randomUUID().toString());
        this.habilitado = !this.login.isEmpty() && !senha.isEmpty() && segredoValido(segredoTotp);
        this.senhaHash = habilitado ? passwordEncoder.encode(senha) : hashFalso;
    }

    private static boolean segredoValido(String segredo) {
        if (segredo.isBlank()) return false;
        try {
            if (Totp.decodificarBase32(segredo).length >= 16) return true;
        } catch (IllegalArgumentException ignored) {
            // cai no aviso abaixo
        }
        log.warn("JARVIS desligado: JARVIS_TOTP_SEGREDO precisa ser Base32 com pelo menos 16 bytes");
        return false;
    }

    /** O BCrypt roda sempre, para o tempo de resposta não entregar se o acesso existe. */
    public boolean autenticar(String loginInformado, String senha, String codigoVerificacao) {
        boolean senhaConfere = passwordEncoder.matches(senha == null ? "" : senha, senhaHash);
        return habilitado
                && login.equals(loginInformado == null ? "" : loginInformado.trim())
                && senhaConfere
                && Totp.valido(segredoTotp, codigoVerificacao, System.currentTimeMillis() / 1000);
    }

    /** Sessão do painel: só enxerga as rotas /jarvis (ver RestricaoJarvis). */
    public Map<String, Object> sessaoPainel() {
        Map<String, Object> sessao = base(jwtUtil.generateTokenJarvis(login, "public", false));
        sessao.put("superAdmin", false);
        sessao.put("somenteLeitura", false);
        return sessao;
    }

    /** Sessão de visualização: as telas normais da empresa, em modo leitura. */
    public Map<String, Object> sessaoEmpresa(String schema, String codigo, String nome) {
        Map<String, Object> sessao = base(jwtUtil.generateTokenJarvis(login, schema, true));
        sessao.put("superAdmin", true);
        sessao.put("somenteLeitura", true);
        sessao.put("empresa", Map.of("codigo", codigo, "nome", nome));
        return sessao;
    }

    private Map<String, Object> base(String token) {
        Map<String, Object> sessao = new LinkedHashMap<>();
        sessao.put("token", token);
        sessao.put("usuarioId", 0);
        sessao.put("username", login);
        sessao.put("nome", NOME);
        sessao.put("perfil", NOME);
        sessao.put("administradorPlataforma", false);
        sessao.put("jarvis", true);
        sessao.put("permissoes", List.of());
        return sessao;
    }
}
