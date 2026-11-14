package com.acougue.modules.industrial.engenharia;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.engenharia.EngenhariaDto.*;
import com.acougue.repository.ProdutoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Ficha técnica industrial do produto e a lista de produtos para as telas industriais. */
@Service
@RequiredArgsConstructor
public class FichaTecnicaService {

    private final FichaTecnicaRepository fichaRepo;
    private final ProdutoRepository produtoRepo;

    static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** Produto ativo, ou 422 com o nome dele. Engenharia e PCP só trabalham com produtos ativos. */
    static Produto produtoAtivo(ProdutoRepository repo, Long id) {
        Produto p = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + id));
        if (!Boolean.TRUE.equals(p.getAtivo())) throw new BusinessException("O produto " + p.getNome() + " está inativo.");
        return p;
    }

    @Transactional(readOnly = true)
    public List<ProdutoOpcao> produtos() {
        Map<Long, String> tipos = fichaRepo.findAll().stream()
                .collect(Collectors.toMap(f -> f.getProduto().getId(), FichaTecnica::getTipoItem));
        return produtoRepo.findByAtivoTrue().stream()
                .map(p -> ProdutoOpcao.de(p, tipos.get(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FichaOut> listar() {
        return fichaRepo.listarTodas().stream().map(FichaOut::de).toList();
    }

    @Transactional
    public FichaOut salvar(Long produtoId, FichaIn in) {
        if (!FichaTecnica.TIPOS_ITEM.contains(in.tipoItem())) throw new BusinessException("Tipo de item inválido: " + in.tipoItem());
        FichaTecnica f = fichaRepo.findByProdutoId(produtoId)
                .orElseGet(() -> FichaTecnica.builder().produto(produtoAtivo(produtoRepo, produtoId)).build());
        if (in.loteMinimo() != null && in.loteMultiplo() != null
                && in.loteMinimo().remainder(in.loteMultiplo()).signum() != 0) {
            throw new BusinessException("O lote mínimo precisa ser múltiplo do lote múltiplo.");
        }
        f.setTipoItem(in.tipoItem());
        f.setCodigoDesenho(texto(in.codigoDesenho()));
        f.setRevisaoDesenho(texto(in.revisaoDesenho()));
        f.setPesoUnitarioKg(in.pesoUnitarioKg());
        f.setPesoLinearKgM(in.pesoLinearKgM());
        f.setComprimentoMm(in.comprimentoMm());
        f.setLoteMinimo(in.loteMinimo());
        f.setLoteMultiplo(in.loteMultiplo());
        f.setPrazoProducaoDias(in.prazoProducaoDias());
        f.setControlaLote(in.controlaLote() == null || in.controlaLote());
        f.setObservacao(texto(in.observacao()));
        f.setUpdatedAt(LocalDateTime.now());
        return FichaOut.de(fichaRepo.save(f));
    }
}
