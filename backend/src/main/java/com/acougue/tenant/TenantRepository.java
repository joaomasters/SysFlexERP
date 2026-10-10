package com.acougue.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/*
 ÚNICA exceção consciente à regra "nunca hardcode schema": a tabela tenants
 precisa ser lida sempre de public, independente de qual schema o tenant
 corrente estiver usando — por isso NÃO é uma @Entity JPA (herdaria o
 schema-switching do TenantIdentifierResolver e quebraria) e sim um
 JdbcTemplate simples, qualificando "public.tenants" explicitamente em
 toda query.
 */
@Repository
public class TenantRepository {

    private final JdbcTemplate jdbc;

    public TenantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLUNAS = "id, codigo, slug, nome, schema_name, ativo";

    private Tenant mapear(ResultSet rs) throws SQLException {
        return new Tenant(
                rs.getLong("id"),
                rs.getString("codigo"),
                rs.getString("slug"),
                rs.getString("nome"),
                rs.getString("schema_name"),
                rs.getBoolean("ativo"));
    }

    public Optional<Tenant> findBySlug(String slug) {
        return jdbc.query(
                "SELECT " + COLUNAS + " FROM public.tenants WHERE slug = ?",
                (rs, rowNum) -> mapear(rs),
                slug
        ).stream().findFirst();
    }

    public Optional<Tenant> findByCodigo(String codigo) {
        return jdbc.query(
                "SELECT " + COLUNAS + " FROM public.tenants WHERE codigo = ?",
                (rs, rowNum) -> mapear(rs),
                codigo
        ).stream().findFirst();
    }

    public Optional<Tenant> findById(Long id) {
        return jdbc.query(
                "SELECT " + COLUNAS + " FROM public.tenants WHERE id = ?",
                (rs, rowNum) -> mapear(rs),
                id
        ).stream().findFirst();
    }

    public List<Tenant> listarTodos() {
        return jdbc.query(
                "SELECT " + COLUNAS + " FROM public.tenants ORDER BY id",
                (rs, rowNum) -> mapear(rs));
    }

    public boolean existsBySchemaName(String schemaName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.tenants WHERE schema_name = ?",
                Integer.class, schemaName);
        return count != null && count > 0;
    }

    public boolean existsBySlug(String slug) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.tenants WHERE slug = ?",
                Integer.class, slug);
        return count != null && count > 0;
    }

    // Usado só pelo TenantBootstrap — o tenant public já existia antes da
    // multi-tenancy, então entra direto ativo (sem passo de ativação manual).
    public void insert(String codigo, String slug, String nome, String schemaName) {
        jdbc.update(
                "INSERT INTO public.tenants (codigo, slug, nome, schema_name) VALUES (?, ?, ?, ?)",
                codigo, slug, nome, schemaName);
    }

    // Usado pelo TenantProvisioningService — todo tenant provisionado por
    // aqui nasce INATIVO, só loga depois de alguém clicar em "Ativar".
    public void insertInativo(String codigo, String slug, String nome, String schemaName) {
        jdbc.update(
                "INSERT INTO public.tenants (codigo, slug, nome, schema_name, ativo) VALUES (?, ?, ?, ?, false)",
                codigo, slug, nome, schemaName);
    }

    public void ativar(Long id) {
        jdbc.update("UPDATE public.tenants SET ativo = true WHERE id = ?", id);
    }

    public void desativar(Long id) {
        jdbc.update("UPDATE public.tenants SET ativo = false WHERE id = ?", id);
    }

    // Base pra gerar o próximo código sequencial (001, 002, ...) ao provisionar.
    public int contarTenants() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM public.tenants", Integer.class);
        return count != null ? count : 0;
    }
}
