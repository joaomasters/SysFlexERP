package com.acougue.modules.industrial.execucao;

import com.acougue.entity.Produto;
import com.acougue.modules.industrial.estrutura.CentroTrabalhoRepository;
import com.acougue.modules.industrial.estrutura.MaquinaRepository;
import com.acougue.modules.industrial.execucao.ExecucaoDto.*;
import com.acougue.modules.industrial.pcp.OpMaterial;
import com.acougue.modules.industrial.pcp.OpOperacao;
import com.acougue.modules.industrial.pcp.OpOperacaoRepository;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import com.acougue.modules.industrial.pcp.OrdemProducaoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Consultas do chão de fábrica e do andamento das OPs (produzido, refugo, WIP, consumo × previsto). */
@Service
@RequiredArgsConstructor
public class ExecucaoConsultaService {

    private final OpOperacaoRepository operacaoRepo;
    private final OrdemProducaoRepository ordemRepo;
    private final ApontamentoRepository apontamentoRepo;
    private final ParadaRepository paradaRepo;
    private final ConsumoRepository consumoRepo;
    private final CentroTrabalhoRepository centroRepo;
    private final MaquinaRepository maquinaRepo;
    private final MotivoParadaRepository motivoRepo;
    private final ExecucaoMapper mapper;

    @Transactional(readOnly = true)
    public ChaoRecursos recursos() {
        Map<Long, Parada> paradas = paradaRepo.abertasComMaquina().stream()
                .collect(Collectors.toMap(p -> p.getMaquina().getId(), Function.identity(), (a, b) -> a));
        Map<Long, String> nomes = mapper.nomes(paradas.values().stream().map(Parada::getUsuarioId).toList());
        List<MaquinaChao> maquinas = maquinaRepo.listarTodas().stream().filter(m -> !"INATIVA".equals(m.getStatus()))
                .map(m -> {
                    Optional<Apontamento> uso = apontamentoRepo.abertoDaMaquina(m.getId());
                    return new MaquinaChao(m.getId(), m.getCodigo(), m.getDescricao(), m.getCentroTrabalho().getId(),
                            m.getTipoMaquina().getId(), m.getStatus(), mapper.parada(paradas.get(m.getId()), nomes),
                            uso.map(Apontamento::getId).orElse(null), uso.map(a -> a.getOrdem().getNumero()).orElse(null));
                }).toList();
        return new ChaoRecursos(
                centroRepo.listarTodos().stream().filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                        .map(c -> new Recurso(c.getId(), c.getCodigo(), c.getNome())).toList(),
                maquinas,
                motivoRepo.findAllByOrderByCodigoAsc().stream().filter(MotivoParada::getAtivo).map(MotivoOut::de).toList());
    }

    /** Operações do centro que ainda podem ser apontadas, da mais urgente para a menos urgente. */
    @Transactional(readOnly = true)
    public List<FilaItem> fila(Long centroId) {
        List<OpOperacao> ops = operacaoRepo.fila(centroId, List.of("LIBERADA", "EM_PRODUCAO", "PAUSADA"));
        Set<Long> ordemIds = ops.stream().map(op -> op.getOrdem().getId()).collect(Collectors.toSet());
        Map<Long, List<Apontamento>> porOperacao = ordemIds.isEmpty() ? Map.of()
                : apontamentoRepo.validosDasOrdens(ordemIds).stream().collect(Collectors.groupingBy(a -> a.getOperacao().getId()));
        LocalDate hoje = LocalDate.now();
        return ops.stream()
                .sorted(Comparator.comparing((OpOperacao op) -> op.getOrdem().getPrioridade())
                        .thenComparing(op -> op.getOrdem().getFimPrevisto())
                        .thenComparing(op -> op.getOrdem().getNumero())
                        .thenComparing(OpOperacao::getSequencia))
                .map(op -> {
                    OrdemProducao o = op.getOrdem();
                    List<Apontamento> aps = porOperacao.getOrDefault(op.getId(), List.of());
                    return new FilaItem(op.getId(), o.getId(), o.getNumero(), o.getStatus(), o.getPrioridade(), o.getFimPrevisto(),
                            o.getFimPrevisto().isBefore(hoje), o.getProduto().getNome(), o.getProduto().getUnidadeMedida(),
                            o.getQuantidade(), op.getSequencia(), op.getDescricao(), op.getStatus(),
                            op.getTipoMaquina() == null ? null : op.getTipoMaquina().getId(),
                            op.getTipoMaquina() == null ? null : op.getTipoMaquina().getNome(),
                            op.getMaquina() == null ? null : op.getMaquina().getId(),
                            op.getMaquina() == null ? null : op.getMaquina().getCodigo(),
                            somar(aps, Apontamento::getQuantidadeBoa), somar(aps, Apontamento::getQuantidadeRefugo),
                            ExecucaoMapper.ultimaOperacao(op), op.getInstrucoes());
                }).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ApontamentoOut> meuApontamento(Long usuarioId) {
        return apontamentoRepo.abertoDoOperador(usuarioId).map(a -> {
            Parada aberta = paradaRepo.abertaDoApontamento(a.getId()).orElse(null);
            return mapper.apontamento(a, aberta, mapper.nomes(List.of(usuarioId)));
        });
    }

    /** Andamento da OP: por operação (produzido, refugo, tempo, WIP) e por material (previsto × consumido). */
    @Transactional(readOnly = true)
    public ExecucaoOut execucao(Long ordemId) {
        OrdemProducao o = ordemRepo.findById(ordemId).orElseThrow(() -> new EntityNotFoundException("OP não encontrada: " + ordemId));
        List<Apontamento> apontamentos = apontamentoRepo.daOrdem(ordemId);
        List<Consumo> consumos = consumoRepo.daOrdem(ordemId);
        List<Parada> paradas = paradaRepo.daOrdem(ordemId);
        Map<Long, List<Apontamento>> validos = apontamentos.stream().filter(a -> !"ESTORNADO".equals(a.getStatus()))
                .collect(Collectors.groupingBy(a -> a.getOperacao().getId()));
        Map<Long, Parada> pausasAbertas = paradas.stream().filter(p -> p.getFim() == null && p.getApontamento() != null)
                .collect(Collectors.toMap(p -> p.getApontamento().getId(), Function.identity(), (a, b) -> a));
        Map<Long, String> nomes = mapper.nomes(Stream.of(
                apontamentos.stream().map(Apontamento::getOperadorId),
                consumos.stream().map(Consumo::getUsuarioId),
                paradas.stream().map(Parada::getUsuarioId)).flatMap(s -> s).toList());

        List<OpOperacao> ops = o.getOperacoes().stream().sorted(Comparator.comparing(OpOperacao::getSequencia)).toList();
        List<OperacaoAndamento> andamento = new ArrayList<>();
        for (int i = 0; i < ops.size(); i++) {
            OpOperacao op = ops.get(i);
            List<Apontamento> aps = validos.getOrDefault(op.getId(), List.of());
            BigDecimal boa = somar(aps, Apontamento::getQuantidadeBoa);
            BigDecimal seguinte = i + 1 < ops.size() ? somar(validos.getOrDefault(ops.get(i + 1).getId(), List.of()),
                    a -> a.getQuantidadeBoa().add(a.getQuantidadeRefugo())) : null;
            BigDecimal trabalhado = BigDecimal.ZERO;
            BigDecimal parado = BigDecimal.ZERO;
            for (Apontamento a : aps) {
                var out = mapper.apontamento(a, pausasAbertas.get(a.getId()), nomes);
                trabalhado = trabalhado.add(out.minutosTrabalhados());
                parado = parado.add(out.minutosParado());
            }
            andamento.add(new OperacaoAndamento(op.getId(), op.getSequencia(), op.getDescricao(), op.getCentroTrabalho().getNome(),
                    op.getStatus(), op.getTempoPrevistoMin(), trabalhado, parado, boa, somar(aps, Apontamento::getQuantidadeRefugo),
                    // WIP: o que a operação já entregou e a seguinte ainda não processou
                    seguinte == null ? null : boa.subtract(seguinte).max(BigDecimal.ZERO),
                    aps.stream().filter(Apontamento::aberto).count()));
        }

        BigDecimal produzido = ops.isEmpty() ? BigDecimal.ZERO : andamento.get(andamento.size() - 1).quantidadeBoa();
        BigDecimal refugo = andamento.stream().map(OperacaoAndamento::quantidadeRefugo).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fracao = produzido.divide(o.getQuantidade(), 6, RoundingMode.HALF_UP);

        Map<Long, BigDecimal> consumido = consumos.stream().filter(c -> !c.getEstornado())
                .collect(Collectors.groupingBy(c -> c.getProduto().getId(),
                        Collectors.reducing(BigDecimal.ZERO, Consumo::getQuantidade, BigDecimal::add)));
        List<MaterialAndamento> materiais = o.getMateriais().stream().map((OpMaterial m) -> {
            Produto p = m.getProduto();
            BigDecimal usado = consumido.getOrDefault(p.getId(), BigDecimal.ZERO);
            // sugestão: o previsto proporcional ao que já foi produzido, menos o que já foi consumido
            BigDecimal sugerido = m.getQuantidadePrevista().multiply(fracao.min(BigDecimal.ONE)).subtract(usado)
                    .max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
            return new MaterialAndamento(p.getId(), p.getCodigoInterno(), p.getNome(), m.getUnidade(), m.getQuantidadePrevista(),
                    usado, p.getEstoqueAtual(), sugerido);
        }).toList();

        return new ExecucaoOut(o.getId(), o.getNumero(), o.getStatus(), o.getQuantidade(), o.getProduto().getUnidadeMedida(),
                produzido, refugo, fracao.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP), andamento, materiais,
                apontamentos.stream().map(a -> mapper.apontamento(a, pausasAbertas.get(a.getId()), nomes)).toList(),
                consumos.stream().map(c -> mapper.consumo(c, nomes)).toList(),
                paradas.stream().map(p -> mapper.parada(p, nomes)).toList());
    }

    static BigDecimal somar(List<Apontamento> aps, Function<Apontamento, BigDecimal> campo) {
        return aps.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
