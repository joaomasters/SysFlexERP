package com.acougue.tenant;

/*
 Guarda, por thread, qual schema Postgres a requisição corrente deve usar.
 Setado pelo JwtAuthFilter (requisições autenticadas) e pelo AuthController
 (durante o login, antes de existir token). Lido pelo TenantIdentifierResolver
 a cada vez que o Hibernate abre uma sessão.

 Default "public": cobre o boot da aplicação (nenhum request em andamento),
 o tenant atual (Açougue do Zé, schema public) e qualquer chamada que não
 passe por login/filtro — nunca fica em estado indefinido.
 */
public class TenantContext {

    private static final String DEFAULT_SCHEMA = "public";
    private static final ThreadLocal<String> ATUAL = new ThreadLocal<>();

    private TenantContext() {}

    public static String get() {
        String schema = ATUAL.get();
        return schema != null ? schema : DEFAULT_SCHEMA;
    }

    public static void set(String schema) {
        ATUAL.set(schema != null ? schema : DEFAULT_SCHEMA);
    }

    public static void clear() {
        ATUAL.remove();
    }
}
