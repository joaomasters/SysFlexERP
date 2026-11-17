package com.acougue.modules.industrial.qualidade;

import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.execucao.ExecucaoMapper;
import com.acougue.modules.industrial.qualidade.QualidadeDto.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Inspeção de lote em quarentena contra o plano ativo do produto. Aprovada (tudo conforme) libera o lote;
 * reprovada bloqueia o lote e abre uma não conformidade com as características reprovadas.
 */
@Service
@RequiredArgsConstructor
public class InspecaoService {

    private final InspecaoRepository inspecaoRepo;
    private final LoteRepository loteRepo;
    private final PlanoInspecaoRepository planoRepo;
    private final LoteService loteService;
    private final NaoConformidadeService ncService;
    private final ExecucaoMapper mapper;

    static boolean dentro(BigDecimal v, BigDecimal min, BigDecimal max) {
        return (min == null || v.compareTo(min) >= 0) && (max == null || v.compareTo(max) <= 0);
    }

    @Transactional
    public InspecaoOut registrar(InspecaoIn in, Long usuarioId) {
        Optional<Inspecao> repetida = inspecaoRepo.findByChave(in.chave());
        if (repetida.isPresent()) return saida(repetida.get(), mapper.nomes(List.of(usuarioId)), null);

        Lote lote = loteRepo.travar(in.loteId()).orElseThrow(() -> new EntityNotFoundException("Lote não encontrado: " + in.loteId()));
        if (!"QUARENTENA".equals(lote.getSituacao())) {
            throw new BusinessException("Só lotes em quarentena são inspecionados (o lote " + lote.getCodigo() + " está "
                    + lote.getSituacao().toLowerCase() + ").");
        }
        PlanoInspecao plano = planoRepo.ativoDoProduto(lote.getProduto().getId())
                .orElseThrow(() -> new BusinessException("O produto " + lote.getProduto().getNome() + " não tem plano de inspeção ativo."));

        Map<String, ValorIn> valores = new HashMap<>();
        for (ValorIn v : in.valores()) {
            String cod = v.codigo().trim().toUpperCase(Locale.ROOT);
            if (valores.put(cod, v) != null) throw new BusinessException("A característica " + cod + " foi informada duas vezes.");
        }
        Set<String> doPlano = plano.getCaracteristicas().stream().map(PlanoInspecao.Caracteristica::getCodigo).collect(Collectors.toSet());
        valores.keySet().stream().filter(c -> !doPlano.contains(c)).findFirst().ifPresent(c -> {
            throw new BusinessException("A característica " + c + " não faz parte do plano " + plano.getCodigo() + ".");
        });

        Inspecao i = Inspecao.builder().chave(in.chave()).lote(lote).plano(plano).planoRevisao(plano.getRevisao())
                .amostra(in.amostra()).observacao(LoteService.texto(in.observacao())).inspetorId(usuarioId).build();
        List<String> reprovadas = new ArrayList<>();
        for (PlanoInspecao.Caracteristica c : plano.getCaracteristicas()) {
            ValorIn v = valores.get(c.getCodigo());
            boolean medida = "MEDIDA".equals(c.getTipo());
            boolean informado = v != null && (medida ? v.valorNumero() != null : v.conforme() != null);
            if (!informado) {
                if (c.getObrigatorio()) throw new BusinessException("Informe " + c.getNome() + ".");
                continue;
            }
            boolean conforme = medida ? dentro(v.valorNumero(), c.getMinimo(), c.getMaximo()) : v.conforme();
            if (!conforme) reprovadas.add(c.getNome());
            i.getValores().add(Inspecao.Valor.builder().inspecao(i).codigo(c.getCodigo()).nome(c.getNome()).tipo(c.getTipo())
                    .unidade(c.getUnidade()).valorNumero(medida ? v.valorNumero() : null).minimo(c.getMinimo()).maximo(c.getMaximo())
                    .conforme(conforme).build());
        }
        i.setResultado(reprovadas.isEmpty() ? "APROVADO" : "REPROVADO");
        i = inspecaoRepo.saveAndFlush(i);

        String nc = null;
        if (reprovadas.isEmpty()) {
            loteService.situacao(lote, "LIBERADO", "Inspeção aprovada (plano " + plano.getCodigo() + " rev. " + plano.getRevisao() + ")", usuarioId);
        } else {
            loteService.situacao(lote, "BLOQUEADO", "Inspeção reprovada: " + String.join(", ", reprovadas), usuarioId);
            nc = ncService.abrirAutomatica("INSPECAO", lote, lote.getOrdem(), i.getId(),
                    "Inspeção reprovada no lote " + lote.getCodigo() + ": " + String.join(", ", reprovadas) + ".", usuarioId).getNumero();
        }
        return saida(i, mapper.nomes(List.of(usuarioId)), nc);
    }

    @Transactional(readOnly = true)
    public List<InspecaoOut> doLote(Long loteId) {
        List<Inspecao> lista = inspecaoRepo.doLote(loteId);
        Map<Long, String> nomes = mapper.nomes(lista.stream().map(Inspecao::getInspetorId).toList());
        return lista.stream().map(i -> saida(i, nomes, null)).toList();
    }

    static InspecaoOut saida(Inspecao i, Map<Long, String> nomes, String nc) {
        return new InspecaoOut(i.getId(), i.getLote().getId(), i.getLote().getCodigo(), i.getPlano().getCodigo(), i.getPlanoRevisao(),
                i.getResultado(), i.getAmostra(), i.getObservacao(), nomes.get(i.getInspetorId()), i.getCreatedAt(),
                i.getValores().stream().map(v -> new ValorOut(v.getCodigo(), v.getNome(), v.getTipo(), v.getUnidade(), v.getValorNumero(),
                        v.getMinimo(), v.getMaximo(), v.getConforme())).toList(), nc);
    }
}
