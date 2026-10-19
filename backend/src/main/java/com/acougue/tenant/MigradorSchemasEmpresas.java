package com.acougue.tenant;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.regex.Pattern;

/*
 Mantém o banco de TODAS as empresas na mesma versão.

 O Spring Boot só aplica as migrations no schema "public" (a empresa 001).
 Os schemas tenant_* recebiam a cadeia de migrations apenas no dia em que a
 empresa era criada — toda migration nova depois disso nunca chegava neles.
 Resultado: tela quebrando com "coluna não existe" só nas outras empresas
 (ex.: V25, cancelamento de recebimento, na Dinamus).

 Agora, no boot, depois de migrar public, migra cada schema registrado em
 public.tenants. Isso roda dentro da inicialização do Flyway, ANTES de o
 Hibernate subir e de o servidor aceitar requisições — nenhuma loja é
 atendida com o banco desatualizado.

 Uma empresa com falha de migration não derruba as outras: o erro é
 registrado no log e o boot segue (essa empresa vai continuar com erro até
 a migration ser corrigida, como antes).
 */
@Slf4j
@Configuration
public class MigradorSchemasEmpresas {

    private static final Pattern SCHEMA_TENANT = Pattern.compile("^tenant_[a-z0-9_]+$");

    /** Mesmas regras do Flyway de public (application.yml). Usado também ao criar empresa. */
    public static MigrateResult migrar(DataSource dataSource, String schema) {
        if (!SCHEMA_TENANT.matcher(schema).matches()) {
            throw new IllegalArgumentException("Schema de empresa inválido: " + schema);
        }
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .outOfOrder(true)
                .load()
                .migrate();
    }

    @Bean
    public FlywayMigrationStrategy migrarTodasAsEmpresas() {
        return flyway -> {
            flyway.migrate(); // public (empresa principal), como o Spring Boot já fazia

            DataSource ds = flyway.getConfiguration().getDataSource();
            List<String> schemas = new JdbcTemplate(ds).queryForList(
                    "SELECT schema_name FROM public.tenants WHERE schema_name <> 'public' ORDER BY id", String.class);

            int ok = 0;
            for (String schema : schemas) {
                try {
                    MigrateResult r = migrar(ds, schema);
                    ok++;
                    if (r.migrationsExecuted > 0) {
                        log.info("Empresa {}: {} migration(s) aplicada(s), agora na versão {}",
                                schema, r.migrationsExecuted, r.targetSchemaVersion);
                    }
                } catch (Exception e) {
                    log.error("Empresa {}: FALHA ao aplicar migrations — telas dessa empresa podem dar erro", schema, e);
                }
            }
            log.info("Migrations por empresa: {}/{} schemas atualizados", ok, schemas.size());
        };
    }
}
