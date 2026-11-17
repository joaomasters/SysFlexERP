package com.acougue.tenant;

// codigo: o que a pessoa digita no login (ex: "001") — curto, fixo, sem
// adivinhação. slug é só interno, usado pra nomear o schema no Postgres.
// moduloIndustrial: a plataforma liberou os módulos industriais para esta empresa.
public record Tenant(Long id, String codigo, String slug, String nome, String schemaName, boolean ativo,
                     boolean moduloIndustrial) {

    public Tenant(Long id, String codigo, String slug, String nome, String schemaName, boolean ativo) {
        this(id, codigo, slug, nome, schemaName, ativo, false);
    }
}
