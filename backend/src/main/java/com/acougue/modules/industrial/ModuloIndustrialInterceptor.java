package com.acougue.modules.industrial;

import com.acougue.tenant.TenantContext;
import com.acougue.tenant.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Barra todas as rotas /industrial/** (menos /industrial/situacao) da empresa que não tem os
 * módulos industriais liberados pela plataforma. É a garantia no backend: esconder o menu no
 * frontend é só conforto. Quem usa só o PDV não alcança nada industrial.
 */
@Component
public class ModuloIndustrialInterceptor implements HandlerInterceptor {

    static final String MENSAGEM = "Os módulos industriais não estão habilitados para esta empresa.";

    private final TenantRepository tenantRepository;

    public ModuloIndustrialInterceptor(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String schema = TenantContext.get();
        if (schema == null || !tenantRepository.moduloIndustrialHabilitado(schema)) {
            throw new AccessDeniedException(MENSAGEM);
        }
        return true;
    }
}
