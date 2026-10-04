package com.acougue.modules.financeiro;

import com.acougue.entity.ContasPagar;
import com.acougue.entity.ContasPagarPagamento;
import com.acougue.exception.BusinessException;
import com.acougue.modules.financeiro.dto.ContasPagarDTO;
import com.acougue.repository.ContasPagarPagamentoRepository;
import com.acougue.repository.ContasPagarRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContasPagarService {

    private final ContasPagarRepository contasRepo;
    private final ContasPagarPagamentoRepository pagamentoRepo;

    public ContasPagar criar(ContasPagarDTO dto) {
        return contasRepo.save(ContasPagar.builder()
                .descricao(dto.getDescricao())
                .fornecedor(dto.getFornecedor())
                .valor(dto.getValor())
                .dataVencimento(dto.getDataVencimento())
                .categoria(dto.getCategoria())
                .observacao(dto.getObservacao())
                .build());
    }

    /**
     * Registra um pagamento (parcial ou total) e grava a linha correspondente no
     * histórico, com o saldo antes/depois e o usuário que pagou.
     *
     * Rejeita valor <= 0 e valor acima do saldo (mesma regra e motivos de
     * FaturamentoService#registrarPagamento). A conta é carregada com lock de escrita.
     */
    @Transactional
    public ContasPagar pagar(Long contaId, BigDecimal valorPago, Long usuarioId) {
        ContasPagar conta = contasRepo.buscarParaAtualizar(contaId)
                .orElseThrow(() -> new EntityNotFoundException("Conta não encontrada: " + contaId));
        if ("CANCELADO".equals(conta.getStatus()) || "PAGO".equals(conta.getStatus())) {
            throw new BusinessException("Conta já está " + conta.getStatus().toLowerCase() + ".");
        }

        BigDecimal valor = valorPago == null ? null : valorPago.setScale(2, RoundingMode.HALF_UP);
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Informe um valor de pagamento maior que zero.");
        }

        BigDecimal jaPago = conta.getValorPago() != null ? conta.getValorPago() : BigDecimal.ZERO;
        BigDecimal saldoAnterior = conta.getValor().subtract(jaPago);
        if (valor.compareTo(saldoAnterior) > 0) {
            throw new BusinessException(String.format(
                    "Valor pago (R$ %.2f) maior que o saldo da conta (R$ %.2f).", valor, saldoAnterior));
        }
        BigDecimal saldoPosterior = saldoAnterior.subtract(valor);

        conta.setValorPago(jaPago.add(valor));
        if (saldoPosterior.compareTo(BigDecimal.ZERO) <= 0) {
            conta.setStatus("PAGO");
            conta.setDataPagamento(LocalDate.now());
        } else {
            conta.setStatus("PARCIAL");
        }
        ContasPagar salva = contasRepo.save(conta);

        pagamentoRepo.save(ContasPagarPagamento.builder()
                .conta(salva)
                .dataPagamento(LocalDateTime.now())
                .valor(valor)
                .saldoAnterior(saldoAnterior)
                .saldoPosterior(saldoPosterior)
                .usuarioId(usuarioId)
                .origem(ContasPagarPagamento.ORIGEM_PAGAMENTO)
                .build());

        return salva;
    }

    @Transactional
    public void cancelar(Long contaId) {
        ContasPagar conta = buscar(contaId);
        if ("PAGO".equals(conta.getStatus())) {
            throw new BusinessException("Não é possível cancelar uma conta já paga.");
        }
        conta.setStatus("CANCELADO");
        contasRepo.save(conta);
    }

    public List<ContasPagar> listarPorStatus(String status) {
        return contasRepo.findByStatusOrderByDataVencimentoAsc(status);
    }

    public List<ContasPagar> listarVencidas() {
        return contasRepo.findByDataVencimentoBeforeAndStatusOrderByDataVencimentoAsc(LocalDate.now(), "ABERTO");
    }

    public List<ContasPagar> listarPorPeriodo(LocalDate inicio, LocalDate fim) {
        return contasRepo.findByDataVencimentoBetweenOrderByDataVencimentoAsc(inicio, fim);
    }

    private ContasPagar buscar(Long id) {
        return contasRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Conta não encontrada: " + id));
    }
}
