package com.acougue.security;

import java.util.List;

/*
 O que cada sessão do JARVIS pode chamar. Regra única, aplicada no JwtAuthFilter
 antes de qualquer controller.

 Primeira camada do modo leitura: nada além de GET passa. A segunda fica no
 banco (SchemaMultiTenantConnectionProvider), para pegar um GET que grave.
 */
public final class RestricaoJarvis {

    /** Mesmo sendo GET: gestão da plataforma, chamada à adquirente com a credencial da empresa e agente da balança. */
    private static final List<String> BLOQUEADOS_NA_VISUALIZACAO = List.of(
            "/admin/", "/jarvis/", "/maquininhas/provedores/", "/balanca/agente/");

    private RestricaoJarvis() {}

    public static boolean permitido(UsuarioAutenticado usuario, String metodo, String caminho) {
        if (!usuario.isJarvis()) return true;
        if (!usuario.isSomenteLeitura()) return caminho.startsWith("/jarvis/");

        boolean leitura = "GET".equals(metodo) || "HEAD".equals(metodo);
        return leitura && BLOQUEADOS_NA_VISUALIZACAO.stream().noneMatch(caminho::startsWith);
    }
}
