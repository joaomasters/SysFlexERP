package com.acougue.modules.industrial.execucao;

import com.acougue.entity.Produto;
import com.acougue.entity.Usuario;
import com.acougue.modules.industrial.execucao.ExecucaoDto.*;
import com.acougue.modules.industrial.pcp.OpOperacao;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import com.acougue.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Monta as respostas da execução (com nomes de usuários) e concentra o cálculo de tempo. */
@Component
@RequiredArgsConstructor
public class ExecucaoMapper {

    private final UsuarioRepository usuarioRepo;

    /** Minutos entre dois instantes, com duas casas (o fim nulo conta até agora). */
    public static BigDecimal minutos(LocalDateTime inicio, LocalDateTime fim) {
        LocalDateTime ate = fim == null ? LocalDateTime.now() : fim;
        long segundos = Math.max(0, Duration.between(inicio, ate).getSeconds());
        return BigDecimal.valueOf(segundos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /** A última operação (maior sequência) é a que entrega o produto acabado. */
    public static boolean ultimaOperacao(OpOperacao op) {
        return op.getOrdem().getOperacoes().stream().mapToInt(OpOperacao::getSequencia).max().orElse(op.getSequencia())
                == op.getSequencia();
    }

    public Map<Long, String> nomes(Collection<Long> ids) {
        Set<Long> validos = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (validos.isEmpty()) return Map.of();
        return usuarioRepo.findAllById(validos).stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNome));
    }

    public ParadaOut parada(Parada p, Map<Long, String> nomes) {
        if (p == null) return null;
        return new ParadaOut(p.getId(), p.getMaquina() == null ? null : p.getMaquina().getId(),
                p.getMaquina() == null ? null : p.getMaquina().getCodigo(), p.getMotivo().getId(),
                p.getMotivo().getDescricao(), p.getMotivo().getCategoria(), p.getMotivo().getPlanejada(),
                p.getApontamento() == null ? null : p.getApontamento().getId(), p.getOrdemId(), p.getInicio(), p.getFim(),
                minutos(p.getInicio(), p.getFim()), p.getObservacao(), nomes.get(p.getUsuarioId()));
    }

    public ApontamentoOut apontamento(Apontamento a, Parada aberta, Map<Long, String> nomes) {
        OrdemProducao o = a.getOrdem();
        OpOperacao op = a.getOperacao();
        BigDecimal parado = a.getMinutosParado();
        if (aberta != null) parado = parado.add(minutos(aberta.getInicio(), null));
        BigDecimal trabalhados = minutos(a.getInicio(), a.getFim()).subtract(parado).max(BigDecimal.ZERO);
        return new ApontamentoOut(a.getId(), o.getId(), o.getNumero(), o.getProduto().getNome(), o.getProduto().getUnidadeMedida(),
                op.getId(), op.getSequencia(), op.getDescricao(), op.getCentroTrabalho().getNome(),
                a.getMaquina() == null ? null : a.getMaquina().getId(), a.getMaquina() == null ? null : a.getMaquina().getCodigo(),
                a.getOperadorId(), nomes.get(a.getOperadorId()), a.getStatus(), a.getInicio(), a.getFim(), parado, trabalhados,
                a.getQuantidadeBoa(), a.getQuantidadeRefugo(), a.getMotivoRefugo(), a.getLote(), a.getConcluiuOperacao(),
                a.getEntradaEstoque(), ultimaOperacao(op), parada(aberta, nomes), a.getObservacao(), a.getMotivoEstorno());
    }

    public ConsumoOut consumo(Consumo c, Map<Long, String> nomes) {
        Produto p = c.getProduto();
        return new ConsumoOut(c.getId(), p.getId(), p.getCodigoInterno(), p.getNome(), p.getUnidadeMedida(), c.getQuantidade(),
                c.getCustoUnitario(), c.getLoteMaterial(), c.getApontamentoId(), c.getObservacao(), nomes.get(c.getUsuarioId()),
                c.getCreatedAt(), c.getEstornado(), c.getMotivoEstorno());
    }
}
