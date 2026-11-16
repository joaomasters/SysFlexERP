package com.acougue.modules.industrial.extrusao;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.engenharia.FichaTecnica;
import com.acougue.modules.industrial.engenharia.FichaTecnicaRepository;
import com.acougue.modules.industrial.execucao.Apontamento;
import com.acougue.modules.industrial.execucao.ApontamentoRepository;
import com.acougue.modules.industrial.execucao.Consumo;
import com.acougue.modules.industrial.execucao.ExecucaoMapper;
import com.acougue.modules.industrial.extrusao.ExtrusaoDto.*;
import com.acougue.modules.industrial.pcp.OpOperacao;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import com.acougue.modules.industrial.pcp.OrdemProducaoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Perdas metálicas da OP e o balanço metálico (rendimento). */
@Service
@RequiredArgsConstructor
public class PerdaService {

    static final List<String> OP_ACEITA = List.of("LIBERADA", "EM_PRODUCAO", "PAUSADA", "CONCLUIDA");

    private final PerdaRepository perdaRepo;
    private final OrdemProducaoRepository ordemRepo;
    private final ApontamentoRepository apontamentoRepo;
    private final ConsumoLoteRepository consumoRepo;
    private final LoteMpRepository loteRepo;
    private final FichaTecnicaRepository fichaRepo;
    private final MedicaoService medicaoService;
    private final ExecucaoMapper mapper;

    @Transactional
    public PerdaOut registrar(PerdaIn in, Long usuarioId) {
        Optional<Perda> repetida = perdaRepo.findByChave(in.chave());
        if (repetida.isPresent()) return saida(repetida.get(), mapper.nomes(List.of(usuarioId)));
        if (!Perda.TIPOS.contains(in.tipo())) throw new BusinessException("Tipo de perda inválido: " + in.tipo());
        OrdemProducao ordem = ordemRepo.findById(in.ordemId())
                .orElseThrow(() -> new EntityNotFoundException("OP não encontrada: " + in.ordemId()));
        if (!OP_ACEITA.contains(ordem.getStatus())) {
            throw new BusinessException("A OP " + ordem.getNumero() + " não está liberada para produção.");
        }
        if (in.apontamentoId() != null) {
            Apontamento a = apontamentoRepo.findById(in.apontamentoId())
                    .orElseThrow(() -> new EntityNotFoundException("Apontamento não encontrado: " + in.apontamentoId()));
            if (!a.getOrdem().getId().equals(ordem.getId())) throw new BusinessException("O apontamento é de outra OP.");
        }
        Perda p = perdaRepo.saveAndFlush(Perda.builder().chave(in.chave()).ordem(ordem).apontamentoId(in.apontamentoId())
                .tipo(in.tipo()).quantidadeKg(in.quantidadeKg()).observacao(LigaService.texto(in.observacao()))
                .usuarioId(usuarioId).build());
        return saida(p, mapper.nomes(List.of(usuarioId)));
    }

    @Transactional
    public PerdaOut estornar(Long id, EstornoPerdaIn in, Long usuarioId) {
        Perda p = perdaRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Perda não encontrada: " + id));
        if (!p.getEstornada()) {
            if (List.of("ENCERRADA", "CANCELADA").contains(p.getOrdem().getStatus())) {
                throw new BusinessException("A OP " + p.getOrdem().getNumero() + " está " + p.getOrdem().getStatus().toLowerCase() + ".");
            }
            p.setEstornada(true);
            p.setEstornadaEm(LocalDateTime.now());
            p.setEstornadaPorId(usuarioId);
            p.setMotivoEstorno(in.motivo().trim());
            p = perdaRepo.save(p);
        }
        return saida(p, mapper.nomes(List.of(p.getUsuarioId())));
    }

    /** Medições, perdas, lotes de matéria-prima consumidos e o rendimento metálico da OP. */
    @Transactional(readOnly = true)
    public MetalurgiaOP metalurgia(Long ordemId) {
        OrdemProducao o = ordemRepo.findById(ordemId).orElseThrow(() -> new EntityNotFoundException("OP não encontrada: " + ordemId));
        List<Perda> perdas = perdaRepo.daOrdem(ordemId);
        List<Consumo> consumos = consumoRepo.validosDaOrdem(ordemId);
        Map<Long, String> nomes = mapper.nomes(perdas.stream().map(Perda::getUsuarioId).toList());

        Map<Long, BigDecimal> porLote = consumos.stream().filter(c -> c.getLoteMpId() != null)
                .collect(Collectors.groupingBy(Consumo::getLoteMpId, LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Consumo::getQuantidade, BigDecimal::add)));
        List<LoteConsumido> lotes = loteRepo.findAllById(porLote.keySet()).stream()
                .map(l -> new LoteConsumido(l.getId(), l.getNumero(), l.getLiga().getCodigo(), porLote.get(l.getId()))).toList();

        return new MetalurgiaOP(medicaoService.daOrdem(ordemId), perdas.stream().map(p -> saida(p, nomes)).toList(),
                rendimento(o, consumos, perdas), lotes);
    }

    /**
     * Balanço metálico: entrada = consumo de metal (materiais com liga na ficha ou com lote de matéria-prima);
     * produzido = quantidade boa da última operação. Tudo convertido para kg pela unidade do produto
     * (KG direto; outras unidades só com o peso da ficha técnica). Sem conversão possível, o rendimento fica nulo.
     */
    Rendimento rendimento(OrdemProducao o, List<Consumo> consumos, List<Perda> perdas) {
        List<String> avisos = new ArrayList<>();
        Set<Long> ids = new HashSet<>(consumos.stream().map(c -> c.getProduto().getId()).toList());
        ids.add(o.getProduto().getId());
        Map<Long, FichaTecnica> fichas = fichaRepo.findByProdutoIdIn(ids).stream()
                .collect(Collectors.toMap(f -> f.getProduto().getId(), f -> f));

        BigDecimal entrada = BigDecimal.ZERO;
        boolean entradaOk = true;
        for (Consumo c : consumos) {
            FichaTecnica f = fichas.get(c.getProduto().getId());
            boolean metal = c.getLoteMpId() != null || (f != null && f.getLiga() != null);
            if (!metal) continue;
            BigDecimal kg = emKg(c.getProduto(), c.getQuantidade(), f);
            if (kg == null) {
                entradaOk = false;
                avisos.add("Sem peso para converter " + c.getProduto().getNome() + " (" + c.getProduto().getUnidadeMedida() + ") em kg.");
            } else entrada = entrada.add(kg);
        }

        OpOperacao ultima = o.getOperacoes().stream().max(Comparator.comparing(OpOperacao::getSequencia)).orElse(null);
        BigDecimal produzido = ultima == null ? BigDecimal.ZERO : apontamentoRepo.validosDasOrdens(List.of(o.getId())).stream()
                .filter(a -> a.getOperacao().getId().equals(ultima.getId()))
                .map(Apontamento::getQuantidadeBoa).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal produzidoKg = emKg(o.getProduto(), produzido, fichas.get(o.getProduto().getId()));
        if (produzidoKg == null) {
            avisos.add("Sem peso na ficha técnica para converter " + o.getProduto().getNome() + " em kg.");
        }
        BigDecimal perdasKg = perdas.stream().filter(p -> !p.getEstornada()).map(Perda::getQuantidadeKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (!entradaOk || produzidoKg == null) {
            return new Rendimento(entradaOk ? entrada : null, produzidoKg, perdasKg, null, null, avisos);
        }
        if (entrada.signum() == 0) avisos.add("Nenhum consumo de metal registrado ainda.");
        BigDecimal percentual = entrada.signum() == 0 ? null
                : produzidoKg.multiply(BigDecimal.valueOf(100)).divide(entrada, 2, RoundingMode.HALF_UP);
        return new Rendimento(entrada, produzidoKg, perdasKg, entrada.subtract(produzidoKg).subtract(perdasKg), percentual, avisos);
    }

    /** Converte para kg: KG direto; UN/PC pelo peso por unidade; M pelo peso linear. Senão, nulo. */
    static BigDecimal emKg(Produto p, BigDecimal quantidade, FichaTecnica f) {
        String un = p.getUnidadeMedida() == null ? "" : p.getUnidadeMedida().trim().toUpperCase(Locale.ROOT);
        if (un.equals("KG")) return quantidade;
        if (un.equals("T")) return quantidade.multiply(BigDecimal.valueOf(1000));
        if (f == null) return null;
        if (un.equals("M") && f.getPesoLinearKgM() != null) return quantidade.multiply(f.getPesoLinearKgM()).setScale(4, RoundingMode.HALF_UP);
        if (f.getPesoUnitarioKg() != null && !un.equals("M")) return quantidade.multiply(f.getPesoUnitarioKg()).setScale(4, RoundingMode.HALF_UP);
        return null;
    }

    static PerdaOut saida(Perda p, Map<Long, String> nomes) {
        return new PerdaOut(p.getId(), p.getOrdem().getId(), p.getApontamentoId(), p.getTipo(), p.getQuantidadeKg(), p.getObservacao(),
                nomes.get(p.getUsuarioId()), p.getCreatedAt(), p.getEstornada(), p.getMotivoEstorno());
    }
}
