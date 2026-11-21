package com.acougue.modules.industrial.execucao;

import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.estrutura.Maquina;
import com.acougue.modules.industrial.estrutura.MaquinaRepository;
import com.acougue.modules.industrial.estrutura.MaquinaService;
import com.acougue.modules.industrial.execucao.ExecucaoDto.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Motivos de parada e paradas avulsas (máquina parada sem apontamento aberto). As pausas de apontamento
 * também são paradas, mas abertas e fechadas pelo {@link ApontamentoService}.
 */
@Service
@RequiredArgsConstructor
public class ParadaService {

    static final int DIAS_MAXIMOS_CONSULTA = 92;

    private final MotivoParadaRepository motivoRepo;
    private final ParadaRepository paradaRepo;
    private final ApontamentoRepository apontamentoRepo;
    private final MaquinaRepository maquinaRepo;
    private final MaquinaService maquinaService;
    private final ExecucaoMapper mapper;

    // ── Motivos ──────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<MotivoOut> motivos() {
        return motivoRepo.findAllByOrderByCodigoAsc().stream().map(MotivoOut::de).toList();
    }

    @Transactional
    public MotivoOut salvarMotivo(Long id, MotivoIn in) {
        if (!MotivoParada.CATEGORIAS.contains(in.categoria())) throw new BusinessException("Categoria inválida: " + in.categoria());
        MotivoParada m = id == null ? new MotivoParada() : motivoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Motivo de parada não encontrado: " + id));
        String codigo = in.codigo().trim().toUpperCase(Locale.ROOT);
        motivoRepo.findByCodigoIgnoreCase(codigo).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("Já existe um motivo de parada com o código " + codigo + ".");
        });
        m.setCodigo(codigo);
        m.setDescricao(in.descricao().trim());
        m.setCategoria(in.categoria());
        m.setPlanejada(Boolean.TRUE.equals(in.planejada()));
        return MotivoOut.de(motivoRepo.save(m));
    }

    @Transactional
    public void ativarMotivo(Long id, boolean ativo) {
        motivoRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Motivo de parada não encontrado: " + id))
                .setAtivo(ativo);
    }

    MotivoParada motivoAtivo(Long id) {
        MotivoParada m = motivoRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Motivo de parada não encontrado: " + id));
        if (!m.getAtivo()) throw new BusinessException("O motivo " + m.getDescricao() + " está inativo.");
        return m;
    }

    // ── Paradas avulsas ──────────────────────────────────────────────────
    @Transactional
    public ParadaOut parar(ParadaIn in, Long usuarioId) {
        var repetida = paradaRepo.findByChave(in.chave());
        if (repetida.isPresent()) return mapper.parada(repetida.get(), mapper.nomes(List.of(usuarioId)));

        Maquina m = maquinaRepo.findById(in.maquinaId())
                .orElseThrow(() -> new EntityNotFoundException("Máquina não encontrada: " + in.maquinaId()));
        if ("INATIVA".equals(m.getStatus())) throw new BusinessException("A máquina " + m.getCodigo() + " está inativa.");
        if ("EM_MANUTENCAO".equals(m.getStatus())) throw new BusinessException("A máquina " + m.getCodigo() + " já está em manutenção.");
        if (apontamentoRepo.abertoDaMaquina(m.getId()).isPresent()) {
            throw new BusinessException("A máquina " + m.getCodigo() + " tem apontamento aberto. Use \"Pausar\" no apontamento.");
        }
        paradaRepo.abertaDaMaquina(m.getId()).ifPresent(p -> {
            throw new BusinessException("A máquina " + m.getCodigo() + " já está parada desde " + p.getInicio().toLocalTime().withNano(0) + ".");
        });
        MotivoParada motivo = motivoAtivo(in.motivoId());
        Parada p = paradaRepo.saveAndFlush(Parada.builder().chave(in.chave()).maquina(m).motivo(motivo)
                .inicio(LocalDateTime.now()).observacao(texto(in.observacao())).usuarioId(usuarioId).build());
        maquinaService.statusPelaProducao(m, "PARADA", "Parada: " + motivo.getDescricao(), usuarioId);
        return mapper.parada(p, mapper.nomes(List.of(usuarioId)));
    }

    @Transactional
    public ParadaOut encerrar(Long id, Long usuarioId) {
        Parada p = paradaRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Parada não encontrada: " + id));
        if (p.getApontamento() != null) {
            throw new BusinessException("Esta parada é uma pausa de apontamento. Use \"Retomar\" no apontamento.");
        }
        if (p.getFim() == null) {
            p.setFim(LocalDateTime.now());
            p.setEncerradaPorId(usuarioId);
            if (p.getMaquina() != null && "PARADA".equals(p.getMaquina().getStatus())) {
                maquinaService.statusPelaProducao(p.getMaquina(), "DISPONIVEL", "Fim da parada: " + p.getMotivo().getDescricao(), usuarioId);
            }
        }
        return mapper.parada(paradaRepo.save(p), mapper.nomes(List.of(p.getUsuarioId())));
    }

    @Transactional(readOnly = true)
    public List<ParadaOut> listar(LocalDate de, LocalDate ate, Long maquinaId) {
        if (ate.isBefore(de)) throw new BusinessException("O fim do período não pode ser antes do início.");
        if (ChronoUnit.DAYS.between(de, ate) + 1 > DIAS_MAXIMOS_CONSULTA) {
            throw new BusinessException("Consulte no máximo " + DIAS_MAXIMOS_CONSULTA + " dias por vez.");
        }
        List<Parada> paradas = paradaRepo.noPeriodo(de.atStartOfDay(), ate.plusDays(1).atStartOfDay(), maquinaId);
        Map<Long, String> nomes = mapper.nomes(paradas.stream().map(Parada::getUsuarioId).toList());
        return paradas.stream().map(p -> mapper.parada(p, nomes)).toList();
    }

    static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
