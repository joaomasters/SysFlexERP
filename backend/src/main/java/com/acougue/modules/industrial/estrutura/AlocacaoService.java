package com.acougue.modules.industrial.estrutura;

import com.acougue.entity.Usuario;
import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.estrutura.EstruturaDto.*;
import com.acougue.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.acougue.modules.industrial.estrutura.EstruturaService.texto;

/**
 * Alocação de operadores (usuários do sistema) a centros de trabalho por turno. O mesmo operador
 * não pode estar em dois centros no mesmo turno em períodos que se sobrepõem.
 * Alocação não é apagada: é encerrada com uma data final, para o histórico de quem trabalhou onde.
 */
@Service
@RequiredArgsConstructor
public class AlocacaoService {

    private final AlocacaoOperadorRepository alocacaoRepo;
    private final CentroTrabalhoRepository centroRepo;
    private final TurnoRepository turnoRepo;
    private final UsuarioRepository usuarioRepo;

    @Transactional(readOnly = true)
    public List<AlocacaoOut> listar() {
        List<AlocacaoOperador> todas = alocacaoRepo.listarTodas();
        Map<Long, String> nomes = usuarioRepo.findAllById(todas.stream().map(AlocacaoOperador::getUsuarioId).toList())
                .stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNome));
        LocalDate hoje = LocalDate.now();
        return todas.stream().map(a -> paraDto(a, nomes.get(a.getUsuarioId()), hoje)).toList();
    }

    /** Usuários ativos que podem ser alocados como operadores. */
    @Transactional(readOnly = true)
    public List<OperadorOut> operadores() {
        return usuarioRepo.findAll().stream().filter(u -> Boolean.TRUE.equals(u.getAtivo()))
                .sorted(Comparator.comparing(Usuario::getNome, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new OperadorOut(u.getId(), u.getNome())).toList();
    }

    @Transactional
    public AlocacaoOut criar(AlocacaoIn in) {
        Usuario u = usuarioRepo.findById(in.usuarioId())
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + in.usuarioId()));
        if (!Boolean.TRUE.equals(u.getAtivo())) throw new BusinessException("O usuário " + u.getNome() + " está inativo.");
        CentroTrabalho c = centroRepo.findById(in.centroTrabalhoId())
                .orElseThrow(() -> new EntityNotFoundException("Centro de trabalho não encontrado: " + in.centroTrabalhoId()));
        if (!c.getAtivo()) throw new BusinessException("O centro de trabalho " + c.getNome() + " está inativo.");
        Turno t = turnoRepo.findById(in.turnoId())
                .orElseThrow(() -> new EntityNotFoundException("Turno não encontrado: " + in.turnoId()));
        if (!t.getAtivo()) throw new BusinessException("O turno " + t.getNome() + " está inativo.");
        if (in.vigenteAte() != null && in.vigenteAte().isBefore(in.vigenteDe())) {
            throw new BusinessException("O fim da alocação é anterior ao início.");
        }
        alocacaoRepo.findByUsuarioIdAndTurnoId(u.getId(), t.getId()).stream()
                .filter(a -> sobrepoe(a.getVigenteDe(), a.getVigenteAte(), in.vigenteDe(), in.vigenteAte()))
                .findFirst().ifPresent(a -> {
                    throw new BusinessException(u.getNome() + " já está alocado(a) no turno " + t.getNome()
                            + " em " + a.getCentroTrabalho().getNome() + " neste período. Encerre a alocação anterior antes.");
                });
        AlocacaoOperador a = alocacaoRepo.save(AlocacaoOperador.builder().usuarioId(u.getId()).centroTrabalho(c)
                .turno(t).funcao(texto(in.funcao())).vigenteDe(in.vigenteDe()).vigenteAte(in.vigenteAte()).build());
        return paraDto(a, u.getNome(), LocalDate.now());
    }

    @Transactional
    public AlocacaoOut encerrar(Long id, LocalDate vigenteAte) {
        AlocacaoOperador a = alocacaoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Alocação não encontrada: " + id));
        if (vigenteAte.isBefore(a.getVigenteDe())) throw new BusinessException("O fim é anterior ao início da alocação.");
        if (a.getVigenteAte() != null && vigenteAte.isAfter(a.getVigenteAte())) {
            throw new BusinessException("Para estender a alocação, crie uma nova a partir do dia seguinte ao fim.");
        }
        a.setVigenteAte(vigenteAte);
        String nome = usuarioRepo.findById(a.getUsuarioId()).map(Usuario::getNome).orElse(null);
        return paraDto(a, nome, LocalDate.now());
    }

    /** Períodos [de, ate] que se encontram em pelo menos um dia; ate nulo = sem fim. */
    static boolean sobrepoe(LocalDate de1, LocalDate ate1, LocalDate de2, LocalDate ate2) {
        boolean primeiroComecaAntesDoFimDoSegundo = ate2 == null || !de1.isAfter(ate2);
        boolean segundoComecaAntesDoFimDoPrimeiro = ate1 == null || !de2.isAfter(ate1);
        return primeiroComecaAntesDoFimDoSegundo && segundoComecaAntesDoFimDoPrimeiro;
    }

    private static AlocacaoOut paraDto(AlocacaoOperador a, String nome, LocalDate hoje) {
        boolean vigente = !a.getVigenteDe().isAfter(hoje) && (a.getVigenteAte() == null || !a.getVigenteAte().isBefore(hoje));
        return new AlocacaoOut(a.getId(), a.getUsuarioId(), nome, a.getCentroTrabalho().getId(),
                a.getCentroTrabalho().getNome(), a.getTurno().getId(), a.getTurno().getNome(), a.getFuncao(),
                a.getVigenteDe(), a.getVigenteAte(), vigente);
    }
}
