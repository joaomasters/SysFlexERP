package com.acougue.modules.financeiro;

import com.acougue.entity.ContasAReceberPagamento;
import com.acougue.entity.ContasPagarPagamento;
import com.acougue.modules.financeiro.dto.HistoricoContaDTO;
import com.acougue.modules.financeiro.dto.PagamentoHistoricoDTO;
import com.acougue.repository.ContasAReceberPagamentoRepository;
import com.acougue.repository.ContasPagarPagamentoRepository;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.IdentificacaoUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Histórico de pagamentos parciais de Contas a Receber / Contas a Pagar —
 * a base do relatório "quem pagou/recebeu quanto, quando, e como ficou o saldo".
 *
 * O histórico INTEIRO (não só a coluna de usuário) é restrito a administradores,
 * conforme a regra da tela. A checagem fica aqui, no serviço, e não só no controller:
 * assim nenhum outro caminho de código consegue consultar o histórico sem passar por ela.
 */
@Service
@RequiredArgsConstructor
public class HistoricoPagamentosService {

    private static final LocalDate DATA_MINIMA = LocalDate.of(2000, 1, 1);
    private static final LocalDate DATA_MAXIMA = LocalDate.of(2999, 12, 31);

    private final ContasAReceberPagamentoRepository receberRepo;
    private final ContasPagarPagamentoRepository    pagarRepo;
    private final IdentificacaoUsuario              identificacao;

    /** clienteId e contaId são filtros opcionais; período opcional (aberto = tudo). */
    @Transactional(readOnly = true)
    public List<HistoricoContaDTO> historicoRecebimentos(Long clienteId, Long contaId,
                                                         LocalDate inicio, LocalDate fim) {
        ContextoUsuario.exigirVisualizacaoIdentificacaoUsuarios();

        List<Linha> linhas = receberRepo
                .buscarHistorico(inicioDe(inicio), fimDe(fim), clienteId, contaId).stream()
                .map(HistoricoPagamentosService::linhaDe)
                .toList();
        return agrupar(linhas);
    }

    /** contaId é filtro opcional; fornecedor é busca parcial (vazio/nulo = sem filtro). */
    @Transactional(readOnly = true)
    public List<HistoricoContaDTO> historicoPagamentos(Long contaId, String fornecedor,
                                                       LocalDate inicio, LocalDate fim) {
        ContextoUsuario.exigirVisualizacaoIdentificacaoUsuarios();

        String filtroFornecedor = fornecedor == null ? "" : fornecedor.trim();
        List<Linha> linhas = pagarRepo
                .buscarHistorico(inicioDe(inicio), fimDe(fim), contaId, filtroFornecedor).stream()
                .map(HistoricoPagamentosService::linhaDe)
                .toList();
        return agrupar(linhas);
    }

    // ─────────────────────────────────────────────────────────────────

    /** Visão neutra de "um pagamento + dados da sua conta", comum a receber e pagar. */
    private record Linha(
            Long contaId, String descricao, String contraparte,
            BigDecimal valorConta, BigDecimal valorPagoConta, String statusConta,
            Long pagamentoId, LocalDateTime data, BigDecimal valor,
            BigDecimal saldoAnterior, BigDecimal saldoPosterior, Long usuarioId, String origem) {}

    private static Linha linhaDe(ContasAReceberPagamento p) {
        var c = p.getConta();
        return new Linha(c.getId(), c.getDescricao(), c.getCliente().getNome(),
                c.getValor(), c.getValorPago(), c.getStatus(),
                p.getId(), p.getDataPagamento(), p.getValor(),
                p.getSaldoAnterior(), p.getSaldoPosterior(), p.getUsuarioId(), p.getOrigem());
    }

    private static Linha linhaDe(ContasPagarPagamento p) {
        var c = p.getConta();
        return new Linha(c.getId(), c.getDescricao(), c.getFornecedor(),
                c.getValor(), c.getValorPago(), c.getStatus(),
                p.getId(), p.getDataPagamento(), p.getValor(),
                p.getSaldoAnterior(), p.getSaldoPosterior(), p.getUsuarioId(), p.getOrigem());
    }

    /** Agrupa as linhas (já ordenadas por conta e data) em um bloco por conta. */
    private List<HistoricoContaDTO> agrupar(List<Linha> linhas) {
        if (linhas.isEmpty()) {
            return List.of();
        }

        Map<Long, String> nomes = identificacao.nomesPorId(
                linhas.stream().map(Linha::usuarioId).filter(Objects::nonNull).collect(Collectors.toSet()));

        Map<Long, List<Linha>> porConta = new LinkedHashMap<>();
        for (Linha l : linhas) {
            porConta.computeIfAbsent(l.contaId(), k -> new ArrayList<>()).add(l);
        }

        List<HistoricoContaDTO> resultado = new ArrayList<>();
        for (List<Linha> doConta : porConta.values()) {
            Linha cab = doConta.get(0);
            BigDecimal pago = cab.valorPagoConta() != null ? cab.valorPagoConta() : BigDecimal.ZERO;

            List<PagamentoHistoricoDTO> pagamentos = doConta.stream()
                    .map(l -> new PagamentoHistoricoDTO(
                            l.pagamentoId(), l.data(), l.valor(),
                            l.saldoAnterior(), l.saldoPosterior(),
                            nomeDe(l, nomes), l.origem()))
                    .toList();

            resultado.add(new HistoricoContaDTO(
                    cab.contaId(), cab.descricao(), cab.contraparte(),
                    cab.valorConta(), pago, cab.valorConta().subtract(pago),
                    cab.statusConta(), pagamentos));
        }
        return resultado;
    }

    private String nomeDe(Linha l, Map<Long, String> nomes) {
        if (l.usuarioId() == null) {
            return null;
        }
        return nomes.getOrDefault(l.usuarioId(), identificacao.rotuloDesconhecido(l.usuarioId()));
    }

    private static LocalDateTime inicioDe(LocalDate inicio) {
        return (inicio != null ? inicio : DATA_MINIMA).atStartOfDay();
    }

    private static LocalDateTime fimDe(LocalDate fim) {
        return (fim != null ? fim : DATA_MAXIMA).atTime(LocalTime.MAX);
    }
}
