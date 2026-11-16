package com.acougue.modules.industrial.extrusao;

import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.estrutura.Maquina;
import com.acougue.modules.industrial.estrutura.MaquinaRepository;
import com.acougue.modules.industrial.execucao.Apontamento;
import com.acougue.modules.industrial.execucao.ApontamentoRepository;
import com.acougue.modules.industrial.execucao.ExecucaoMapper;
import com.acougue.modules.industrial.extrusao.ExtrusaoDto.*;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import com.acougue.modules.industrial.pcp.OrdemProducaoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Medições de processo contra a receita. Cada valor é conferido com a faixa vigente e a faixa é copiada para a
 * medição. Fora da faixa não bloqueia nada nem comanda máquina: marca a medição e exige a observação da ação tomada.
 */
@Service
@RequiredArgsConstructor
public class MedicaoService {

    static final List<String> OP_ACEITA = List.of("LIBERADA", "EM_PRODUCAO", "PAUSADA", "CONCLUIDA");
    static final List<String> ORIGENS = List.of("MANUAL", "SENSOR");

    private final MedicaoRepository medicaoRepo;
    private final ReceitaService receitaService;
    private final OrdemProducaoRepository ordemRepo;
    private final ApontamentoRepository apontamentoRepo;
    private final MaquinaRepository maquinaRepo;
    private final ExecucaoMapper mapper;

    /** Valor dentro de [mínimo, máximo]; um limite em branco não restringe. */
    static boolean dentro(BigDecimal valor, BigDecimal minimo, BigDecimal maximo) {
        return (minimo == null || valor.compareTo(minimo) >= 0) && (maximo == null || valor.compareTo(maximo) <= 0);
    }

    @Transactional(readOnly = true)
    public List<ReceitaOut> receitasPara(Long ordemId, Long maquinaId) {
        OrdemProducao o = ordemRepo.findById(ordemId).orElseThrow(() -> new EntityNotFoundException("OP não encontrada: " + ordemId));
        Maquina m = maquinaId == null ? null : maquinaRepo.findById(maquinaId)
                .orElseThrow(() -> new EntityNotFoundException("Máquina não encontrada: " + maquinaId));
        return receitaService.paraOrdem(o, m);
    }

    @Transactional
    public MedicaoOut registrar(MedicaoIn in, Long usuarioId) {
        Optional<Medicao> repetida = medicaoRepo.findByChave(in.chave());
        if (repetida.isPresent()) return saida(repetida.get(), mapper.nomes(List.of(usuarioId)));

        OrdemProducao ordem = ordemRepo.findById(in.ordemId())
                .orElseThrow(() -> new EntityNotFoundException("OP não encontrada: " + in.ordemId()));
        if (!OP_ACEITA.contains(ordem.getStatus())) {
            throw new BusinessException("A OP " + ordem.getNumero() + " não está liberada para produção.");
        }
        String origem = in.origem() == null ? "MANUAL" : in.origem();
        if (!ORIGENS.contains(origem)) throw new BusinessException("Origem inválida: " + origem);

        Maquina maquina = null;
        if (in.apontamentoId() != null) {
            Apontamento a = apontamentoRepo.findById(in.apontamentoId())
                    .orElseThrow(() -> new EntityNotFoundException("Apontamento não encontrado: " + in.apontamentoId()));
            if (!a.getOrdem().getId().equals(ordem.getId())) throw new BusinessException("O apontamento é de outra OP.");
            maquina = a.getMaquina();
        }
        if (maquina == null && in.maquinaId() != null) {
            maquina = maquinaRepo.findById(in.maquinaId())
                    .orElseThrow(() -> new EntityNotFoundException("Máquina não encontrada: " + in.maquinaId()));
        }
        Receita receita = receitaService.receita(in.receitaId());
        if (!receita.getAtivo()) throw new BusinessException("A receita " + receita.getCodigo() + " está inativa.");
        if (maquina != null && !maquina.getTipoMaquina().getId().equals(receita.getTipoMaquina().getId())) {
            throw new BusinessException("A receita " + receita.getCodigo() + " é para " + receita.getTipoMaquina().getNome()
                    + ", e a máquina " + maquina.getCodigo() + " é de outro tipo.");
        }

        Map<String, BigDecimal> valores = new HashMap<>();
        for (ValorIn v : in.valores()) {
            String cod = v.codigo().trim().toUpperCase(Locale.ROOT);
            if (valores.put(cod, v.valor()) != null) throw new BusinessException("O parâmetro " + cod + " foi informado duas vezes.");
        }
        Set<String> daReceita = receita.getParametros().stream().map(ReceitaParametro::getCodigo).collect(Collectors.toSet());
        valores.keySet().stream().filter(c -> !daReceita.contains(c)).findFirst().ifPresent(c -> {
            throw new BusinessException("O parâmetro " + c + " não faz parte da receita " + receita.getCodigo() + ".");
        });

        Medicao m = Medicao.builder().chave(in.chave()).ordem(ordem).apontamentoId(in.apontamentoId()).maquina(maquina)
                .receita(receita).receitaRevisao(receita.getRevisao()).etapa(receita.getEtapa()).origem(origem)
                .medidoEm(LocalDateTime.now()).observacao(LigaService.texto(in.observacao())).usuarioId(usuarioId).build();
        boolean fora = false;
        for (ReceitaParametro p : receita.getParametros()) {
            BigDecimal v = valores.get(p.getCodigo());
            if (v == null) {
                if (p.getObrigatorio()) throw new BusinessException("Informe " + p.getNome() + ".");
                continue;
            }
            boolean ok = dentro(v, p.getMinimo(), p.getMaximo());
            fora |= !ok;
            m.getValores().add(MedicaoValor.builder().medicao(m).codigo(p.getCodigo()).nome(p.getNome()).unidade(p.getUnidade())
                    .valor(v).alvo(p.getAlvo()).minimo(p.getMinimo()).maximo(p.getMaximo()).dentroFaixa(ok).build());
        }
        if (fora && m.getObservacao() == null) {
            throw new BusinessException("Há valor fora da faixa da receita. Descreva na observação a ação tomada.");
        }
        m.setForaEspecificacao(fora);
        return saida(medicaoRepo.saveAndFlush(m), mapper.nomes(List.of(usuarioId)));
    }

    @Transactional(readOnly = true)
    public List<MedicaoOut> daOrdem(Long ordemId) {
        List<Medicao> lista = medicaoRepo.daOrdem(ordemId);
        Map<Long, String> nomes = mapper.nomes(lista.stream().map(Medicao::getUsuarioId).toList());
        return lista.stream().map(m -> saida(m, nomes)).toList();
    }

    @Transactional(readOnly = true)
    public List<MedicaoOut> noPeriodo(LocalDate de, LocalDate ate, boolean somenteFora) {
        if (ate.isBefore(de)) throw new BusinessException("O fim do período não pode ser antes do início.");
        if (ChronoUnit.DAYS.between(de, ate) + 1 > 92) throw new BusinessException("Consulte no máximo 92 dias por vez.");
        List<Medicao> lista = medicaoRepo.noPeriodo(de.atStartOfDay(), ate.plusDays(1).atStartOfDay(), somenteFora);
        Map<Long, String> nomes = mapper.nomes(lista.stream().map(Medicao::getUsuarioId).toList());
        return lista.stream().map(m -> saida(m, nomes)).toList();
    }

    static MedicaoOut saida(Medicao m, Map<Long, String> nomes) {
        return new MedicaoOut(m.getId(), m.getOrdem().getId(), m.getOrdem().getNumero(), m.getApontamentoId(),
                m.getMaquina() == null ? null : m.getMaquina().getCodigo(), m.getReceita().getId(), m.getReceita().getCodigo(),
                m.getReceitaRevisao(), m.getEtapa(), m.getOrigem(), m.getMedidoEm(), m.getForaEspecificacao(), m.getObservacao(),
                nomes.get(m.getUsuarioId()),
                m.getValores().stream().map(v -> new ValorOut(v.getCodigo(), v.getNome(), v.getUnidade(), v.getValor(), v.getAlvo(),
                        v.getMinimo(), v.getMaximo(), v.getDentroFaixa())).toList());
    }
}
