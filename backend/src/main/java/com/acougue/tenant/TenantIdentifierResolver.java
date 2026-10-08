package com.acougue.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

// Instanciado manualmente em MultiTenancyConfig (não é @Component — registrar
// das duas formas criaria dois beans do mesmo tipo e quebraria a injeção).
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.get();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
