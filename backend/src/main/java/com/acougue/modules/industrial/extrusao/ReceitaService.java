package com.acougue.modules.industrial.extrusao;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.engenharia.FichaTecnicaRepository;
import com.acougue.modules.industrial.estrutura.Maquina;
import com.acougue.modules.industrial.estrutura.TipoMaquina;
import com.acougue.modules.industrial.estrutura.TipoMaquinaRepository;
import com.acougue.modules.industrial.extrusao.ExtrusaoDto.*;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import com.acougue.repository.ProdutoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Receitas de processo. Toda alteração sobe a revisão; as medições já feitas guardam a revisão e a faixa
 * da época, então mudar a receita não reescreve o histórico.
 */
@Service
@RequiredArgsConstructor
public class ReceitaService {

    private final ReceitaRepository receitaRepo;
    private final LigaService ligaService;
    private final TipoMaquinaRepository tipoRepo;
    private final ProdutoRepository produtoRepo;
    private final FichaTecnicaRepository fichaRepo;

    @Transactional(readOnly = true)
    public List<ReceitaOut> listar(Long tipoMaquinaId, Long ligaId) {
        return receitaRepo.listar(tipoMaquinaId, ligaId).stream().map(ReceitaOut::de).toList();
    }

    /**
     * Receitas que valem para medir uma OP numa máquina: ativas, da liga do produto da OP (ficha técnica),
     * do tipo da máquina e sem produto específico ou com o produto da OP.
     */
    @Transactional(readOnly = true)
    public List<ReceitaOut> paraOrdem(OrdemProducao ordem, Maquina maquina) {
        Long ligaId = fichaRepo.findByProdutoId(ordem.getProduto().getId())
                .map(f -> f.getLiga() == null ? null : f.getLiga().getId()).orElse(null);
        if (ligaId == null) return List.of();
        return receitaRepo.listar(maquina == null ? null : maquina.getTipoMaquina().getId(), ligaId).stream()
                .filter(Receita::getAtivo)
                .filter(r -> r.getProduto() == null || r.getProduto().getId().equals(ordem.getProduto().getId()))
                .map(ReceitaOut::de).toList();
    }

    @Transactional
    public ReceitaOut salvar(Long id, ReceitaIn in, Long usuarioId) {
        if (!Receita.ETAPAS.contains(in.etapa())) throw new BusinessException("Etapa inválida: " + in.etapa());
        Receita r = id == null ? new Receita() : receita(id);
        String codigo = in.codigo().trim().toUpperCase(Locale.ROOT);
        receitaRepo.findByCodigoIgnoreCase(codigo).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("Já existe uma receita com o código " + codigo + ".");
        });
        Liga liga = ligaService.ligaAtiva(in.ligaId());
        TipoMaquina tipo = tipoRepo.findById(in.tipoMaquinaId())
                .orElseThrow(() -> new EntityNotFoundException("Tipo de máquina não encontrado: " + in.tipoMaquinaId()));
        Produto produto = in.produtoId() == null ? null : produtoRepo.findById(in.produtoId())
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + in.produtoId()));

        Set<String> codigos = new HashSet<>();
        List<ReceitaParametro> novos = new ArrayList<>();
        short ordem = 0;
        for (ParametroIn p : in.parametros()) {
            String cod = p.codigo().trim().toUpperCase(Locale.ROOT);
            if (!codigos.add(cod)) throw new BusinessException("O parâmetro " + cod + " aparece mais de uma vez.");
            if (p.minimo() != null && p.maximo() != null && p.minimo().compareTo(p.maximo()) > 0) {
                throw new BusinessException("Parâmetro " + p.nome() + ": o mínimo é maior que o máximo.");
            }
            if (p.alvo() != null && ((p.minimo() != null && p.alvo().compareTo(p.minimo()) < 0)
                    || (p.maximo() != null && p.alvo().compareTo(p.maximo()) > 0))) {
                throw new BusinessException("Parâmetro " + p.nome() + ": o alvo está fora da faixa.");
            }
            novos.add(ReceitaParametro.builder().receita(r).ordem(ordem++).codigo(cod).nome(p.nome().trim())
                    .unidade(LigaService.texto(p.unidade())).alvo(p.alvo()).minimo(p.minimo()).maximo(p.maximo())
                    .obrigatorio(p.obrigatorio() == null || p.obrigatorio()).build());
        }
        r.setCodigo(codigo);
        r.setDescricao(in.descricao().trim());
        r.setEtapa(in.etapa());
        r.setLiga(liga);
        r.setTipoMaquina(tipo);
        r.setProduto(produto);
        r.setObservacao(LigaService.texto(in.observacao()));
        r.setRevisao(id == null ? 1 : r.getRevisao() + 1);
        r.setAtualizadoPorId(usuarioId);
        r.setUpdatedAt(LocalDateTime.now());
        r.getParametros().clear();
        r = receitaRepo.saveAndFlush(r); // tira os parâmetros antigos antes de inserir (restrição única receita+código)
        for (ReceitaParametro p : novos) p.setReceita(r);
        r.getParametros().addAll(novos);
        return ReceitaOut.de(receitaRepo.saveAndFlush(r));
    }

    @Transactional
    public void ativar(Long id, boolean ativo) {
        receita(id).setAtivo(ativo);
    }

    Receita receita(Long id) {
        return receitaRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Receita não encontrada: " + id));
    }
}
