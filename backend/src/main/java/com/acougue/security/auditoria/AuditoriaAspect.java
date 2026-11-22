package com.acougue.security.auditoria;

import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/*
 Registra automaticamente toda ação de CRIAR/EDITAR/EXCLUIR no sistema,
 reaproveitando o mesmo ponto de interceptação que já protege os endpoints
 (@ExigirPermissao) — não precisa espalhar chamadas de log manualmente
 pelos services.

 Roda só DEPOIS que o método protegido termina com sucesso (@AfterReturning):
 se o PermissaoAspect já barrou a chamada com 403, esse advice nunca chega
 a rodar — e está certo que seja assim, não houve ação nenhuma pra logar.

 VER nunca é auditado: toda tela lista dados o tempo todo, e isso geraria
 um volume enorme de entradas sem nenhum valor de auditoria real.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditoriaAspect {

    private final AuditoriaService auditoriaService;

    @AfterReturning(pointcut = "@annotation(exigirPermissao)", returning = "resultado")
    public void registrar(JoinPoint jp, ExigirPermissao exigirPermissao, Object resultado) {
        if (exigirPermissao.acao() == Acao.VER || exigirPermissao.auditoriaPropria()) {
            return;
        }
        try {
            String descricao = descrever(jp, exigirPermissao, resultado);
            auditoriaService.registrar(exigirPermissao.modulo().name(), exigirPermissao.acao().name(), descricao);
        } catch (Exception e) {
            // Auditoria nunca pode quebrar a ação real do usuário, mesmo que
            // algo dê errado só na hora de montar a descrição do log.
            log.warn("Falha ao montar descrição de auditoria: {}", e.getMessage());
        }
    }

    private String descrever(JoinPoint jp, ExigirPermissao exigirPermissao, Object resultado) {
        String metodo = jp.getSignature().getName();
        Long id = extrairId(jp, exigirPermissao.acao(), resultado);
        return id != null ? metodo + " #" + id : metodo;
    }

    /**
     * Tenta achar o id do recurso afetado. Em EDITAR/EXCLUIR o id certo
     * quase sempre vem como argumento (@PathVariable Long id). Em CRIAR não
     * existe id nos argumentos ainda — e se houver outro Long no meio do
     * caminho (ex: clienteId em "gerarFechamento(clienteId, ...)"), ele não
     * é o id do recurso criado — por isso aqui a prioridade é inversa,
     * primeiro o valor de retorno (o recurso recém-criado).
     */
    private Long extrairId(JoinPoint jp, Acao acao, Object resultado) {
        if (acao == Acao.CRIAR) {
            Long doRetorno = extrairIdDoRetorno(resultado);
            return doRetorno != null ? doRetorno : extrairIdDosArgumentos(jp);
        }
        Long dosArgumentos = extrairIdDosArgumentos(jp);
        return dosArgumentos != null ? dosArgumentos : extrairIdDoRetorno(resultado);
    }

    private Long extrairIdDosArgumentos(JoinPoint jp) {
        for (Object arg : jp.getArgs()) {
            if (arg instanceof Long id) {
                return id;
            }
        }
        return null;
    }

    private Long extrairIdDoRetorno(Object resultado) {
        try {
            Object corpo = resultado instanceof ResponseEntity<?> re ? re.getBody() : resultado;
            if (corpo == null) return null;
            Object id = corpo.getClass().getMethod("getId").invoke(corpo);
            return id instanceof Long l ? l : null;
        } catch (Exception e) {
            return null;
        }
    }
}