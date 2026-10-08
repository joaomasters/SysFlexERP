package com.acougue.tenant;

public record Tenant(Long id, String slug, String nome, String schemaName, boolean ativo) {
}
