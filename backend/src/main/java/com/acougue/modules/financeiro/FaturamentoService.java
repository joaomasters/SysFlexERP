package com.acougue.modules.financeiro;

import com.acougue.entity.*;
import com.acougue.exception.BusinessException;
import com.acougue.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FaturamentoService {

    private final FaturamentoClienteRepository faturamentoRepo;
    private final ContasAReceberRepository     contasRepo;
    private final ClienteRepository            clienteRepo;
    private final ContasAReceberPagamentoRepository pagamentoRepo;



    @Transactional
    public FaturamentoCliente gerarFechamento(Long clienteId, LocalDate inicio, LocalDate fim) {
        Cliente cliente = clienteRepo.findById(clienteId)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado: " + clienteId));

        List<FaturamentoCliente> sobrepostos = faturamentoRepo.buscarSobrepostos(clienteId, inicio, fim);
        if (!sobrepostos.isEmpty()) {
            FaturamentoCliente existente = sobrepostos.get(0);
            throw new BusinessException(String.format(
                    "Já existe um fechamento em aberto para esse cliente cobrindo parte desse período " +
                            "(fechamento #%d, %s a %s). Gerar outro cobraria as mesmas vendas duas vezes.",
                    existente.getId(), existente.getPeriodoInicio(), existente.getPeriodoFim()));
        }

        LocalDateTime dtInicio = inicio.atStartOfDay();
        LocalDateTime dtFim    = fim.atTime(LocalTime.MAX);

        // Agrupa só fiado avulso (lançado pelo PDV) ainda em aberto — nunca
        // vendas já quitadas na hora (dinheiro/cartão/PIX) nem contas já
        // absorvidas por outro fechamento. É isso que evita cobrar 2x.
        List<ContasAReceber> elegiveis = contasRepo.buscarFiadoAvulsoElegivel(clienteId, dtInicio, dtFim);

        if (elegiveis.isEmpty()) {
            throw new BusinessException(
                    "Nenhum fiado em aberto encontrado para esse cliente no período informado — nada a faturar.");
        }

        BigDecimal totalVendas = elegiveis.stream()
                .map(ContasAReceber::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPago = elegiveis.stream()
                .map(ContasAReceber::getValorPago)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldo = totalVendas.subtract(totalPago).max(BigDecimal.ZERO);

        FaturamentoCliente fat = FaturamentoCliente.builder()
                .cliente(cliente)
                .periodoInicio(inicio)
                .periodoFim(fim)
                .totalVendas(totalVendas)
                .totalPago(totalPago)
                .saldoDevedor(saldo)
                .status(saldo.compareTo(BigDecimal.ZERO) == 0 ? "QUITADO" : "ABERTO")
                .dataVencimento(fim.plusDays(5))
                .build();
        fat = faturamentoRepo.save(fat);
        final FaturamentoCliente faturamentoSalvo = fat;

        ContasAReceber contaConsolidada = ContasAReceber.builder()
                .cliente(cliente)
                .faturamento(fat)
                .descricao(String.format("Faturamento %s a %s (%d venda(s) fiado)", inicio, fim, elegiveis.size()))
                .valor(totalVendas)
                .valorPago(totalPago)
                .dataEmissao(LocalDate.now())
                .dataVencimento(fat.getDataVencimento())
                .status(fat.getStatus().equals("QUITADO") ? "PAGO" : "ABERTO")
                .build();
        contasRepo.save(contaConsolidada);

        // A conta consolidada já nasce com o que foi pago nas contas avulsas
        // absorvidas. Sem registrar isso no histórico, SUM(histórico) ficaria
        // diferente de valor_pago — e o relatório de recebimentos mostraria
        // um "total recebido" que não bate com as linhas listadas.
        if (totalPago.compareTo(BigDecimal.ZERO) > 0) {
            pagamentoRepo.save(ContasAReceberPagamento.builder()
                    .conta(contaConsolidada)
                    .dataPagamento(LocalDateTime.now())
                    .valor(totalPago)
                    .saldoAnterior(totalVendas)
                    .saldoPosterior(totalVendas.subtract(totalPago))
                    .origem("TRANSFERENCIA")
                    .build());
        }

        // Absorve as contas de fiado individuais: somem da lista solta de
        // "a receber", mas continuam no banco pra auditoria/rastreabilidade,
        // apontando pro fechamento que as engoliu.
        elegiveis.forEach(c -> {
            c.setStatus("AGRUPADO");
            c.setAbsorvidoPorFaturamento(faturamentoSalvo);
        });
        contasRepo.saveAll(elegiveis);

        return fat;
    }

    /**
     * Registra um recebimento (parcial ou total) e grava a linha correspondente
     * no histórico, com o saldo antes/depois e o usuário que recebeu.
     *
     * Rejeita valor <= 0 e valor acima do saldo: com histórico, "saldo após o
     * recebimento" negativo não faria sentido e quebraria a soma do relatório.
     * A conta é carregada com lock de escrita para que dois recebimentos
     * simultâneos não leiam o mesmo saldo anterior.
     */
    @Transactional
    public ContasAReceber registrarPagamento(Long contaId, BigDecimal valorPago, Long usuarioId) {
        ContasAReceber conta = contasRepo.buscarParaAtualizar(contaId)
                .orElseThrow(() -> new EntityNotFoundException("Conta não encontrada: " + contaId));

        if ("PAGO".equals(conta.getStatus())) {
            throw new BusinessException("Conta já está quitada.");
        }
        if ("CANCELADO".equals(conta.getStatus())) {
            throw new BusinessException("Conta cancelada não pode receber pagamento.");
        }
        if ("AGRUPADO".equals(conta.getStatus())) {
            // O recebimento passa a acontecer só pela conta consolidada do fechamento
            // (ver ContasAReceber#absorvidoPorFaturamento) — nunca nas duas, pra não cobrar 2x.
            throw new BusinessException(
                    "Esta conta foi agrupada em um fechamento — lance o recebimento na conta consolidada do fechamento.");
        }

        BigDecimal valor = valorPago == null ? null : valorPago.setScale(2, RoundingMode.HALF_UP);
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Informe um valor de recebimento maior que zero.");
        }

        BigDecimal jaPago = conta.getValorPago() != null ? conta.getValorPago() : BigDecimal.ZERO;
        BigDecimal saldoAnterior = conta.getValor().subtract(jaPago);
        if (valor.compareTo(saldoAnterior) > 0) {
            throw new BusinessException(String.format(
                    "Valor recebido (R$ %.2f) maior que o saldo da conta (R$ %.2f).", valor, saldoAnterior));
        }
        BigDecimal saldoPosterior = saldoAnterior.subtract(valor);

        conta.setValorPago(jaPago.add(valor));
        conta.setDataPagamento(LocalDate.now());
        conta.setStatus(saldoPosterior.compareTo(BigDecimal.ZERO) <= 0 ? "PAGO" : "PARCIAL");

        if (conta.getFaturamento() != null) {
            FaturamentoCliente fat = conta.getFaturamento();
            fat.setTotalPago(fat.getTotalPago().add(valor));
            fat.setSaldoDevedor(fat.getTotalVendas().subtract(fat.getTotalPago()).max(BigDecimal.ZERO));
            fat.setStatus(fat.getSaldoDevedor().compareTo(BigDecimal.ZERO) == 0 ? "QUITADO" : "PARCIAL");
            faturamentoRepo.save(fat);
        }

        ContasAReceber salva = contasRepo.save(conta);

        pagamentoRepo.save(ContasAReceberPagamento.builder()
                .conta(salva)
                .dataPagamento(LocalDateTime.now())
                .valor(valor)
                .saldoAnterior(saldoAnterior)
                .saldoPosterior(saldoPosterior)
                .usuarioId(usuarioId)
                .origem(ContasAReceberPagamento.ORIGEM_PAGAMENTO)
                .build());

        return salva;
    }

    public List<ContasAReceber> listarContasCliente(Long clienteId) {
        return contasRepo.findByClienteId(clienteId);
    }

    public List<ContasAReceber> listarPorStatus(String status) {
        return contasRepo.findByStatusOrderByDataVencimentoAsc(status);
    }

    public BigDecimal saldoAbertoCliente(Long clienteId) {
        return contasRepo.saldoAberto(clienteId);
    }

    public List<FaturamentoCliente> listarFaturamentosAbertos() {
        return faturamentoRepo.findByStatusIn(List.of("ABERTO", "PARCIAL", "VENCIDO"));
    }

    @Transactional
    public void marcarVencidos() {
        List<FaturamentoCliente> vencidos = faturamentoRepo
                .findByDataVencimentoBeforeAndStatusIn(LocalDate.now(), List.of("ABERTO", "PARCIAL"));
        vencidos.forEach(f -> {
            f.setStatus("VENCIDO");
            faturamentoRepo.save(f);
        });
    }
}