package com.acougue.modules.industrial.estrutura;

import com.acougue.entity.Usuario;
import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.estrutura.EstruturaDto.*;
import com.acougue.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.acougue.modules.industrial.estrutura.EstruturaService.codigo;
import static com.acougue.modules.industrial.estrutura.EstruturaService.texto;

/**
 * Tipos de máquina (com seus parâmetros técnicos), máquinas, status e histórico.
 *
 * Regras de status:
 * - mudar para o mesmo status é recusado;
 * - INATIVA só volta para DISPONIVEL;
 * - EM_OPERACAO só a partir de DISPONIVEL ou PARADA (máquina em manutenção não opera).
 * Toda mudança de status, horímetro ou manutenção grava um evento no histórico.
 */
@Service
@RequiredArgsConstructor
public class MaquinaService {

    private final TipoMaquinaRepository tipoRepo;
    private final MaquinaRepository maquinaRepo;
    private final MaquinaParametroRepository maquinaParametroRepo;
    private final MaquinaEventoRepository eventoRepo;
    private final CentroTrabalhoRepository centroRepo;
    private final TurnoRepository turnoRepo;
    private final UsuarioRepository usuarioRepo;

    // ── Tipos de máquina ─────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<TipoMaquinaOut> listarTipos() {
        return tipoRepo.listarTodos().stream().map(TipoMaquinaOut::de).toList();
    }

    @Transactional
    public TipoMaquinaOut salvarTipo(Long id, TipoMaquinaIn in) {
        TipoMaquina t = id == null ? new TipoMaquina() : tipo(id);
        if (!TipoMaquina.CATEGORIAS.contains(in.categoria())) {
            throw new BusinessException("Categoria de máquina inválida: " + in.categoria());
        }
        String cod = codigo(in.codigo());
        tipoRepo.findByCodigoIgnoreCase(cod).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("Já existe um tipo de máquina com o código " + cod + ".");
        });
        t.setCodigo(cod);
        t.setNome(in.nome().trim());
        t.setCategoria(in.categoria());
        if (t.getAtivo() == null) t.setAtivo(true);
        if (t.getParametros() == null) t.setParametros(new ArrayList<>());
        sincronizarParametros(t, in.parametros() == null ? List.of() : in.parametros());
        return TipoMaquinaOut.de(tipoRepo.save(t));
    }

    /** Atualiza, inclui e remove os parâmetros do tipo. Parâmetro já usado por alguma máquina não sai. */
    private void sincronizarParametros(TipoMaquina t, List<ParametroIn> entrada) {
        Set<String> codigos = new HashSet<>();
        for (ParametroIn p : entrada) {
            if (!codigos.add(codigo(p.codigo()))) {
                throw new BusinessException("O parâmetro " + codigo(p.codigo()) + " aparece duas vezes.");
            }
            String tipoValor = p.tipoValor() == null ? "NUMERO" : p.tipoValor();
            if (!List.of("NUMERO", "TEXTO").contains(tipoValor)) {
                throw new BusinessException("Tipo de valor inválido no parâmetro " + p.nome() + ".");
            }
            if (p.minimo() != null && p.maximo() != null && p.minimo().compareTo(p.maximo()) > 0) {
                throw new BusinessException("No parâmetro " + p.nome() + ", o mínimo é maior que o máximo.");
            }
        }
        Set<Long> mantidos = entrada.stream().map(ParametroIn::id).filter(Objects::nonNull).collect(Collectors.toSet());
        Iterator<TipoMaquinaParametro> it = t.getParametros().iterator();
        while (it.hasNext()) {
            TipoMaquinaParametro atual = it.next();
            if (!mantidos.contains(atual.getId())) {
                if (atual.getId() != null && maquinaParametroRepo.existsByParametroId(atual.getId())) {
                    throw new BusinessException("O parâmetro " + atual.getNome()
                            + " já tem valor em alguma máquina e não pode ser removido.");
                }
                it.remove();
            }
        }
        Map<Long, TipoMaquinaParametro> porId = t.getParametros().stream()
                .collect(Collectors.toMap(TipoMaquinaParametro::getId, Function.identity()));
        short ordem = 0;
        for (ParametroIn p : entrada) {
            TipoMaquinaParametro alvo;
            if (p.id() != null) {
                alvo = porId.get(p.id());
                if (alvo == null) throw new BusinessException("Parâmetro " + p.id() + " não pertence a este tipo.");
            } else {
                alvo = TipoMaquinaParametro.builder().tipoMaquina(t).build();
                t.getParametros().add(alvo);
            }
            alvo.setCodigo(codigo(p.codigo()));
            alvo.setNome(p.nome().trim());
            alvo.setUnidade(texto(p.unidade()));
            alvo.setTipoValor(p.tipoValor() == null ? "NUMERO" : p.tipoValor());
            alvo.setMinimo(alvo.numerico() ? p.minimo() : null);
            alvo.setMaximo(alvo.numerico() ? p.maximo() : null);
            alvo.setObrigatorio(Boolean.TRUE.equals(p.obrigatorio()));
            alvo.setOrdem(ordem++);
        }
    }

    @Transactional
    public void ativarTipo(Long id, boolean ativo) {
        tipo(id).setAtivo(ativo);
    }

    // ── Máquinas ─────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<MaquinaOut> listarMaquinas() {
        return maquinaRepo.listarTodas().stream().map(this::paraDto).toList();
    }

    @Transactional(readOnly = true)
    public MaquinaOut buscarMaquina(Long id) {
        return paraDto(maquina(id));
    }

    @Transactional
    public MaquinaOut salvarMaquina(Long id, MaquinaIn in, Long usuarioId) {
        Maquina m = id == null ? Maquina.builder().criadoPorId(usuarioId).build() : maquina(id);
        if (id != null) conferirVersao(m, in.versao());

        TipoMaquina tipo = tipo(in.tipoMaquinaId());
        CentroTrabalho centro = centroRepo.findById(in.centroTrabalhoId())
                .orElseThrow(() -> new EntityNotFoundException("Centro de trabalho não encontrado: " + in.centroTrabalhoId()));
        if (!tipo.getAtivo() && (m.getTipoMaquina() == null || !m.getTipoMaquina().getId().equals(tipo.getId()))) {
            throw new BusinessException("O tipo de máquina " + tipo.getNome() + " está inativo.");
        }
        if (!centro.getAtivo()) throw new BusinessException("O centro de trabalho " + centro.getNome() + " está inativo.");
        if ((in.capacidade() == null) != (in.unidadeCapacidade() == null || in.unidadeCapacidade().isBlank())) {
            throw new BusinessException("Informe a capacidade junto com a unidade dela (ex.: 1.500 kg/h).");
        }
        if (in.unidadeCapacidade() != null && !in.unidadeCapacidade().isBlank()
                && !Maquina.UNIDADES_CAPACIDADE.contains(in.unidadeCapacidade())) {
            throw new BusinessException("Unidade de capacidade inválida: " + in.unidadeCapacidade());
        }
        String cod = codigo(in.codigo());
        maquinaRepo.findByCodigoIgnoreCase(cod).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("Já existe uma máquina com o código " + cod + ".");
        });

        m.setCodigo(cod);
        m.setDescricao(in.descricao().trim());
        m.setTipoMaquina(tipo);
        m.setCentroTrabalho(centro);
        m.setCapacidade(in.capacidade());
        m.setUnidadeCapacidade(in.capacidade() == null ? null : in.unidadeCapacidade());
        m.setCustoHora(in.custoHora());
        m.setFabricante(texto(in.fabricante()));
        m.setModelo(texto(in.modelo()));
        m.setNumeroSerie(texto(in.numeroSerie()));
        m.setAnoFabricacao(in.anoFabricacao());
        m.setObservacao(texto(in.observacao()));
        aplicarParametros(m, tipo, in.parametros() == null ? List.of() : in.parametros());
        aplicarTurnos(m, in.turnoIds() == null ? List.of() : in.turnoIds());
        return paraDto(maquinaRepo.saveAndFlush(m));
    }

    /** Os valores precisam ser parâmetros do tipo da máquina e respeitar a faixa e a obrigatoriedade. */
    static void aplicarParametros(Maquina m, TipoMaquina tipo, List<ValorParametroIn> valores) {
        Map<Long, TipoMaquinaParametro> doTipo = tipo.getParametros().stream()
                .collect(Collectors.toMap(TipoMaquinaParametro::getId, Function.identity()));
        Map<Long, ValorParametroIn> informados = new LinkedHashMap<>();
        for (ValorParametroIn v : valores) {
            TipoMaquinaParametro p = doTipo.get(v.parametroId());
            if (p == null) {
                throw new BusinessException("O parâmetro " + v.parametroId() + " não é do tipo " + tipo.getNome() + ".");
            }
            boolean vazio = p.numerico() ? v.valorNumero() == null : v.valorTexto() == null || v.valorTexto().isBlank();
            if (vazio) continue;
            if (p.numerico()) {
                BigDecimal n = v.valorNumero();
                if (p.getMinimo() != null && n.compareTo(p.getMinimo()) < 0
                        || p.getMaximo() != null && n.compareTo(p.getMaximo()) > 0) {
                    throw new BusinessException(String.format("%s fora da faixa permitida para %s (%s a %s%s).",
                            p.getNome(), tipo.getNome(), numero(p.getMinimo()), numero(p.getMaximo()),
                            p.getUnidade() == null ? "" : " " + p.getUnidade()));
                }
            }
            informados.put(p.getId(), v);
        }
        for (TipoMaquinaParametro p : tipo.getParametros()) {
            if (p.getObrigatorio() && !informados.containsKey(p.getId())) {
                throw new BusinessException("Informe o parâmetro obrigatório " + p.getNome() + ".");
            }
        }
        // Troca o conjunto: os que sumiram saem, os que ficaram são atualizados
        m.getParametros().removeIf(mp -> !informados.containsKey(mp.getParametro().getId())
                || !mp.getParametro().getTipoMaquina().getId().equals(tipo.getId()));
        Map<Long, MaquinaParametro> atuais = m.getParametros().stream()
                .collect(Collectors.toMap(mp -> mp.getParametro().getId(), Function.identity()));
        informados.forEach((parametroId, v) -> {
            TipoMaquinaParametro p = doTipo.get(parametroId);
            MaquinaParametro mp = atuais.get(parametroId);
            if (mp == null) {
                mp = MaquinaParametro.builder().maquina(m).parametro(p).build();
                m.getParametros().add(mp);
            }
            mp.setValorNumero(p.numerico() ? v.valorNumero() : null);
            mp.setValorTexto(p.numerico() ? null : v.valorTexto().trim());
        });
    }

    private static String numero(BigDecimal n) {
        return n == null ? "—" : n.stripTrailingZeros().toPlainString();
    }

    private void aplicarTurnos(Maquina m, List<Long> turnoIds) {
        Set<Long> ids = new HashSet<>(turnoIds);
        List<Turno> turnos = turnoRepo.findAllById(ids);
        if (turnos.size() != ids.size()) throw new BusinessException("Algum turno informado não existe.");
        turnos.stream().filter(t -> !t.getAtivo()).findFirst().ifPresent(t -> {
            throw new BusinessException("O turno " + t.getNome() + " está inativo.");
        });
        m.getTurnos().clear();
        m.getTurnos().addAll(turnos);
    }

    @Transactional
    public MaquinaOut mudarStatus(Long id, MudarStatusIn in, Long usuarioId) {
        Maquina m = maquina(id);
        conferirVersao(m, in.versao());
        String novo = in.status();
        validarTransicao(m.getStatus(), novo);
        String anterior = m.getStatus();
        m.setStatus(novo);
        eventoRepo.save(MaquinaEvento.builder().maquina(m).tipo("STATUS").statusAnterior(anterior).statusNovo(novo)
                .horimetro(m.getHorimetro())
                .descricao(texto(in.motivo()) != null ? in.motivo().trim() : "Status alterado")
                .usuarioId(usuarioId).build());
        return paraDto(maquinaRepo.saveAndFlush(m));
    }

    /**
     * Mudança de status causada pela produção (início, pausa e fim de apontamento, parada avulsa).
     * Não exige a versão da tela, mas grava o evento no histórico como qualquer mudança. Se a máquina
     * já estiver no status pedido, não faz nada.
     */
    @Transactional
    public void statusPelaProducao(Maquina m, String novo, String descricao, Long usuarioId) {
        if (novo.equals(m.getStatus())) return;
        String anterior = m.getStatus();
        m.setStatus(novo);
        eventoRepo.save(MaquinaEvento.builder().maquina(m).tipo("STATUS").statusAnterior(anterior).statusNovo(novo)
                .horimetro(m.getHorimetro()).descricao(descricao).usuarioId(usuarioId).build());
        maquinaRepo.save(m);
    }

    static void validarTransicao(String atual, String novo) {
        if (!Maquina.STATUS.contains(novo)) throw new BusinessException("Status de máquina inválido: " + novo);
        if (novo.equals(atual)) throw new BusinessException("A máquina já está com este status.");
        if ("INATIVA".equals(atual) && !"DISPONIVEL".equals(novo)) {
            throw new BusinessException("Máquina inativa só pode voltar para Disponível.");
        }
        if ("EM_OPERACAO".equals(novo) && !List.of("DISPONIVEL", "PARADA").contains(atual)) {
            throw new BusinessException("Só máquina disponível ou parada pode entrar em operação.");
        }
    }

    @Transactional
    public MaquinaOut registrarHorimetro(Long id, HorimetroIn in, Long usuarioId) {
        Maquina m = maquina(id);
        conferirVersao(m, in.versao());
        if (in.valor().compareTo(m.getHorimetro()) < 0) {
            throw new BusinessException("O horímetro não pode voltar: a leitura atual é " + numero(m.getHorimetro()) + " h.");
        }
        m.setHorimetro(in.valor());
        eventoRepo.save(MaquinaEvento.builder().maquina(m).tipo("HORIMETRO").horimetro(in.valor())
                .descricao("Leitura do horímetro").usuarioId(usuarioId).build());
        return paraDto(maquinaRepo.saveAndFlush(m));
    }

    @Transactional
    public EventoOut registrarManutencao(Long id, ManutencaoIn in, Long usuarioId) {
        Maquina m = maquina(id);
        String tipo = switch (in.tipo()) {
            case "PREVENTIVA" -> "MANUTENCAO_PREVENTIVA";
            case "CORRETIVA" -> "MANUTENCAO_CORRETIVA";
            default -> throw new BusinessException("Tipo de manutenção inválido: use PREVENTIVA ou CORRETIVA.");
        };
        if (in.inicio() != null && in.fim() != null && in.fim().isBefore(in.inicio())) {
            throw new BusinessException("O fim da manutenção é anterior ao início.");
        }
        MaquinaEvento e = eventoRepo.save(MaquinaEvento.builder().maquina(m).tipo(tipo).horimetro(m.getHorimetro())
                .inicio(in.inicio()).fim(in.fim()).custo(in.custo()).descricao(in.descricao().trim())
                .usuarioId(usuarioId).build());
        return paraEvento(e, nomes(List.of(e)));
    }

    @Transactional(readOnly = true)
    public List<EventoOut> historico(Long id) {
        maquina(id);
        List<MaquinaEvento> eventos = eventoRepo.findByMaquinaIdOrderByCreatedAtDescIdDesc(id);
        Map<Long, String> nomes = nomes(eventos);
        return eventos.stream().map(e -> paraEvento(e, nomes)).toList();
    }

    private Map<Long, String> nomes(List<MaquinaEvento> eventos) {
        Set<Long> ids = eventos.stream().map(MaquinaEvento::getUsuarioId).filter(Objects::nonNull).collect(Collectors.toSet());
        return usuarioRepo.findAllById(ids).stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNome));
    }

    private static EventoOut paraEvento(MaquinaEvento e, Map<Long, String> nomes) {
        return new EventoOut(e.getId(), e.getTipo(), e.getStatusAnterior(), e.getStatusNovo(), e.getHorimetro(),
                e.getCusto(), e.getInicio(), e.getFim(), e.getDescricao(),
                e.getUsuarioId() == null ? null : nomes.get(e.getUsuarioId()), e.getCreatedAt());
    }

    /** A tela manda a versão que carregou; se outra pessoa salvou no meio, recusa em vez de sobrescrever. */
    static void conferirVersao(Maquina m, Long versaoDaTela) {
        if (versaoDaTela == null || !versaoDaTela.equals(m.getVersao())) {
            throw new BusinessException("Esta máquina foi alterada por outra pessoa. Recarregue a tela e tente de novo.");
        }
    }

    private MaquinaOut paraDto(Maquina m) {
        CentroTrabalho c = m.getCentroTrabalho();
        List<ValorParametroOut> parametros = m.getParametros().stream()
                .sorted(Comparator.comparing(mp -> mp.getParametro().getOrdem()))
                .map(mp -> new ValorParametroOut(mp.getParametro().getId(), mp.getParametro().getCodigo(),
                        mp.getParametro().getNome(), mp.getParametro().getUnidade(), mp.getValorNumero(), mp.getValorTexto()))
                .toList();
        return new MaquinaOut(m.getId(), m.getCodigo(), m.getDescricao(), m.getTipoMaquina().getId(),
                m.getTipoMaquina().getNome(), m.getTipoMaquina().getCategoria(), c.getId(), c.getNome(),
                c.getSetor().getNome(), c.getSetor().getUnidade().getNome(), m.getCapacidade(), m.getUnidadeCapacidade(),
                m.getStatus(), m.getCustoHora(), m.getHorimetro(), m.getFabricante(), m.getModelo(), m.getNumeroSerie(),
                m.getAnoFabricacao(), m.getObservacao(), m.getVersao(), parametros,
                m.getTurnos().stream().map(Turno::getId).sorted().toList());
    }

    TipoMaquina tipo(Long id) {
        return tipoRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Tipo de máquina não encontrado: " + id));
    }

    Maquina maquina(Long id) {
        return maquinaRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Máquina não encontrada: " + id));
    }
}
