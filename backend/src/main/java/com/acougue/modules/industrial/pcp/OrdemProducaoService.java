package com.acougue.modules.industrial.pcp;

import com.acougue.entity.Cliente;
import com.acougue.entity.Produto;
import com.acougue.entity.Usuario;
import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.engenharia.*;
import com.acougue.modules.industrial.estrutura.Maquina;
import com.acougue.modules.industrial.estrutura.MaquinaRepository;
import com.acougue.modules.industrial.pcp.PcpDto.*;
import com.acougue.repository.ClienteRepository;
import com.acougue.repository.ProdutoRepository;
import com.acougue.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ordens de produção: criação, edição enquanto planejada, transições de status e liberação.
 * A liberação copia a BOM e o roteiro ativos para a OP (revisão congelada), calculados para a quantidade da OP.
 * Esta sprint não movimenta estoque: consumo e entrada de produção vêm com os apontamentos (sprint 3).
 */
@Service
@RequiredArgsConstructor
public class OrdemProducaoService {

    /** Transições permitidas. ENCERRADA e CANCELADA são finais. */
    static final Map<String, Set<String>> TRANSICOES = Map.of(
            "PLANEJADA", Set.of("LIBERADA", "CANCELADA"),
            "LIBERADA", Set.of("PLANEJADA", "EM_PRODUCAO", "CANCELADA"),
            "EM_PRODUCAO", Set.of("PAUSADA", "CONCLUIDA"),
            "PAUSADA", Set.of("EM_PRODUCAO"),
            "CONCLUIDA", Set.of("ENCERRADA", "EM_PRODUCAO"),
            "ENCERRADA", Set.of(),
            "CANCELADA", Set.of());
    private static final List<String> ORDEM_EXIBICAO = OrdemProducao.STATUS;

    private final OrdemProducaoRepository ordemRepo;
    private final OpEventoRepository eventoRepo;
    private final ProdutoRepository produtoRepo;
    private final ClienteRepository clienteRepo;
    private final FichaTecnicaRepository fichaRepo;
    private final BomRepository bomRepo;
    private final RoteiroRepository roteiroRepo;
    private final MaquinaRepository maquinaRepo;
    private final UsuarioRepository usuarioRepo;

    /** Tempo previsto de uma operação: preparação mais o tempo por unidade vezes a quantidade. */
    public static BigDecimal tempoPrevisto(BigDecimal setup, BigDecimal porUnidade, BigDecimal quantidade) {
        return setup.add(porUnidade.multiply(quantidade)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Motivo é obrigatório quando a mudança desfaz algo ou interrompe a produção. */
    static boolean exigeMotivo(String de, String para) {
        return "CANCELADA".equals(para) || "PAUSADA".equals(para)
                || ("LIBERADA".equals(de) && "PLANEJADA".equals(para))
                || ("CONCLUIDA".equals(de) && "EM_PRODUCAO".equals(para));
    }

    static void validarTransicao(String de, String para) {
        if (!OrdemProducao.STATUS.contains(para)) throw new BusinessException("Status inválido: " + para);
        if (!TRANSICOES.getOrDefault(de, Set.of()).contains(para)) {
            throw new BusinessException("Uma OP " + rotulo(de) + " não pode passar para " + rotulo(para) + ".");
        }
    }

    static String rotulo(String status) {
        return switch (status) {
            case "PLANEJADA" -> "planejada";
            case "LIBERADA" -> "liberada";
            case "EM_PRODUCAO" -> "em produção";
            case "PAUSADA" -> "pausada";
            case "CONCLUIDA" -> "concluída";
            case "ENCERRADA" -> "encerrada";
            case "CANCELADA" -> "cancelada";
            default -> status;
        };
    }

    // ── Consultas ────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<OrdemResumo> listar(String status) {
        LocalDate hoje = LocalDate.now();
        return ordemRepo.listar(status == null || status.isBlank() ? null : status).stream()
                .map(o -> resumo(o, hoje)).toList();
    }

    @Transactional(readOnly = true)
    public OrdemOut buscar(Long id) {
        return detalhe(ordem(id));
    }

    // ── Escrita ──────────────────────────────────────────────────────────
    @Transactional
    public OrdemOut criar(OrdemIn in, Long usuarioId) {
        OrdemProducao o = OrdemProducao.builder().criadoPorId(usuarioId).build();
        aplicar(o, in);
        // o número só é reservado depois das validações: uma OP recusada não deixa buraco na numeração
        o.setNumero(String.format("OP%06d", ordemRepo.proximoNumero()));
        o = ordemRepo.saveAndFlush(o);
        registrar(o.getId(), null, "PLANEJADA", null, usuarioId);
        return detalhe(o);
    }

    @Transactional
    public OrdemOut editar(Long id, OrdemIn in) {
        OrdemProducao o = ordem(id);
        conferirVersao(o, in.versao());
        if (!"PLANEJADA".equals(o.getStatus())) {
            throw new BusinessException("Só OPs planejadas podem ser editadas. Para mudar uma OP liberada, volte-a para planejada.");
        }
        aplicar(o, in);
        return detalhe(ordemRepo.saveAndFlush(o));
    }

    @Transactional
    public OrdemOut mudarStatus(Long id, StatusIn in, Long usuarioId) {
        OrdemProducao o = ordem(id);
        conferirVersao(o, in.versao());
        String de = o.getStatus();
        String para = in.status();
        validarTransicao(de, para);
        String motivo = in.motivo() == null || in.motivo().isBlank() ? null : in.motivo().trim();
        if (motivo == null && exigeMotivo(de, para)) throw new BusinessException("Informe o motivo.");

        if ("LIBERADA".equals(para) && "PLANEJADA".equals(de)) liberar(o, usuarioId);
        if ("PLANEJADA".equals(para)) estornarLiberacao(o);
        if ("EM_PRODUCAO".equals(para) && "CONCLUIDA".equals(de) && o.getOperacoes().isEmpty()) {
            throw new BusinessException("A OP não tem operações para reabrir.");
        }
        o.setStatus(para);
        o = ordemRepo.saveAndFlush(o);
        registrar(o.getId(), de, para, motivo, usuarioId);
        return detalhe(o);
    }

    /** O PCP escolhe em qual máquina do centro a operação vai rodar (ou limpa a escolha). */
    @Transactional
    public OrdemOut definirMaquina(Long ordemId, Long operacaoId, MaquinaOperacaoIn in) {
        OrdemProducao o = ordem(ordemId);
        conferirVersao(o, in.versao());
        if (!List.of("LIBERADA", "EM_PRODUCAO", "PAUSADA").contains(o.getStatus())) {
            throw new BusinessException("A máquina só é escolhida em OPs liberadas ou em produção.");
        }
        OpOperacao op = o.getOperacoes().stream().filter(x -> x.getId().equals(operacaoId)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Operação não encontrada nesta OP: " + operacaoId));
        if ("CONCLUIDA".equals(op.getStatus())) throw new BusinessException("A operação já foi concluída.");
        Maquina m = null;
        if (in.maquinaId() != null) {
            m = maquinaRepo.findById(in.maquinaId())
                    .orElseThrow(() -> new EntityNotFoundException("Máquina não encontrada: " + in.maquinaId()));
            if (!m.getCentroTrabalho().getId().equals(op.getCentroTrabalho().getId())) {
                throw new BusinessException("A máquina " + m.getCodigo() + " não pertence ao centro " + op.getCentroTrabalho().getNome() + ".");
            }
            if ("INATIVA".equals(m.getStatus())) throw new BusinessException("A máquina " + m.getCodigo() + " está inativa.");
            if (op.getTipoMaquina() != null && !op.getTipoMaquina().getId().equals(m.getTipoMaquina().getId())) {
                throw new BusinessException("A operação pede uma máquina do tipo " + op.getTipoMaquina().getNome() + ".");
            }
        }
        op.setMaquina(m);
        return detalhe(ordemRepo.saveAndFlush(o));
    }

    // ── regras internas ─────────────────────────────────────────────────
    private void aplicar(OrdemProducao o, OrdemIn in) {
        Produto produto = produtoRepo.findById(in.produtoId())
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + in.produtoId()));
        if (!Boolean.TRUE.equals(produto.getAtivo())) throw new BusinessException("O produto " + produto.getNome() + " está inativo.");
        if (in.fimPrevisto().isBefore(in.inicioPrevisto())) {
            throw new BusinessException("O fim previsto não pode ser antes do início.");
        }
        fichaRepo.findByProdutoId(produto.getId()).ifPresent(f -> validarLote(f, in.quantidade(), produto.getUnidadeMedida()));
        Cliente cliente = null;
        if (in.clienteId() != null) {
            cliente = clienteRepo.findById(in.clienteId())
                    .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado: " + in.clienteId()));
        }
        o.setProduto(produto);
        o.setQuantidade(in.quantidade());
        o.setPrioridade(in.prioridade() == null ? 3 : in.prioridade().shortValue());
        o.setInicioPrevisto(in.inicioPrevisto());
        o.setFimPrevisto(in.fimPrevisto());
        o.setCliente(cliente);
        o.setPedidoReferencia(texto(in.pedidoReferencia()));
        o.setObservacao(texto(in.observacao()));
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    static void validarLote(FichaTecnica f, BigDecimal quantidade, String unidade) {
        if (f.getLoteMinimo() != null && quantidade.compareTo(f.getLoteMinimo()) < 0) {
            throw new BusinessException("A quantidade é menor que o lote mínimo de "
                    + f.getLoteMinimo().stripTrailingZeros().toPlainString() + " " + unidade + ".");
        }
        if (f.getLoteMultiplo() != null && quantidade.remainder(f.getLoteMultiplo()).signum() != 0) {
            throw new BusinessException("A quantidade precisa ser múltipla de "
                    + f.getLoteMultiplo().stripTrailingZeros().toPlainString() + " " + unidade + ".");
        }
    }

    /** Pendências que impedem a liberação (vazia = pode liberar). */
    List<String> pendencias(OrdemProducao o) {
        List<String> p = new ArrayList<>();
        Optional<Bom> bom = bomRepo.ativaDoProduto(o.getProduto().getId());
        if (bom.isEmpty()) p.add("O produto não tem estrutura (BOM) ativa.");
        else bom.get().getItens().stream().filter(i -> !Boolean.TRUE.equals(i.getComponente().getAtivo()))
                .forEach(i -> p.add("O componente " + i.getComponente().getNome() + " está inativo."));
        Optional<Roteiro> roteiro = roteiroRepo.ativoDoProduto(o.getProduto().getId());
        if (roteiro.isEmpty()) p.add("O produto não tem roteiro ativo.");
        else roteiro.get().getOperacoes().stream().filter(op -> !op.getCentroTrabalho().getAtivo())
                .forEach(op -> p.add("O centro " + op.getCentroTrabalho().getNome() + " do roteiro está inativo."));
        return p;
    }

    private void liberar(OrdemProducao o, Long usuarioId) {
        List<String> p = pendencias(o);
        if (!p.isEmpty()) throw new BusinessException("Não dá para liberar: " + String.join(" ", p));
        Bom bom = bomRepo.ativaDoProduto(o.getProduto().getId()).orElseThrow();
        Roteiro roteiro = roteiroRepo.ativoDoProduto(o.getProduto().getId()).orElseThrow();
        o.getMateriais().clear();
        o.getOperacoes().clear();
        for (BomItem i : bom.getItens()) {
            o.getMateriais().add(OpMaterial.builder().ordem(o).sequencia(i.getSequencia()).produto(i.getComponente())
                    .quantidadeLiquida(BomService.liquida(i.getQuantidade(), bom.getQuantidadeBase(), o.getQuantidade()))
                    .perdaPercentual(i.getPerdaPercentual())
                    .quantidadePrevista(BomService.necessidade(i.getQuantidade(), i.getPerdaPercentual(),
                            bom.getQuantidadeBase(), o.getQuantidade()))
                    .unidade(i.getComponente().getUnidadeMedida()).build());
        }
        for (RoteiroOperacao r : roteiro.getOperacoes()) {
            o.getOperacoes().add(OpOperacao.builder().ordem(o).sequencia(r.getSequencia()).descricao(r.getDescricao())
                    .centroTrabalho(r.getCentroTrabalho()).tipoMaquina(r.getTipoMaquina())
                    .tempoSetupMin(r.getTempoSetupMin())
                    .tempoPrevistoMin(tempoPrevisto(r.getTempoSetupMin(), r.getTempoPorUnidadeMin(), o.getQuantidade()))
                    .instrucoes(r.getInstrucoes()).build());
        }
        o.setBom(bom);
        o.setRoteiro(roteiro);
        o.setLiberadaEm(LocalDateTime.now());
        o.setLiberadaPorId(usuarioId);
    }

    private void estornarLiberacao(OrdemProducao o) {
        if (o.getOperacoes().stream().anyMatch(op -> !"PENDENTE".equals(op.getStatus()))) {
            throw new BusinessException("A OP já teve operação iniciada e não pode voltar para planejada.");
        }
        o.getMateriais().clear();
        o.getOperacoes().clear();
        o.setBom(null);
        o.setRoteiro(null);
        o.setLiberadaEm(null);
        o.setLiberadaPorId(null);
    }

    private void registrar(Long ordemId, String de, String para, String motivo, Long usuarioId) {
        eventoRepo.save(OpEvento.builder().ordemId(ordemId).statusAnterior(de).statusNovo(para).motivo(motivo)
                .usuarioId(usuarioId).build());
    }

    private void conferirVersao(OrdemProducao o, Long versao) {
        if (versao != null && !versao.equals(o.getVersao())) {
            throw new BusinessException("A OP " + o.getNumero() + " foi alterada por outra pessoa. Recarregue e tente de novo.");
        }
    }

    private OrdemProducao ordem(Long id) {
        return ordemRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("OP não encontrada: " + id));
    }

    // ── montagem das respostas ──────────────────────────────────────────
    private static OrdemResumo resumo(OrdemProducao o, LocalDate hoje) {
        Produto p = o.getProduto();
        boolean atrasada = OrdemProducao.ABERTAS.contains(o.getStatus()) && o.getFimPrevisto().isBefore(hoje);
        return new OrdemResumo(o.getId(), o.getNumero(), p.getId(), p.getCodigoInterno(), p.getNome(), p.getUnidadeMedida(),
                o.getQuantidade(), o.getStatus(), o.getPrioridade(), o.getInicioPrevisto(), o.getFimPrevisto(),
                o.getCliente() == null ? null : o.getCliente().getNome(), o.getPedidoReferencia(), atrasada);
    }

    private OrdemOut detalhe(OrdemProducao o) {
        boolean previa = "PLANEJADA".equals(o.getStatus());
        List<MaterialOut> materiais;
        List<OperacaoOut> operacoes;
        if (previa) {
            materiais = bomRepo.ativaDoProduto(o.getProduto().getId()).map(b -> b.getItens().stream()
                    .map(i -> material(i.getComponente(),
                            BomService.liquida(i.getQuantidade(), b.getQuantidadeBase(), o.getQuantidade()),
                            i.getPerdaPercentual(),
                            BomService.necessidade(i.getQuantidade(), i.getPerdaPercentual(), b.getQuantidadeBase(), o.getQuantidade())))
                    .toList()).orElse(List.of());
            operacoes = roteiroRepo.ativoDoProduto(o.getProduto().getId()).map(r -> r.getOperacoes().stream()
                    .map(op -> new OperacaoOut(null, op.getSequencia(), op.getDescricao(), op.getCentroTrabalho().getId(),
                            op.getCentroTrabalho().getNome(), op.getTipoMaquina() == null ? null : op.getTipoMaquina().getId(),
                            op.getTipoMaquina() == null ? null : op.getTipoMaquina().getNome(), null, null, null,
                            op.getTempoSetupMin(), tempoPrevisto(op.getTempoSetupMin(), op.getTempoPorUnidadeMin(), o.getQuantidade()),
                            "PENDENTE"))
                    .toList()).orElse(List.of());
        } else {
            materiais = o.getMateriais().stream()
                    .map(m -> material(m.getProduto(), m.getQuantidadeLiquida(), m.getPerdaPercentual(), m.getQuantidadePrevista()))
                    .toList();
            operacoes = o.getOperacoes().stream().map(op -> new OperacaoOut(op.getId(), op.getSequencia(), op.getDescricao(),
                    op.getCentroTrabalho().getId(), op.getCentroTrabalho().getNome(),
                    op.getTipoMaquina() == null ? null : op.getTipoMaquina().getId(),
                    op.getTipoMaquina() == null ? null : op.getTipoMaquina().getNome(),
                    op.getMaquina() == null ? null : op.getMaquina().getId(),
                    op.getMaquina() == null ? null : op.getMaquina().getCodigo(),
                    op.getMaquina() == null ? null : op.getMaquina().getDescricao(),
                    op.getTempoSetupMin(), op.getTempoPrevistoMin(), op.getStatus())).toList();
        }
        List<OpEvento> eventos = eventoRepo.findByOrdemIdOrderByCreatedAtDescIdDesc(o.getId());
        Set<Long> ids = eventos.stream().map(OpEvento::getUsuarioId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nomes = usuarioRepo.findAllById(ids).stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNome));
        List<String> proximos = TRANSICOES.getOrDefault(o.getStatus(), Set.of()).stream()
                .sorted(Comparator.comparingInt(ORDEM_EXIBICAO::indexOf)).toList();
        return new OrdemOut(resumo(o, LocalDate.now()), o.getCliente() == null ? null : o.getCliente().getId(),
                o.getObservacao(), o.getVersao(),
                o.getBom() == null ? null : o.getBom().getVersao(), o.getRoteiro() == null ? null : o.getRoteiro().getVersao(),
                o.getLiberadaEm(), previa, "PLANEJADA".equals(o.getStatus()) ? pendencias(o) : List.of(), proximos,
                materiais, operacoes,
                eventos.stream().map(e -> new EventoOut(e.getId(), e.getStatusAnterior(), e.getStatusNovo(), e.getMotivo(),
                        e.getUsuarioId() == null ? null : nomes.get(e.getUsuarioId()), e.getCreatedAt())).toList());
    }

    private static MaterialOut material(Produto p, BigDecimal liquida, BigDecimal perda, BigDecimal prevista) {
        BigDecimal saldo = p.getEstoqueAtual() == null ? BigDecimal.ZERO : p.getEstoqueAtual();
        BigDecimal falta = prevista.subtract(saldo).max(BigDecimal.ZERO);
        return new MaterialOut(p.getId(), p.getCodigoInterno(), p.getNome(), p.getUnidadeMedida(), liquida, perda, prevista,
                saldo, falta);
    }
}
