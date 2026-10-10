package com.acougue.tenant;

// codigo: o que a pessoa digita no login (ex: "001") — curto, fixo, sem
// adivinhação. slug é só interno, usado pra nomear o schema no Postgres.
public record Tenant(Long id, String codigo, String slug, String nome, String schemaName, boolean ativo) {
}
