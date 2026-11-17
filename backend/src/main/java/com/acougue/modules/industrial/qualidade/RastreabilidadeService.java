package com.acougue.modules.industrial.qualidade;

import com.acougue.exception.BusinessException;
import com.acougue.modules.fiscal.emissao.QrCodeSvg;
import com.acougue.modules.industrial.execucao.Consumo;
import com.acougue.modules.industrial.expedicao.ExpedicaoItem;
import com.acougue.modules.industrial.expedicao.ExpedicaoRepository;
import com.acougue.modules.industrial.extrusao.ConsumoLoteRepository;
import com.acougue.modules.industrial.extrusao.LoteMp;
import com.acougue.modules.industrial.extrusao.LoteMpRepository;
import com.acougue.modules.industrial.extrusao.MedicaoRepository;
import com.acougue.modules.industrial.qualidade.QualidadeDto.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Rastreabilidade "da origem ao cliente":
 * - para trás: lote → lotes de que veio (divisão/união) → OPs → lotes de matéria-prima consumidos (corrida, fornecedor);
 * - para frente: lote → lotes derivados → expedições carregadas → clientes;
 * - a partir de um lote de matéria-prima: OPs que o consumiram → lotes produzidos → clientes (base para recall).
 */
@Service
@RequiredArgsConstructor
public class RastreabilidadeService {

    static final int NIVEIS_MAXIMOS = 30;

    private final LoteRepository loteRepo;
    private final LoteGenealogiaRepository genealogiaRepo;
    private final LoteService loteService;
    private final InspecaoService inspecaoService;
    private final NaoConformidadeService ncService;
    private final ConsumoLoteRepository consumoRepo;
    private final LoteMpRepository loteMpRepo;
    private final MedicaoRepository medicaoRepo;
    private final ExpedicaoRepository expedicaoRepo;

    @Transactional(readOnly = true)
    public List<ResultadoBusca> buscar(String texto) {
        String t = texto == null ? "" : texto.trim();
        if (t.length() < 2) throw new BusinessException("Digite ao menos 2 caracteres.");
        List<ResultadoBusca> r = new ArrayList<>();
        loteRepo.findTop20ByCodigoContainingIgnoreCaseOrderByIdDesc(t)
                .forEach(l -> r.add(new ResultadoBusca("LOTE", l.getId(), l.getCodigo(), l.getProduto().getNome())));
        loteMpRepo.findTop20ByNumeroContainingIgnoreCaseOrderByIdDesc(t)
                .forEach(l -> r.add(new ResultadoBusca("LOTE_MP", l.getId(), l.getNumero(), l.getProduto().getNome() + " · liga " + l.getLiga().getCodigo())));
        return r;
    }

    @Transactional(readOnly = true)
    public EtiquetaOut etiqueta(Long loteId) {
        Lote l = loteService.lote(loteId);
        return new EtiquetaOut(l.getCodigo(), l.getProduto().getNome(), l.getProduto().getUnidadeMedida(), l.getQuantidadeInicial(),
                l.getOrdem() == null ? null : l.getOrdem().getNumero(),
                l.getCreatedAt() == null ? null : l.getCreatedAt().toLocalDate(), l.getSituacao(), QrCodeSvg.gerar(l.getCodigo()));
    }

    @Transactional(readOnly = true)
    public RastreioLote porLote(String codigo) {
        Lote l = loteRepo.findByCodigoIgnoreCase(codigo.trim())
                .orElseThrow(() -> new EntityNotFoundException("Lote não encontrado: " + codigo));

        List<LoteResumo> ascendentes = new ArrayList<>();
        Set<Long> visitados = new HashSet<>(Set.of(l.getId()));
        List<Lote> comOrdem = new ArrayList<>(List.of(l));
        subir(l, 1, visitados, ascendentes, comOrdem);

        List<LoteResumo> descendentes = new ArrayList<>();
        List<Long> linha = new ArrayList<>(List.of(l.getId()));
        descer(l, 1, new HashSet<>(Set.of(l.getId())), descendentes, linha);

        // origens: matéria-prima consumida pelas OPs do lote e dos lotes de que ele veio
        List<OrigemMaterial> origens = new ArrayList<>();
        Set<Long> ordens = new LinkedHashSet<>();
        comOrdem.stream().filter(x -> x.getOrdem() != null).forEach(x -> ordens.add(x.getOrdem().getId()));
        Map<Long, LoteMp> cacheMp = new HashMap<>();
        for (Long ordemId : ordens) {
            Map<Long, BigDecimal> porLote = new LinkedHashMap<>();
            String numeroOp = null;
            for (Consumo c : consumoRepo.validosDaOrdem(ordemId)) {
                numeroOp = c.getOrdem().getNumero();
                if (c.getLoteMpId() != null) porLote.merge(c.getLoteMpId(), c.getQuantidade(), BigDecimal::add);
            }
            for (var e : porLote.entrySet()) {
                LoteMp mp = cacheMp.computeIfAbsent(e.getKey(), id -> loteMpRepo.findById(id).orElse(null));
                if (mp == null) continue;
                origens.add(new OrigemMaterial(mp.getId(), mp.getNumero(), mp.getProduto().getNome(), mp.getLiga().getCodigo(),
                        mp.getFornecedor() == null ? null : mp.getFornecedor().getNome(), mp.getCertificado(), e.getValue(), numeroOp));
            }
        }

        long medicoesFora = l.getOrdem() == null ? 0 : medicaoRepo.countByOrdemIdAndForaEspecificacaoTrue(l.getOrdem().getId());
        return new RastreioLote(loteService.buscar(l.getId()), QrCodeSvg.gerar(l.getCodigo()), ascendentes, descendentes, origens,
                destinos(linha), inspecaoService.doLote(l.getId()), ncService.doLote(l.getId()), loteService.movimentos(l.getId()),
                medicoesFora);
    }

    @Transactional(readOnly = true)
    public RastreioLoteMp porLoteMp(Long loteMpId) {
        LoteMp mp = loteMpRepo.findById(loteMpId).orElseThrow(() -> new EntityNotFoundException("Lote de matéria-prima não encontrado: " + loteMpId));
        List<Consumo> consumos = consumoRepo.validosDoLoteMp(loteMpId);
        Set<Long> ordemIds = new LinkedHashSet<>();
        List<String> ordens = new ArrayList<>();
        for (Consumo c : consumos) {
            if (ordemIds.add(c.getOrdem().getId())) ordens.add(c.getOrdem().getNumero());
        }
        List<LoteResumo> produzidos = new ArrayList<>();
        List<Long> todos = new ArrayList<>();
        Set<Long> visitados = new HashSet<>();
        if (!ordemIds.isEmpty()) {
            for (Lote l : loteRepo.findByOrdemIdIn(ordemIds)) {
                if (!visitados.add(l.getId()) || "ESTORNADO".equals(l.getSituacao())) continue;
                produzidos.add(resumo(l, 1));
                todos.add(l.getId());
                descer(l, 2, visitados, produzidos, todos);
            }
        }
        return new RastreioLoteMp(mp.getId(), mp.getNumero(), mp.getProduto().getNome(), mp.getLiga().getCodigo(),
                mp.getFornecedor() == null ? null : mp.getFornecedor().getNome(), mp.getCertificado(), mp.getStatus(), ordens,
                produzidos, destinos(todos));
    }

    private void subir(Lote l, int nivel, Set<Long> visitados, List<LoteResumo> saida, List<Lote> comOrdem) {
        if (nivel > NIVEIS_MAXIMOS) return;
        for (LoteGenealogia g : genealogiaRepo.pais(l.getId())) {
            Lote pai = g.getLotePai();
            if (!visitados.add(pai.getId())) continue;
            saida.add(resumo(pai, nivel));
            comOrdem.add(pai);
            subir(pai, nivel + 1, visitados, saida, comOrdem);
        }
    }

    private void descer(Lote l, int nivel, Set<Long> visitados, List<LoteResumo> saida, List<Long> ids) {
        if (nivel > NIVEIS_MAXIMOS) return;
        for (LoteGenealogia g : genealogiaRepo.filhos(l.getId())) {
            Lote filho = g.getLote();
            if (!visitados.add(filho.getId())) continue;
            saida.add(resumo(filho, nivel));
            ids.add(filho.getId());
            descer(filho, nivel + 1, visitados, saida, ids);
        }
    }

    private List<Destino> destinos(Collection<Long> loteIds) {
        if (loteIds.isEmpty()) return List.of();
        return expedicaoRepo.carregadasDosLotes(loteIds).stream()
                .sorted(Comparator.comparing((ExpedicaoItem.Reserva r) -> r.getItem().getExpedicao().getCarregadaEm(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(r -> new Destino(r.getItem().getExpedicao().getNumero(), r.getItem().getExpedicao().getCliente().getNome(),
                        r.getItem().getExpedicao().getCliente().getCpfCnpj(), r.getItem().getExpedicao().getCarregadaEm(),
                        r.getLote().getCodigo(), r.getQuantidade()))
                .toList();
    }

    private static LoteResumo resumo(Lote l, int nivel) {
        return new LoteResumo(l.getId(), l.getCodigo(), l.getProduto().getNome(), l.getSituacao(), l.getQuantidadeInicial(),
                l.getSaldo(), l.getOrdem() == null ? null : l.getOrdem().getNumero(), nivel);
    }
}
