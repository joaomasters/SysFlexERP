package com.acougue.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/*
 Um único Postgres, um único pool Hikari, N schemas (um por tenant).
 Cada tenant troca de "gaveta" via Connection.setSchema(...) — o driver do
 Postgres traduz isso para SET search_path. Não há DataSource por tenant,
 então NÃO usamos AbstractDataSourceBasedMultiTenantConnectionProviderImpl
 (essa é para quem tem um DataSource inteiro por tenant).
 */
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private static final String SCHEMA_PADRAO = "public";

    private final DataSource dataSource;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /*
     Usada pelo Hibernate no boot (validação de schema via ddl-auto: validate),
     ANTES de qualquer tenant resolvido — não troca schema aqui. A conexão crua
     resolve para "public" via search_path padrão do Postgres, que já tem o
     schema de negócio completo.
     */
    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setSchema(tenantIdentifier);
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        // Reseta antes de devolver ao pool — defesa extra contra uma conexão
        // reaproveitada carregar o search_path do tenant anterior.
        connection.setSchema(SCHEMA_PADRAO);
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new UnsupportedOperationException("Unwrap não suportado: " + unwrapType);
    }
}
