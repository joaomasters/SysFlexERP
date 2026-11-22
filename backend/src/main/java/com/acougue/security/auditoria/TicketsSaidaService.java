package com.acougue.security.auditoria;

import com.acougue.entity.ItensVenda;
import com.acougue.entity.PagamentoVenda;
import com.acougue.entity.Usuario;
import com.acougue.entity.Venda;
import com.acougue.exception.BusinessException;
import com.acougue.repository.UsuarioRepository;
import com.acougue.repository.VendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Relatório de tickets de saída (vendas do PDV) para a Auditoria: todos os tickets do período,
 * inclusive os cancelados, cada um com seus itens, operador e formas de pagamento.
 */
@Service
@RequiredArgsConstructor
public class TicketsSaidaService {

    /** Limite do período: o relatório carrega os itens de todas as vendas de uma vez. */
    private static final int MAX_DIAS = 93;

    private final VendaRepository vendaRepo;
    private final UsuarioRepository usuarioRepo;

    @Transactional(readOnly = true)
    public List<TicketSaidaDTO> listar(LocalDate inicio, LocalDate fim, String status, Long operadorId) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            throw new BusinessException("Informe um período válido.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) > MAX_DIAS) {
            throw new BusinessException("O período do relatório pode ter no máximo " + MAX_DIAS + " dias.");
        }
        String statusFiltro = (status == null || status.isBlank()) ? null : status.trim().toUpperCase(Locale.ROOT);

        List<Venda> vendas = vendaRepo.findTicketsDoPeriodo(inicio.atStartOfDay(), fim.atTime(LocalTime.MAX)).stream()
                .filter(v -> statusFiltro == null || statusFiltro.equals(v.getStatus()))
                .filter(v -> operadorId == null || operadorId.equals(v.getOperadorId()))
                .toList();

        Set<Long> ids = vendas.stream().map(Venda::getOperadorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nomes = usuarioRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNome));

        return vendas.stream().map(v -> paraDto(v, nomes)).toList();
    }

    private TicketSaidaDTO paraDto(Venda v, Map<Long, String> nomes) {
        String formas = v.getPagamentos().stream()
                .map(PagamentoVenda::getFormaPagamento)
                .distinct()
                .collect(Collectors.joining(", "));
        List<TicketSaidaDTO.Item> itens = v.getItens().stream().map(this::itemParaDto).toList();
        return TicketSaidaDTO.builder()
                .id(v.getId())
                .numeroCupom(v.getNumeroCupom())
                .dataVenda(v.getDataVenda())
                .status(v.getStatus())
                .operadorId(v.getOperadorId())
                .operadorNome(v.getOperadorId() == null ? null : nomes.get(v.getOperadorId()))
                .clienteNome(v.getCliente() == null ? null : v.getCliente().getNome())
                .subtotal(zero(v.getSubtotal()))
                .desconto(zero(v.getDesconto()))
                .total(zero(v.getTotal()))
                .formasPagamento(formas)
                .observacao(v.getObservacao())
                .itens(itens)
                .build();
    }

    private TicketSaidaDTO.Item itemParaDto(ItensVenda i) {
        return TicketSaidaDTO.Item.builder()
                .produto(i.getProduto().getNome())
                .unidade(i.getProduto().getUnidadeMedida())
                .quantidade(i.getQuantidade())
                .precoUnitario(i.getPrecoUnitario())
                .desconto(zero(i.getDescontoItem()))
                .total(i.getTotalItem())
                .build();
    }

    private static BigDecimal zero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
