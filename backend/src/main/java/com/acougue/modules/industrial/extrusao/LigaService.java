package com.acougue.modules.industrial.extrusao;

import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.extrusao.ExtrusaoDto.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/** Ligas e a conferência de composição química contra a faixa da liga. */
@Service
@RequiredArgsConstructor
public class LigaService {

    private final LigaRepository ligaRepo;

    static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    static String pct(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
    }

    /**
     * Desvios da composição em relação à liga: elemento acima do máximo, abaixo do mínimo, ou sem valor
     * para um elemento que a liga especifica. Lista vazia = dentro da especificação.
     */
    public static List<String> desvios(Liga liga, Map<String, BigDecimal> composicao) {
        List<String> d = new ArrayList<>();
        for (LigaElemento e : liga.getElementos()) {
            if (e.getMinimo() == null && e.getMaximo() == null) continue;
            BigDecimal v = composicao.get(e.getElemento());
            if (v == null) d.add(e.getElemento() + " sem valor no certificado");
            else if (e.getMinimo() != null && v.compareTo(e.getMinimo()) < 0) {
                d.add(e.getElemento() + " " + pct(v) + " abaixo do mínimo " + pct(e.getMinimo()));
            } else if (e.getMaximo() != null && v.compareTo(e.getMaximo()) > 0) {
                d.add(e.getElemento() + " " + pct(v) + " acima do máximo " + pct(e.getMaximo()));
            }
        }
        return d;
    }

    @Transactional(readOnly = true)
    public List<LigaOut> listar() {
        return ligaRepo.findAllByOrderByCodigoAsc().stream().map(LigaOut::de).toList();
    }

    @Transactional
    public LigaOut salvar(Long id, LigaIn in) {
        Liga l = id == null ? new Liga() : liga(id);
        String codigo = in.codigo().trim().toUpperCase(Locale.ROOT);
        ligaRepo.findByCodigoIgnoreCase(codigo).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("Já existe uma liga com o código " + codigo + ".");
        });
        Set<String> vistos = new HashSet<>();
        List<LigaElemento> novos = new ArrayList<>();
        for (ElementoIn e : in.elementos()) {
            if (!vistos.add(e.elemento())) throw new BusinessException("O elemento " + e.elemento() + " aparece mais de uma vez.");
            if (e.minimo() != null && e.maximo() != null && e.minimo().compareTo(e.maximo()) > 0) {
                throw new BusinessException("Elemento " + e.elemento() + ": o mínimo é maior que o máximo.");
            }
            novos.add(LigaElemento.builder().liga(l).elemento(e.elemento()).minimo(e.minimo()).maximo(e.maximo()).build());
        }
        l.setCodigo(codigo);
        l.setDescricao(in.descricao().trim());
        l.setNorma(texto(in.norma()));
        l.setDensidadeGCm3(in.densidadeGCm3());
        l.setObservacao(texto(in.observacao()));
        l.getElementos().clear();
        ligaRepo.saveAndFlush(l); // remove os elementos antigos antes de inserir (restrição única liga+elemento)
        l.getElementos().addAll(novos);
        return LigaOut.de(ligaRepo.save(l));
    }

    @Transactional
    public void ativar(Long id, boolean ativo) {
        liga(id).setAtivo(ativo);
    }

    public Liga liga(Long id) {
        return ligaRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Liga não encontrada: " + id));
    }

    public Liga ligaAtiva(Long id) {
        Liga l = liga(id);
        if (!l.getAtivo()) throw new BusinessException("A liga " + l.getCodigo() + " está inativa.");
        return l;
    }
}
