package com.acougue.modules.industrial.estrutura;

import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.estrutura.EstruturaDto.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Unidades, setores, centros de trabalho e turnos: a hierarquia onde as máquinas ficam.
 * Nada é apagado: cadastros que já foram usados só são inativados.
 */
@Service
@RequiredArgsConstructor
public class EstruturaService {

    private final UnidadeRepository unidadeRepo;
    private final SetorRepository setorRepo;
    private final CentroTrabalhoRepository centroRepo;
    private final TurnoRepository turnoRepo;
    private final MaquinaRepository maquinaRepo;

    /** Códigos são guardados sem espaços nas pontas e em maiúsculas: "ext-01" e "EXT-01" são o mesmo. */
    static String codigo(String valor) {
        return valor == null ? null : valor.trim().toUpperCase(Locale.ROOT);
    }

    static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    // ── Unidades ─────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<UnidadeOut> listarUnidades() {
        return unidadeRepo.findAllByOrderByCodigoAsc().stream().map(UnidadeOut::de).toList();
    }

    @Transactional
    public UnidadeOut salvarUnidade(Long id, UnidadeIn in, Long usuarioId) {
        Unidade u = id == null ? Unidade.builder().criadoPorId(usuarioId).build() : unidade(id);
        String cod = codigo(in.codigo());
        unidadeRepo.findByCodigoIgnoreCase(cod).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("Já existe uma unidade com o código " + cod + ".");
        });
        u.setCodigo(cod);
        u.setNome(in.nome().trim());
        u.setCidade(texto(in.cidade()));
        u.setUf(in.uf() == null || in.uf().isBlank() ? null : in.uf().trim().toUpperCase(Locale.ROOT));
        return UnidadeOut.de(unidadeRepo.save(u));
    }

    @Transactional
    public void ativarUnidade(Long id, boolean ativo) {
        Unidade u = unidade(id);
        if (!ativo && setorRepo.listarTodos().stream()
                .anyMatch(s -> s.getUnidade().getId().equals(id) && s.getAtivo())) {
            throw new BusinessException("Inative antes os setores desta unidade.");
        }
        u.setAtivo(ativo);
    }

    // ── Setores ──────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<SetorOut> listarSetores() {
        return setorRepo.listarTodos().stream().map(SetorOut::de).toList();
    }

    @Transactional
    public SetorOut salvarSetor(Long id, SetorIn in) {
        Setor s = id == null ? new Setor() : setor(id);
        Unidade u = unidade(in.unidadeId());
        if (!u.getAtivo()) throw new BusinessException("A unidade " + u.getNome() + " está inativa.");
        String cod = codigo(in.codigo());
        setorRepo.findByUnidadeIdAndCodigoIgnoreCase(u.getId(), cod).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("Já existe um setor com o código " + cod + " nesta unidade.");
        });
        s.setUnidade(u);
        s.setCodigo(cod);
        s.setNome(in.nome().trim());
        if (s.getAtivo() == null) s.setAtivo(true);
        return SetorOut.de(setorRepo.save(s));
    }

    @Transactional
    public void ativarSetor(Long id, boolean ativo) {
        Setor s = setor(id);
        if (ativo && !s.getUnidade().getAtivo()) {
            throw new BusinessException("Reative antes a unidade " + s.getUnidade().getNome() + ".");
        }
        if (!ativo && centroRepo.listarTodos().stream()
                .anyMatch(c -> c.getSetor().getId().equals(id) && c.getAtivo())) {
            throw new BusinessException("Inative antes os centros de trabalho deste setor.");
        }
        s.setAtivo(ativo);
    }

    // ── Centros de trabalho ──────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<CentroOut> listarCentros() {
        return centroRepo.listarTodos().stream().map(CentroOut::de).toList();
    }

    @Transactional
    public CentroOut salvarCentro(Long id, CentroIn in) {
        CentroTrabalho c = id == null ? new CentroTrabalho() : centro(id);
        Setor s = setor(in.setorId());
        if (!s.getAtivo()) throw new BusinessException("O setor " + s.getNome() + " está inativo.");
        String cod = codigo(in.codigo());
        centroRepo.findByCodigoIgnoreCase(cod).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("Já existe um centro de trabalho com o código " + cod + ".");
        });
        c.setSetor(s);
        c.setCodigo(cod);
        c.setNome(in.nome().trim());
        c.setCustoHora(in.custoHora());
        if (c.getAtivo() == null) c.setAtivo(true);
        return CentroOut.de(centroRepo.save(c));
    }

    @Transactional
    public void ativarCentro(Long id, boolean ativo) {
        CentroTrabalho c = centro(id);
        if (ativo && !c.getSetor().getAtivo()) {
            throw new BusinessException("Reative antes o setor " + c.getSetor().getNome() + ".");
        }
        if (!ativo && maquinaRepo.countByCentroTrabalhoIdAndStatusNot(id, "INATIVA") > 0) {
            throw new BusinessException("Este centro tem máquinas ativas: mova ou inative as máquinas antes.");
        }
        c.setAtivo(ativo);
    }

    // ── Turnos ───────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<TurnoOut> listarTurnos() {
        return turnoRepo.findAllByOrderByHoraInicioAsc().stream().map(TurnoOut::de).toList();
    }

    @Transactional
    public TurnoOut salvarTurno(Long id, TurnoIn in) {
        Turno t = id == null ? new Turno() : turno(id);
        if (in.horaInicio().equals(in.horaFim())) {
            throw new BusinessException("O turno precisa terminar em um horário diferente do início.");
        }
        String dias = in.diasSemana() == null || in.diasSemana().isBlank() ? "1111100" : in.diasSemana();
        if (!dias.contains("1")) throw new BusinessException("Marque pelo menos um dia da semana.");
        String cod = codigo(in.codigo());
        turnoRepo.findByCodigoIgnoreCase(cod).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("Já existe um turno com o código " + cod + ".");
        });
        t.setCodigo(cod);
        t.setNome(in.nome().trim());
        t.setHoraInicio(in.horaInicio());
        t.setHoraFim(in.horaFim());
        t.setDiasSemana(dias);
        if (t.getAtivo() == null) t.setAtivo(true);
        return TurnoOut.de(turnoRepo.save(t));
    }

    @Transactional
    public void ativarTurno(Long id, boolean ativo) {
        turno(id).setAtivo(ativo);
    }

    // ── Busca com 404 ────────────────────────────────────────────────────
    Unidade unidade(Long id) {
        return unidadeRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada: " + id));
    }

    Setor setor(Long id) {
        return setorRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Setor não encontrado: " + id));
    }

    CentroTrabalho centro(Long id) {
        return centroRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Centro de trabalho não encontrado: " + id));
    }

    Turno turno(Long id) {
        return turnoRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Turno não encontrado: " + id));
    }
}
