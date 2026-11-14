package com.acougue.modules.industrial.engenharia;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.engenharia.EngenhariaDto.*;
import com.acougue.modules.industrial.estrutura.CentroTrabalho;
import com.acougue.modules.industrial.estrutura.CentroTrabalhoRepository;
import com.acougue.modules.industrial.estrutura.TipoMaquina;
import com.acougue.modules.industrial.estrutura.TipoMaquinaRepository;
import com.acougue.repository.ProdutoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Roteiro de fabricação versionado, com as mesmas regras de versão da BOM. */
@Service
@RequiredArgsConstructor
public class RoteiroService {

    private final RoteiroRepository roteiroRepo;
    private final ProdutoRepository produtoRepo;
    private final CentroTrabalhoRepository centroRepo;
    private final TipoMaquinaRepository tipoRepo;

    @Transactional(readOnly = true)
    public List<RoteiroResumo> listar(Long produtoId) {
        return roteiroRepo.listar(produtoId).stream().map(RoteiroResumo::de).toList();
    }

    @Transactional(readOnly = true)
    public RoteiroOut buscar(Long id) {
        return RoteiroOut.de(roteiro(id));
    }

    @Transactional
    public RoteiroOut criar(RoteiroIn in, Long usuarioId) {
        Produto produto = FichaTecnicaService.produtoAtivo(produtoRepo, in.produtoId());
        exigirSemRascunho(produto);
        Roteiro r = Roteiro.builder().produto(produto).versao(roteiroRepo.ultimaVersao(produto.getId()) + 1)
                .criadoPorId(usuarioId).build();
        aplicar(r, in);
        return RoteiroOut.de(roteiroRepo.save(r));
    }

    @Transactional
    public RoteiroOut editar(Long id, RoteiroIn in) {
        Roteiro r = roteiro(id);
        exigirRascunho(r);
        if (!r.getProduto().getId().equals(in.produtoId())) {
            throw new BusinessException("O produto de um roteiro não pode ser trocado. Crie um roteiro para o outro produto.");
        }
        aplicar(r, in);
        return RoteiroOut.de(roteiroRepo.save(r));
    }

    @Transactional
    public void excluir(Long id) {
        Roteiro r = roteiro(id);
        if (!"RASCUNHO".equals(r.getStatus())) {
            throw new BusinessException("Só rascunhos podem ser excluídos. Versões ativadas ficam no histórico.");
        }
        roteiroRepo.delete(r);
    }

    @Transactional
    public RoteiroOut ativar(Long id, Long usuarioId) {
        Roteiro r = roteiro(id);
        exigirRascunho(r);
        if (r.getOperacoes().isEmpty()) throw new BusinessException("Inclua ao menos uma operação antes de ativar o roteiro.");
        for (RoteiroOperacao o : r.getOperacoes()) {
            if (!o.getCentroTrabalho().getAtivo()) {
                throw new BusinessException("O centro de trabalho " + o.getCentroTrabalho().getNome() + " está inativo.");
            }
        }
        LocalDate hoje = LocalDate.now();
        roteiroRepo.ativoDoProduto(r.getProduto().getId()).ifPresent(atual -> {
            atual.setStatus("OBSOLETO");
            LocalDate fim = hoje.minusDays(1);
            atual.setVigenciaFim(atual.getVigenciaInicio() != null && fim.isBefore(atual.getVigenciaInicio())
                    ? atual.getVigenciaInicio() : fim);
            roteiroRepo.saveAndFlush(atual);
        });
        r.setStatus("ATIVO");
        r.setVigenciaInicio(hoje);
        r.setAtivadoPorId(usuarioId);
        r.setAtivadoEm(LocalDateTime.now());
        return RoteiroOut.de(roteiroRepo.saveAndFlush(r));
    }

    @Transactional
    public RoteiroOut obsoletar(Long id) {
        Roteiro r = roteiro(id);
        if (!"ATIVO".equals(r.getStatus())) throw new BusinessException("Só a versão ativa pode ser tornada obsoleta.");
        r.setStatus("OBSOLETO");
        LocalDate hoje = LocalDate.now();
        r.setVigenciaFim(r.getVigenciaInicio() != null && hoje.isBefore(r.getVigenciaInicio()) ? r.getVigenciaInicio() : hoje);
        return RoteiroOut.de(roteiroRepo.save(r));
    }

    @Transactional
    public RoteiroOut novaVersao(Long id, Long usuarioId) {
        Roteiro origem = roteiro(id);
        exigirSemRascunho(origem.getProduto());
        Roteiro r = Roteiro.builder().produto(origem.getProduto())
                .versao(roteiroRepo.ultimaVersao(origem.getProduto().getId()) + 1)
                .observacao(origem.getObservacao()).criadoPorId(usuarioId).build();
        for (RoteiroOperacao o : origem.getOperacoes()) {
            r.getOperacoes().add(RoteiroOperacao.builder().roteiro(r).sequencia(o.getSequencia()).descricao(o.getDescricao())
                    .centroTrabalho(o.getCentroTrabalho()).tipoMaquina(o.getTipoMaquina()).tempoSetupMin(o.getTempoSetupMin())
                    .tempoPorUnidadeMin(o.getTempoPorUnidadeMin()).instrucoes(o.getInstrucoes()).build());
        }
        return RoteiroOut.de(roteiroRepo.save(r));
    }

    private void aplicar(Roteiro r, RoteiroIn in) {
        r.setObservacao(FichaTecnicaService.texto(in.observacao()));
        Set<Integer> sequencias = new HashSet<>();
        List<RoteiroOperacao> novas = new ArrayList<>();
        for (OperacaoIn o : in.operacoes()) {
            if (!sequencias.add(o.sequencia())) throw new BusinessException("A sequência " + o.sequencia() + " está repetida.");
            CentroTrabalho centro = centroRepo.findById(o.centroTrabalhoId())
                    .orElseThrow(() -> new EntityNotFoundException("Centro de trabalho não encontrado: " + o.centroTrabalhoId()));
            if (!centro.getAtivo()) throw new BusinessException("O centro de trabalho " + centro.getNome() + " está inativo.");
            TipoMaquina tipo = null;
            if (o.tipoMaquinaId() != null) {
                tipo = tipoRepo.findById(o.tipoMaquinaId())
                        .orElseThrow(() -> new EntityNotFoundException("Tipo de máquina não encontrado: " + o.tipoMaquinaId()));
                if (!tipo.getAtivo()) throw new BusinessException("O tipo de máquina " + tipo.getNome() + " está inativo.");
            }
            novas.add(RoteiroOperacao.builder().roteiro(r).sequencia(o.sequencia()).descricao(o.descricao().trim())
                    .centroTrabalho(centro).tipoMaquina(tipo).tempoSetupMin(o.tempoSetupMin())
                    .tempoPorUnidadeMin(o.tempoPorUnidadeMin()).instrucoes(FichaTecnicaService.texto(o.instrucoes())).build());
        }
        novas.sort(Comparator.comparing(RoteiroOperacao::getSequencia));
        r.getOperacoes().clear();
        r.getOperacoes().addAll(novas);
    }

    private void exigirRascunho(Roteiro r) {
        if (!"RASCUNHO".equals(r.getStatus())) {
            throw new BusinessException("A versão " + r.getVersao() + " já foi ativada e não pode mais ser alterada. "
                    + "Crie uma nova versão a partir dela.");
        }
    }

    private void exigirSemRascunho(Produto produto) {
        roteiroRepo.findFirstByProdutoIdAndStatus(produto.getId(), "RASCUNHO").ifPresent(r -> {
            throw new BusinessException("Já existe a versão " + r.getVersao() + " do roteiro em rascunho para "
                    + produto.getNome() + ". Edite ou exclua esse rascunho.");
        });
    }

    private Roteiro roteiro(Long id) {
        return roteiroRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Roteiro não encontrado: " + id));
    }
}
