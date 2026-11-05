package com.acougue.security;

/**
 * Componente que guarda em memória algo de uma empresa e sabe descartar só a parte dela.
 * O "Limpar cache" do SUPER_ADMIN (LimpezaCacheService) chama todos os que existirem.
 *
 * Cotas de proteção (ex.: limite de perguntas do assistente) NÃO implementam isto:
 * zerá-las não é limpar cache, é furar o limite.
 */
public interface CacheDaEmpresa {

    /** @param schema schema da empresa (ex.: tenant_loja_x); @param codigo código de login (ex.: 001) */
    void limparEmpresa(String schema, String codigo);
}
