package com.acougue.tenant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/*
 Garante que o tenant atual (Açougue do Zé, schema public, em produção
 desde antes da multi-tenancy existir) tenha uma linha em public.tenants.
 Idempotente — roda a cada boot, só insere se ainda não existir.

 TenantContext não precisa ser setado aqui: ApplicationRunner roda antes de
 qualquer request, TenantContext.get() já resolve "public" por padrão.
 */
@Component
public class TenantBootstrap implements ApplicationRunner {

    private static final String CODIGO_TENANT_ATUAL  = "001";
    private static final String SLUG_TENANT_ATUAL    = "acougue-do-ze";
    private static final String NOME_TENANT_ATUAL    = "Açougue e Mercearia do Zé";
    private static final String SCHEMA_TENANT_ATUAL  = "public";

    private final TenantRepository tenantRepository;

    public TenantBootstrap(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tenantRepository.existsBySchemaName(SCHEMA_TENANT_ATUAL)) {
            tenantRepository.insert(CODIGO_TENANT_ATUAL, SLUG_TENANT_ATUAL, NOME_TENANT_ATUAL, SCHEMA_TENANT_ATUAL);
        }
    }
}
