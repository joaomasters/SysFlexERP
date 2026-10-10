package com.acougue.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/*
 Garante que o tenant atual (Açougue do Zé, schema public, em produção
 desde antes da multi-tenancy existir) tenha uma linha em public.tenants.
 Idempotente — roda a cada boot, só insere se ainda não existir.

 Código/slug/nome vêm de application.yml (TENANT_PUBLICO_*), não hardcoded
 aqui — são dados desse negócio específico, não algo do código em si. Só o
 schema_name é fixo em "public": isso sim é estrutural (é o schema que já
 existia antes da multi-tenancy, não dá pra configurar).

 TenantContext não precisa ser setado aqui: ApplicationRunner roda antes de
 qualquer request, TenantContext.get() já resolve "public" por padrão.
 */
@Component
public class TenantBootstrap implements ApplicationRunner {

    private static final String SCHEMA_TENANT_ATUAL = "public";

    private final TenantRepository tenantRepository;
    private final String codigoTenantAtual;
    private final String slugTenantAtual;
    private final String nomeTenantAtual;

    public TenantBootstrap(
            TenantRepository tenantRepository,
            @Value("${tenant.publico.codigo:001}") String codigoTenantAtual,
            @Value("${tenant.publico.slug:acougue-do-ze}") String slugTenantAtual,
            @Value("${tenant.publico.nome:Açougue e Mercearia do Zé}") String nomeTenantAtual) {
        this.tenantRepository = tenantRepository;
        this.codigoTenantAtual = codigoTenantAtual;
        this.slugTenantAtual = slugTenantAtual;
        this.nomeTenantAtual = nomeTenantAtual;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tenantRepository.existsBySchemaName(SCHEMA_TENANT_ATUAL)) {
            tenantRepository.insert(codigoTenantAtual, slugTenantAtual, nomeTenantAtual, SCHEMA_TENANT_ATUAL);
        }
    }
}
