package com.acougue.modules.industrial.execucao;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.estoque.EstoqueService;
import com.acougue.modules.industrial.estrutura.Maquina;
import com.acougue.modules.industrial.estrutura.MaquinaRepository;
import com.acougue.modules.industrial.estrutura.MaquinaService;
import com.acougue.modules.industrial.execucao.ExecucaoDto.*;
import com.acougue.modules.industrial.pcp.OpOperacao;
import com.acougue.modules.industrial.pcp.OpOperacaoRepository;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import com.acougue.modules.industrial.pcp.OrdemProducaoService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Apontamento de produção: início, pausa (parada com motivo), retomada, fim com quantidades e estorno.
 *
 * Efeitos:
 * - o primeiro início numa OP liberada a coloca em produção; a máquina vai para "em operação";
 * - pausa abre uma parada (máquina "parada"); retomar fecha e soma o tempo parado;
 * - finalizar fecha o apontamento; na última operação, a quantidade boa entra no estoque como produção
 *   (com um lote da OP); concluir a última operação pendente conclui a OP;
 * - estornar desfaz a entrada no estoque e reabre a operação/OP se for o caso.
 * Comandos repetidos com a mesma chave devolvem o resultado já gravado, sem efeito duplicado.
 */
@Service
@RequiredArgsConstructor
public class ApontamentoService {

    static final List<String> OP_APONTAVEL = List.of("LIBERADA", "EM_PRODUCAO");

    private final ApontamentoRepository apontamentoRepo;
    private final ParadaRepository paradaRepo;
    private final ParadaService paradaService;
    private final OpOperacaoRepository operacaoRepo;
    private final MaquinaRepository maquinaRepo;
    private final MaquinaService maquinaService;
    private final OrdemProducaoService ordemService;
    private final EstoqueService estoque;
    private final ExecucaoMapper mapper;

    /** Documento das movimentações de estoque do apontamento (cabe nos 50 caracteres de documento_ref). */
    static String documento(Apontamento a) {
        return a.getOrdem().getNumero() + "/AP" + a.getId();
    }

    @Transactional
    public ApontamentoOut iniciar(IniciarIn in, Long usuarioId) {
        Optional<Apontamento> repetido = apontamentoRepo.findByChave(in.chave());
        if (repetido.isPresent()) return saida(repetido.get());

        OpOperacao op = operacaoRepo.findById(in.operacaoId())
                .orElseThrow(() -> new EntityNotFoundException("Operação não encontrada: " + in.operacaoId()));
        OrdemProducao ordem = op.getOrdem();
        if ("PAUSADA".equals(ordem.getStatus())) throw new BusinessException("A OP " + ordem.getNumero() + " está pausada pelo PCP.");
        if (!OP_APONTAVEL.contains(ordem.getStatus())) {
            throw new BusinessException("A OP " + ordem.getNumero() + " não está liberada para produção.");
        }
        if ("CONCLUIDA".equals(op.getStatus())) throw new BusinessException("Esta operação já foi concluída.");
        apontamentoRepo.abertoDoOperador(usuarioId).ifPresent(a -> {
            throw new BusinessException("Você já tem um apontamento aberto (" + a.getOrdem().getNumero()
                    + ", operação " + a.getOperacao().getSequencia() + "). Finalize-o antes.");
        });

        Maquina maquina = escolherMaquina(op, in.maquinaId());
        Apontamento a = apontamentoRepo.saveAndFlush(Apontamento.builder().chave(in.chave()).ordem(ordem).operacao(op)
                .maquina(maquina).operadorId(usuarioId).inicio(LocalDateTime.now())
                .observacao(ParadaService.texto(in.observacao())).build());

        if ("PENDENTE".equals(op.getStatus())) op.setStatus("EM_EXECUCAO");
        if ("LIBERADA".equals(ordem.getStatus())) {
            ordemService.statusPelaExecucao(ordem, "EM_PRODUCAO", "Início do primeiro apontamento", usuarioId);
        }
        if (maquina != null) {
            maquinaService.statusPelaProducao(maquina, "EM_OPERACAO", "Início de produção: " + ordem.getNumero(), usuarioId);
        }
        return saida(a);
    }

    /** Máquina informada, ou a planejada pelo PCP. Precisa ser do centro, do tipo pedido e estar livre. */
    private Maquina escolherMaquina(OpOperacao op, Long maquinaId) {
        Long id = maquinaId != null ? maquinaId : op.getMaquina() == null ? null : op.getMaquina().getId();
        if (id == null) {
            boolean centroTemMaquina = maquinaRepo.countByCentroTrabalhoIdAndStatusNot(op.getCentroTrabalho().getId(), "INATIVA") > 0;
            if (centroTemMaquina) throw new BusinessException("Escolha a máquina em que a operação vai rodar.");
            return null;
        }
        Maquina m = maquinaRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Máquina não encontrada: " + id));
        if (!m.getCentroTrabalho().getId().equals(op.getCentroTrabalho().getId())) {
            throw new BusinessException("A máquina " + m.getCodigo() + " não é do centro " + op.getCentroTrabalho().getNome() + ".");
        }
        if (op.getTipoMaquina() != null && !op.getTipoMaquina().getId().equals(m.getTipoMaquina().getId())) {
            throw new BusinessException("A operação pede uma máquina do tipo " + op.getTipoMaquina().getNome() + ".");
        }
        if (List.of("INATIVA", "EM_MANUTENCAO").contains(m.getStatus())) {
            throw new BusinessException("A máquina " + m.getCodigo() + " está "
                    + ("INATIVA".equals(m.getStatus()) ? "inativa." : "em manutenção."));
        }
        apontamentoRepo.abertoDaMaquina(m.getId()).ifPresent(a -> {
            throw new BusinessException("A máquina " + m.getCodigo() + " já está em uso na " + a.getOrdem().getNumero() + ".");
        });
        paradaRepo.abertaDaMaquina(m.getId()).ifPresent(p -> {
            throw new BusinessException("A máquina " + m.getCodigo() + " está parada (" + p.getMotivo().getDescricao()
                    + "). Encerre a parada antes.");
        });
        return m;
    }

    @Transactional
    public ApontamentoOut pausar(Long id, PausarIn in, Long usuarioId, boolean supervisor) {
        if (paradaRepo.findByChave(in.chave()).isPresent()) return saida(apontamento(id));
        Apontamento a = apontamento(id);
        exigirDono(a, usuarioId, supervisor);
        if (!"EM_ANDAMENTO".equals(a.getStatus())) throw new BusinessException("O apontamento não está em andamento.");
        MotivoParada motivo = paradaService.motivoAtivo(in.motivoId());
        paradaRepo.saveAndFlush(Parada.builder().chave(in.chave()).maquina(a.getMaquina()).motivo(motivo).apontamento(a)
                .ordemId(a.getOrdem().getId()).inicio(LocalDateTime.now()).observacao(ParadaService.texto(in.observacao()))
                .usuarioId(usuarioId).build());
        a.setStatus("PAUSADO");
        if (a.getMaquina() != null) {
            maquinaService.statusPelaProducao(a.getMaquina(), "PARADA", "Pausa: " + motivo.getDescricao(), usuarioId);
        }
        return saida(apontamentoRepo.saveAndFlush(a));
    }

    @Transactional
    public ApontamentoOut retomar(Long id, Long usuarioId, boolean supervisor) {
        Apontamento a = apontamento(id);
        if ("EM_ANDAMENTO".equals(a.getStatus())) return saida(a); // repetido: já retomou
        exigirDono(a, usuarioId, supervisor);
        if (!"PAUSADO".equals(a.getStatus())) throw new BusinessException("O apontamento não está pausado.");
        fecharPausa(a, usuarioId);
        a.setStatus("EM_ANDAMENTO");
        if (a.getMaquina() != null) {
            maquinaService.statusPelaProducao(a.getMaquina(), "EM_OPERACAO", "Retomada: " + a.getOrdem().getNumero(), usuarioId);
        }
        return saida(apontamentoRepo.saveAndFlush(a));
    }

    @Transactional
    public ApontamentoOut finalizar(Long id, FinalizarIn in, Long usuarioId, boolean supervisor) {
        Apontamento a = apontamento(id);
        if ("FINALIZADO".equals(a.getStatus()) && in.chave().equals(a.getChaveFinalizacao())) return saida(a);
        exigirDono(a, usuarioId, supervisor);
        if (!a.aberto()) throw new BusinessException("Este apontamento já foi finalizado.");
        if (in.quantidadeRefugo().signum() > 0 && ParadaService.texto(in.motivoRefugo()) == null) {
            throw new BusinessException("Informe o motivo do refugo.");
        }
        if ("PAUSADO".equals(a.getStatus())) fecharPausa(a, usuarioId);

        OpOperacao op = a.getOperacao();
        OrdemProducao ordem = a.getOrdem();
        a.setFim(LocalDateTime.now());
        a.setStatus("FINALIZADO");
        a.setChaveFinalizacao(in.chave());
        a.setQuantidadeBoa(in.quantidadeBoa());
        a.setQuantidadeRefugo(in.quantidadeRefugo());
        a.setMotivoRefugo(ParadaService.texto(in.motivoRefugo()));
        if (ParadaService.texto(in.observacao()) != null) a.setObservacao(in.observacao().trim());
        a.setConcluiuOperacao(in.concluirOperacao());
        a = apontamentoRepo.saveAndFlush(a);

        // Última operação: o que saiu bom é produto acabado e entra no estoque, com lote da OP.
        if (ExecucaoMapper.ultimaOperacao(op) && in.quantidadeBoa().signum() > 0) {
            long sequencia = apontamentoRepo.lotesDaOrdem(ordem.getId()) + 1;
            a.setLote(String.format("%s-L%02d", ordem.getNumero(), sequencia));
            Produto produto = ordem.getProduto();
            estoque.entrada(produto, in.quantidadeBoa(), null, "ENTRADA_PRODUCAO", documento(a), usuarioId);
            a.setEntradaEstoque(true);
            a = apontamentoRepo.saveAndFlush(a);
        }
        if (a.getMaquina() != null) {
            maquinaService.statusPelaProducao(a.getMaquina(), "DISPONIVEL", "Fim do apontamento: " + ordem.getNumero(), usuarioId);
        }
        if (in.concluirOperacao()) {
            op.setStatus("CONCLUIDA");
            boolean todas = ordem.getOperacoes().stream().allMatch(x -> "CONCLUIDA".equals(x.getStatus()));
            if (todas && "EM_PRODUCAO".equals(ordem.getStatus())) {
                ordemService.statusPelaExecucao(ordem, "CONCLUIDA", "Todas as operações concluídas", usuarioId);
            }
        }
        return saida(a);
    }

    @Transactional
    public ApontamentoOut estornar(Long id, EstornoIn in, Long usuarioId) {
        Apontamento a = apontamento(id);
        if ("ESTORNADO".equals(a.getStatus())) return saida(a);
        if (!"FINALIZADO".equals(a.getStatus())) {
            throw new BusinessException("Só apontamentos finalizados são estornados. Finalize este antes (pode ser com quantidade zero).");
        }
        OrdemProducao ordem = a.getOrdem();
        if (List.of("ENCERRADA", "CANCELADA").contains(ordem.getStatus())) {
            throw new BusinessException("A OP " + ordem.getNumero() + " está " + ordem.getStatus().toLowerCase() + ".");
        }
        if (Boolean.TRUE.equals(a.getEntradaEstoque())) {
            // falha com mensagem clara se o produto já saiu do estoque (vendido ou consumido)
            estoque.saida(ordem.getProduto(), a.getQuantidadeBoa(), "SAIDA_ESTORNO_PRODUCAO", documento(a), usuarioId);
        }
        a.setStatus("ESTORNADO");
        a.setEstornadoEm(LocalDateTime.now());
        a.setEstornadoPorId(usuarioId);
        a.setMotivoEstorno(in.motivo().trim());
        a = apontamentoRepo.saveAndFlush(a);

        OpOperacao op = a.getOperacao();
        if ("CONCLUIDA".equals(op.getStatus()) && Boolean.TRUE.equals(a.getConcluiuOperacao())) {
            op.setStatus("EM_EXECUCAO");
        }
        if ("CONCLUIDA".equals(ordem.getStatus()) && !"CONCLUIDA".equals(op.getStatus())) {
            ordemService.statusPelaExecucao(ordem, "EM_PRODUCAO", "Estorno de apontamento: " + in.motivo().trim(), usuarioId);
        }
        return saida(a);
    }

    // ── auxiliares ──────────────────────────────────────────────────────
    private void fecharPausa(Apontamento a, Long usuarioId) {
        paradaRepo.abertaDoApontamento(a.getId()).ifPresent(p -> {
            p.setFim(LocalDateTime.now());
            p.setEncerradaPorId(usuarioId);
            paradaRepo.save(p);
            a.setMinutosParado(a.getMinutosParado().add(ExecucaoMapper.minutos(p.getInicio(), p.getFim())));
        });
    }

    /** O próprio operador mexe no seu apontamento; outra pessoa só com permissão de editar apontamentos. */
    static void exigirDono(Apontamento a, Long usuarioId, boolean supervisor) {
        if (!supervisor && !Objects.equals(a.getOperadorId(), usuarioId)) {
            throw new BusinessException("Este apontamento é de outro operador.");
        }
    }

    private Apontamento apontamento(Long id) {
        return apontamentoRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Apontamento não encontrado: " + id));
    }

    ApontamentoOut saida(Apontamento a) {
        Parada aberta = a.aberto() ? paradaRepo.abertaDoApontamento(a.getId()).orElse(null) : null;
        return mapper.apontamento(a, aberta, mapper.nomes(List.of(a.getOperadorId(), aberta == null ? a.getOperadorId() : aberta.getUsuarioId())));
    }
}
