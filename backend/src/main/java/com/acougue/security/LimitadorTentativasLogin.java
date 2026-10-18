package com.acougue.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 Freio contra adivinhação de senha: depois de N erros seguidos para o mesmo
 login (na mesma empresa), bloqueia novas tentativas por alguns minutos. Um
 acerto zera a contagem.

 A chave é empresa+login, e não o IP: em produção todas as requisições
 chegam pelo proxy do Vercel, então o IP visto pelo backend é o mesmo para
 todos os clientes — limitar por IP bloquearia todo mundo junto.

 Em memória: vale por instância. Com uma instância só (Railway hoje) cobre o
 caso; com várias, cada uma conta separado (o limite efetivo multiplica).
 */
@Component
public class LimitadorTentativasLogin {

    private final int maxTentativas;
    private final long bloqueioMs;
    private final Map<String, long[]> registros = new ConcurrentHashMap<>(); // chave -> [erros, bloqueadoAte]

    public LimitadorTentativasLogin(@Value("${seguranca.login.max-tentativas:8}") int maxTentativas,
                                    @Value("${seguranca.login.bloqueio-minutos:15}") int bloqueioMinutos) {
        this.maxTentativas = maxTentativas;
        this.bloqueioMs = bloqueioMinutos * 60_000L;
    }

    /** Minutos restantes de bloqueio para esta chave, ou 0 se pode tentar. */
    public long minutosBloqueado(String empresa, String login) {
        long[] r = registros.get(chave(empresa, login));
        if (r == null) return 0;
        long restante = r[1] - System.currentTimeMillis();
        return restante > 0 ? Math.max(1, restante / 60_000) : 0;
    }

    public void registrarFalha(String empresa, String login) {
        long agora = System.currentTimeMillis();
        registros.compute(chave(empresa, login), (k, r) -> {
            long erros = (r == null || (r[1] > 0 && r[1] <= agora)) ? 1 : r[0] + 1;
            long bloqueadoAte = erros >= maxTentativas ? agora + bloqueioMs : 0;
            return new long[]{bloqueadoAte > 0 ? 0 : erros, bloqueadoAte};
        });
        // limpeza simples para o mapa não crescer sem limite
        if (registros.size() > 10_000) {
            registros.entrySet().removeIf(e -> e.getValue()[1] > 0 && e.getValue()[1] < agora);
        }
    }

    public void registrarSucesso(String empresa, String login) {
        registros.remove(chave(empresa, login));
    }

    private static String chave(String empresa, String login) {
        return (empresa == null ? "" : empresa.trim()) + "|" + (login == null ? "" : login.trim().toLowerCase(Locale.ROOT));
    }
}
