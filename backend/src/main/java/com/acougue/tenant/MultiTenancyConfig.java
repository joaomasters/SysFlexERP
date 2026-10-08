package com.acougue.tenant;

import com.acougue.config.JwtUtil;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import javax.sql.DataSource;

/*
 As beans MultiTenantConnectionProvider/CurrentTenantIdentifierResolver por
 si só NÃO bastam no Spring Boot 3.2.5 — a auto-detecção documentada por aí
 não se confirmou na prática (testado: Hibernate nunca chamava o resolver).
 HibernatePropertiesCustomizer é o jeito explícito e garantido de injetar
 essas duas propriedades, sem depender de detecção implícita por tipo.
 */
@Configuration
public class MultiTenancyConfig {

    @Bean
    public MultiTenantConnectionProvider<String> multiTenantConnectionProvider(DataSource dataSource) {
        return new SchemaMultiTenantConnectionProvider(dataSource);
    }

    @Bean
    public CurrentTenantIdentifierResolver<String> currentTenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    @Bean
    public HibernatePropertiesCustomizer multiTenancyHibernatePropertiesCustomizer(
            MultiTenantConnectionProvider<String> connectionProvider,
            CurrentTenantIdentifierResolver<String> identifierResolver) {
        return properties -> {
            properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, identifierResolver);
        };
    }

    // Ordered.HIGHEST_PRECEDENCE garante que roda antes de QUALQUER outro
    // filtro — inclusive o Open Session In View do Spring, que é quem abre
    // a sessão Hibernate (e resolve o tenant dela) logo no início da cadeia.
    // Ver TenantResolvingFilter para o porquê disso ser obrigatório.
    @Bean
    public FilterRegistrationBean<TenantResolvingFilter> tenantResolvingFilterRegistration(
            JwtUtil jwtUtil, TenantRepository tenantRepository) {
        FilterRegistrationBean<TenantResolvingFilter> registration =
                new FilterRegistrationBean<>(new TenantResolvingFilter(jwtUtil, tenantRepository));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
