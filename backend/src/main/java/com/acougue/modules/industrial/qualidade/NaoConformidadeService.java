package com.acougue.modules.industrial.qualidade;

import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.execucao.ExecucaoMapper;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import com.acougue.modules.industrial.pcp.OrdemProducaoRepository;
import com.acougue.modules.industrial.qualidade.QualidadeDto.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Não conformidades. Abrir com lote bloqueia o lote (se ainda não estiver). Encerrar exige causa, ação corretiva
 * e, com lote, a disposição: USAR_COMO_ESTA libera o lote (concessão), SUCATEAR baixa o saldo do lote e do estoque
 * como descarte, RETRABALHAR mantém o lote bloqueado até a reinspeção.
 */
@Service
@RequiredArgsConstructor
public class NaoConformidadeService {

    private final NaoConformidadeRepository ncRepo;
    private final LoteRepository loteRepo;
    private final OrdemProducaoRepository ordemRepo;
    private final LoteService loteService;
    private final ExecucaoMapper mapper;

    @Transactional(readOnly = true)
    public List<NcOut> listar(String status) {
        List<NaoConformidade> lista = ncRepo.listar(status == null || status.isBlank() ? null : status);
        Map<Long, String> nomes = mapper.nomes(lista.stream().flatMap(n -> Stream.of(n.getAbertaPorId(), n.getEncerradaPorId())).toList());
        return lista.stream().map(n -> saida(n, nomes)).toList();
    }

    @Transactional(readOnly = true)
    public List<NcOut> doLote(Long loteId) {
        List<NaoConformidade> lista = ncRepo.doLote(loteId);
        Map<Long, String> nomes = mapper.nomes(lista.stream().flatMap(n -> Stream.of(n.getAbertaPorId(), n.getEncerradaPorId())).toList());
        return lista.stream().map(n -> saida(n, nomes)).toList();
    }

    @Transactional
    public NcOut abrir(NcIn in, Long usuarioId) {
        if (!NaoConformidade.ORIGENS.contains(in.origem())) throw new BusinessException("Origem inválida: " + in.origem());
        Lote lote = null;
        if (in.loteId() != null) {
            lote = loteRepo.travar(in.loteId()).orElseThrow(() -> new EntityNotFoundException("Lote não encontrado: " + in.loteId()));
            if ("ESTORNADO".equals(lote.getSituacao())) throw new BusinessException("O lote " + lote.getCodigo() + " foi estornado.");
        }
        OrdemProducao ordem = in.ordemId() != null
                ? ordemRepo.findById(in.ordemId()).orElseThrow(() -> new EntityNotFoundException("OP não encontrada: " + in.ordemId()))
                : lote != null ? lote.getOrdem() : null;
        NaoConformidade n = abrirAutomatica(in.origem(), lote, ordem, null, in.descricao().trim(), usuarioId);
        return saida(n, mapper.nomes(List.of(usuarioId)));
    }

    /** Abre a NC (também usada pela inspeção reprovada) e bloqueia o lote, se houver. */
    @Transactional
    public NaoConformidade abrirAutomatica(String origem, Lote lote, OrdemProducao ordem, Long inspecaoId, String descricao, Long usuarioId) {
        NaoConformidade n = ncRepo.saveAndFlush(NaoConformidade.builder()
                .numero(String.format("NC%06d", ncRepo.proximoNumero())).origem(origem).lote(lote).ordem(ordem)
                .inspecaoId(inspecaoId).descricao(descricao).abertaPorId(usuarioId).build());
        if (lote != null && !"BLOQUEADO".equals(lote.getSituacao())) {
            loteService.situacao(lote, "BLOQUEADO", "Não conformidade " + n.getNumero(), usuarioId);
        }
        return n;
    }

    @Transactional
    public NcOut encerrar(Long id, EncerrarNcIn in, Long usuarioId) {
        NaoConformidade n = ncRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Não conformidade não encontrada: " + id));
        if (!in.versao().equals(n.getVersao())) {
            throw new BusinessException("A " + n.getNumero() + " foi alterada por outra pessoa. Recarregue e tente de novo.");
        }
        if (!"ABERTA".equals(n.getStatus())) throw new BusinessException("A " + n.getNumero() + " já está encerrada.");
        String disposicao = in.disposicao();
        Lote lote = n.getLote();
        if (lote != null) {
            if (disposicao == null || "SEM_LOTE".equals(disposicao) || !NaoConformidade.DISPOSICOES.contains(disposicao)) {
                throw new BusinessException("Escolha a disposição do lote: usar como está, retrabalhar ou sucatear.");
            }
        } else disposicao = "SEM_LOTE";

        n.setStatus("ENCERRADA");
        n.setDisposicao(disposicao);
        n.setCausa(in.causa().trim());
        n.setAcaoCorretiva(in.acaoCorretiva().trim());
        n.setEncerradaPorId(usuarioId);
        n.setEncerradaEm(LocalDateTime.now());
        n = ncRepo.saveAndFlush(n);

        if (lote != null) {
            Lote l = loteRepo.travar(lote.getId()).orElseThrow();
            boolean outraAberta = ncRepo.abertasDoLote(l.getId()) > 0;
            switch (disposicao) {
                case "USAR_COMO_ESTA" -> {
                    if (!outraAberta && "BLOQUEADO".equals(l.getSituacao())) {
                        loteService.situacao(l, "LIBERADO", "Concessão: " + n.getNumero() + " (usar como está)", usuarioId);
                    }
                }
                case "SUCATEAR" -> {
                    loteService.sucatear(l, n.getNumero(), usuarioId);
                    loteService.situacao(l, "BLOQUEADO", "Sucateado: " + n.getNumero(), usuarioId);
                }
                default -> loteService.situacao(l, "BLOQUEADO", "Retrabalho: " + n.getNumero() + ". Reinspecione depois.", usuarioId);
            }
        }
        return saida(n, mapper.nomes(Stream.of(n.getAbertaPorId(), usuarioId).toList()));
    }

    static NcOut saida(NaoConformidade n, Map<Long, String> nomes) {
        return new NcOut(n.getId(), n.getNumero(), n.getOrigem(), n.getLote() == null ? null : n.getLote().getId(),
                n.getLote() == null ? null : n.getLote().getCodigo(), n.getOrdem() == null ? null : n.getOrdem().getId(),
                n.getOrdem() == null ? null : n.getOrdem().getNumero(), n.getInspecaoId(), n.getDescricao(), n.getStatus(),
                n.getDisposicao(), n.getCausa(), n.getAcaoCorretiva(), nomes.get(n.getAbertaPorId()), n.getCreatedAt(),
                nomes.get(n.getEncerradaPorId()), n.getEncerradaEm(), n.getVersao());
    }
}
