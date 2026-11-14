package com.acougue.modules.industrial.estrutura;

import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.estrutura.EstruturaDto.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static com.acougue.modules.industrial.estrutura.EstruturaService.codigo;
import static com.acougue.modules.industrial.estrutura.EstruturaService.texto;

/**
 * Ferramentas, matrizes e dispositivos. Uma revisão nova da matriz é um cadastro novo: as ordens
 * de produção guardam a revisão que usaram. Ferramenta descartada não volta.
 */
@Service
@RequiredArgsConstructor
public class FerramentaService {

    private final FerramentaRepository ferramentaRepo;
    private final TipoMaquinaRepository tipoRepo;

    @Transactional(readOnly = true)
    public List<FerramentaOut> listar() {
        return ferramentaRepo.listarTodas().stream().map(FerramentaService::paraDto).toList();
    }

    @Transactional
    public FerramentaOut salvar(Long id, FerramentaIn in) {
        Ferramenta f = id == null ? new Ferramenta() : ferramenta(id);
        if ("DESCARTADA".equals(f.getStatus())) throw new BusinessException("Ferramenta descartada não pode ser alterada.");
        if (!Ferramenta.TIPOS.contains(in.tipo())) throw new BusinessException("Tipo de ferramenta inválido: " + in.tipo());
        String cod = codigo(in.codigo());
        String revisao = texto(in.revisao()) == null ? null : codigo(in.revisao());
        ferramentaRepo.buscarPorCodigoERevisao(cod, revisao).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("Já existe a ferramenta " + cod + (revisao == null ? "" : " revisão " + revisao) + ".");
        });
        int ciclos = in.ciclosUtilizados() == null ? (f.getCiclosUtilizados() == null ? 0 : f.getCiclosUtilizados())
                : in.ciclosUtilizados();
        if (id != null && ciclos < f.getCiclosUtilizados()) {
            throw new BusinessException("Os ciclos utilizados não podem diminuir (hoje: " + f.getCiclosUtilizados() + ").");
        }
        f.setCodigo(cod);
        f.setRevisao(revisao);
        f.setDescricao(in.descricao().trim());
        f.setTipo(in.tipo());
        f.setTipoMaquina(in.tipoMaquinaId() == null ? null : tipoRepo.findById(in.tipoMaquinaId())
                .orElseThrow(() -> new EntityNotFoundException("Tipo de máquina não encontrado: " + in.tipoMaquinaId())));
        f.setVidaUtilCiclos(in.vidaUtilCiclos());
        f.setCiclosUtilizados(ciclos);
        f.setCustoAquisicao(in.custoAquisicao());
        f.setObservacao(texto(in.observacao()));
        if (f.getStatus() == null) f.setStatus("DISPONIVEL");
        return paraDto(ferramentaRepo.save(f));
    }

    @Transactional
    public FerramentaOut mudarStatus(Long id, String status) {
        Ferramenta f = ferramenta(id);
        if (!Ferramenta.STATUS.contains(status)) throw new BusinessException("Status de ferramenta inválido: " + status);
        if ("DESCARTADA".equals(f.getStatus())) throw new BusinessException("Ferramenta descartada não volta a ser usada.");
        f.setStatus(status);
        return paraDto(f);
    }

    static FerramentaOut paraDto(Ferramenta f) {
        BigDecimal vida = f.getVidaUtilCiclos() == null ? null
                : BigDecimal.valueOf(f.getCiclosUtilizados() * 100L)
                    .divide(BigDecimal.valueOf(f.getVidaUtilCiclos()), 1, RoundingMode.HALF_UP);
        return new FerramentaOut(f.getId(), f.getCodigo(), f.getDescricao(), f.getTipo(), f.getRevisao(),
                f.getTipoMaquina() == null ? null : f.getTipoMaquina().getId(),
                f.getTipoMaquina() == null ? null : f.getTipoMaquina().getNome(),
                f.getVidaUtilCiclos(), f.getCiclosUtilizados(), vida, f.getStatus(), f.getCustoAquisicao(),
                f.getObservacao());
    }

    private Ferramenta ferramenta(Long id) {
        return ferramentaRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Ferramenta não encontrada: " + id));
    }
}
