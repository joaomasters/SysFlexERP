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

    @Transactional
    public ContasAReceber registrarPagamento(Long contaId, BigDecimal valorPago) {
        ContasAReceber conta = contasRepo.findById(contaId)
                .orElseThrow(() -> new EntityNotFoundException("Conta não encontrada: " + contaId));

        if ("PAGO".equals(conta.getStatus())) {
            throw new BusinessException("Conta já está quitada.");
        }

        conta.setValorPago(conta.getValorPago().add(valorPago));
        conta.setDataPagamento(LocalDate.now());

        BigDecimal saldo = conta.getValor().subtract(conta.getValorPago());
        if (saldo.compareTo(BigDecimal.ZERO) <= 0) {
            conta.setStatus("PAGO");
        } else {
            conta.setStatus("PARCIAL");
        }


        if (conta.getFaturamento() != null) {
            FaturamentoCliente fat = conta.getFaturamento();
            fat.setTotalPago(fat.getTotalPago().add(valorPago));
            fat.setSaldoDevedor(fat.getTotalVendas().subtract(fat.getTotalPago()).max(BigDecimal.ZERO));
            fat.setStatus(fat.getSaldoDevedor().compareTo(BigDecimal.ZERO) == 0 ? "QUITADO" : "PARCIAL");
            faturamentoRepo.save(fat);
        }

        return contasRepo.save(conta);
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