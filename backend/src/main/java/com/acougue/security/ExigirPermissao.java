package com.acougue.security;

import com.acougue.entity.Modulo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 Trava um endpoint (ou método de service) atrás da matriz de permissões:
 o usuário logado precisa ter a ação marcada no módulo indicado, senão
 a chamada é barrada com 403 antes de o método rodar.

 Uso:
 {@literal @}ExigirPermissao(modulo = Modulo.PRODUTOS, acao = Acao.CRIAR)
 {@literal @}PostMapping("/produtos")
 public ResponseEntity<Produto> criar(...) { ... }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ExigirPermissao {
    Modulo modulo();
    Acao acao();

    /**
     * true quando o próprio método de negócio já grava um registro de auditoria detalhado
     * (ex.: itens do PDV): o {@code AuditoriaAspect} então não grava a linha genérica, para não duplicar.
     */
    boolean auditoriaPropria() default false;
}