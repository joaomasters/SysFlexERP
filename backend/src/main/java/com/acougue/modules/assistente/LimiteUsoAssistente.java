package com.acougue.modules.assistente;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 A cota gratuita do Gemini é uma só para o projeto inteiro: sem freio, uma loja
 consome tudo e o assistente para para as outras. Limite por usuário (por hora)
 e por empresa (por dia), em memória, como o LimitadorTentativasLogin.
 */
@Component
public class LimiteUsoAssistente {

    private static final long HORA_MS = 60L * 60 * 1000;
    private static final long DIA_MS = 24 * HORA_MS;

    private static final long MINUTO_MS = 60L * 1000;

    private final int porUsuarioHora;
    private final int porEmpresaDia;
    private final int porMinuto;
    private final Map<String, Deque<Long>> usos = new ConcurrentHashMap<>();

    public LimiteUsoAssistente(@Value("${assistente.limite.por-usuario-hora:20}") int porUsuarioHora,
                               @Value("${assistente.limite.por-empresa-dia:100}") int porEmpresaDia,
                               @Value("${assistente.limite.por-minuto:2}") int porMinuto) {
        this.porUsuarioHora = porUsuarioHora;
        this.porEmpresaDia = porEmpresaDia;
        this.porMinuto = porMinuto;
    }

    /** Registra o uso e devolve null; se passou do limite, não registra e devolve o motivo. */
    public synchronized String consumir(String empresa, Long usuarioId, long agora) {
        Deque<Long> doUsuario = janela("u:" + empresa + ":" + usuarioId, agora - HORA_MS);
        Deque<Long> daEmpresa = janela("e:" + empresa, agora - DIA_MS);
        // A camada gratuita recusa (403/429) quando chegam chamadas demais no mesmo minuto
        Deque<Long> geral = janela("geral", agora - MINUTO_MS);
        if (geral.size() >= porMinuto) {
            return "O assistente está atendendo outras perguntas agora. Tente de novo em um minuto.";
        }
        if (doUsuario.size() >= porUsuarioHora) {
            return "Você atingiu o limite de " + porUsuarioHora + " perguntas por hora. Tente mais tarde.";
        }
        if (daEmpresa.size() >= porEmpresaDia) {
            return "Sua empresa atingiu o limite de " + porEmpresaDia + " perguntas por dia. O assistente volta amanhã.";
        }
        doUsuario.addLast(agora);
        daEmpresa.addLast(agora);
        geral.addLast(agora);
        return null;
    }

    private Deque<Long> janela(String chave, long desde) {
        Deque<Long> fila = usos.computeIfAbsent(chave, k -> new ArrayDeque<>());
        while (!fila.isEmpty() && fila.peekFirst() <= desde) {
            fila.pollFirst();
        }
        return fila;
    }
}
