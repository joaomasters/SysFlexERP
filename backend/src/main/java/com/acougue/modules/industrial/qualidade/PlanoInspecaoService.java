package com.acougue.modules.industrial.qualidade;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.qualidade.QualidadeDto.*;
import com.acougue.repository.ProdutoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** Planos de inspeção por produto (um ativo por produto). Cada alteração sobe a revisão. */
@Service
@RequiredArgsConstructor
public class PlanoInspecaoService {

    static final List<String> TIPOS = List.of("MEDIDA", "ATRIBUTO");

    private final PlanoInspecaoRepository planoRepo;
    private final ProdutoRepository produtoRepo;

    @Transactional(readOnly = true)
    public List<PlanoOut> listar() {
        return planoRepo.listar().stream().map(PlanoOut::de).toList();
    }

    @Transactional
    public PlanoOut salvar(Long id, PlanoIn in, Long usuarioId) {
        PlanoInspecao p = id == null ? new PlanoInspecao() : plano(id);
        String codigo = in.codigo().trim().toUpperCase(Locale.ROOT);
        planoRepo.findByCodigoIgnoreCase(codigo).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("Já existe um plano com o código " + codigo + ".");
        });
        Produto produto = produtoRepo.findById(in.produtoId())
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + in.produtoId()));
        if (id == null || Boolean.TRUE.equals(p.getAtivo())) exigirUnicoAtivo(produto, id);

        Set<String> codigos = new HashSet<>();
        List<PlanoInspecao.Caracteristica> novas = new ArrayList<>();
        short ordem = 0;
        for (CaracteristicaIn c : in.caracteristicas()) {
            String cod = c.codigo().trim().toUpperCase(Locale.ROOT);
            if (!codigos.add(cod)) throw new BusinessException("A característica " + cod + " aparece mais de uma vez.");
            if (!TIPOS.contains(c.tipo())) throw new BusinessException("Tipo de característica inválido: " + c.tipo());
            if ("MEDIDA".equals(c.tipo()) && c.minimo() == null && c.maximo() == null) {
                throw new BusinessException("A medida " + c.nome() + " precisa de mínimo ou máximo.");
            }
            if (c.minimo() != null && c.maximo() != null && c.minimo().compareTo(c.maximo()) > 0) {
                throw new BusinessException("Característica " + c.nome() + ": o mínimo é maior que o máximo.");
            }
            boolean medida = "MEDIDA".equals(c.tipo());
            novas.add(PlanoInspecao.Caracteristica.builder().ordem(ordem++).codigo(cod).nome(c.nome().trim()).tipo(c.tipo())
                    .unidade(medida ? LoteService.texto(c.unidade()) : null).minimo(medida ? c.minimo() : null)
                    .maximo(medida ? c.maximo() : null).obrigatorio(c.obrigatorio() == null || c.obrigatorio()).build());
        }
        p.setCodigo(codigo);
        p.setDescricao(in.descricao().trim());
        p.setProduto(produto);
        p.setObservacao(LoteService.texto(in.observacao()));
        p.setRevisao(id == null ? 1 : p.getRevisao() + 1);
        p.setAtualizadoPorId(usuarioId);
        p.setUpdatedAt(LocalDateTime.now());
        p.getCaracteristicas().clear();
        p = planoRepo.saveAndFlush(p); // tira as antigas antes de inserir (restrição única plano+código)
        for (PlanoInspecao.Caracteristica c : novas) c.setPlano(p);
        p.getCaracteristicas().addAll(novas);
        return PlanoOut.de(planoRepo.saveAndFlush(p));
    }

    @Transactional
    public void ativar(Long id, boolean ativo) {
        PlanoInspecao p = plano(id);
        if (ativo) exigirUnicoAtivo(p.getProduto(), id);
        p.setAtivo(ativo);
    }

    private void exigirUnicoAtivo(Produto produto, Long id) {
        planoRepo.ativoDoProduto(produto.getId()).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("O produto " + produto.getNome() + " já tem o plano ativo " + o.getCodigo() + ". Inative-o antes.");
        });
    }

    PlanoInspecao plano(Long id) {
        return planoRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Plano de inspeção não encontrado: " + id));
    }
}
