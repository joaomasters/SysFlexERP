package com.acougue.modules.pdv;

import com.acougue.entity.*;
import com.acougue.exception.BusinessException;
import com.acougue.modules.pdv.dto.FechamentoCaixaDetalhadoDTO;
import com.acougue.modules.pdv.dto.SangriaDTO;
import com.acougue.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CaixaService {

    private final CaixaRepository          caixaRepo;
    private final SangriaCaixaRepository   sangriaRepo;
    private final VendaRepository          vendaRepo;
    private final PagamentoVendaRepository pagamentoRepo;

    /**
     * Busca o caixa aberto no momento — evita que o frontend precise
     * "adivinhar"/fixar um id de caixa. Se não houver nenhum caixa aberto,
     * o operador precisa abrir um primeiro.
     */
    /**
     * Busca o caixa aberto do operador informado. Antes esse método pegava
     * "o caixa aberto mais recente" globalmente, o que quebrava se dois
     * operadores tivessem caixa aberto ao mesmo tempo (um deles ficava
     * invisível pro sistema). Agora cada operador só enxerga o seu.
     */
    public Caixa buscarCaixaAbertoDoOperador(Long operadorId) {
        return caixaRepo.findFirstByOperadorIdAndStatus(operadorId, "ABERTO")
                .orElseThrow(() -> new BusinessException("Você não tem nenhum caixa aberto. Abra um caixa antes de vender."));
    }

    /** Histórico de sessões de caixa (abertas e fechadas), mais recente primeiro. */
    public List<Caixa> listarTodos() {
        return caixaRepo.findAllByOrderByDataAberturaDesc();
    }

    @Transactional
    public SangriaCaixa registrarMovimento(Long caixaId, SangriaDTO dto) {
        Caixa caixa = buscarCaixaAberto(caixaId);

        if (dto.getValor() == null || dto.getValor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor inválido para " + dto.getTipo());
        }

        SangriaCaixa mov = SangriaCaixa.builder()
                .caixa(caixa)
                .tipo(dto.getTipo())
                .valor(dto.getValor())
                .motivo(dto.getMotivo())
                .operadorId(dto.getOperadorId())
                .build();

        if ("SANGRIA".equals(dto.getTipo())) {
            caixa.setTotalSangria(caixa.getTotalSangria().add(dto.getValor()));
        } else {
            caixa.setTotalSuprimento(caixa.getTotalSuprimento().add(dto.getValor()));
        }
        caixaRepo.save(caixa);
        return sangriaRepo.save(mov);
    }

    public FechamentoCaixaDetalhadoDTO calcularFechamento(Long caixaId) {
        Caixa caixa = caixaRepo.findById(caixaId)
                .orElseThrow(() -> new EntityNotFoundException("Caixa não encontrado: " + caixaId));

        List<Venda> vendas = vendaRepo.findByCaixaIdAndStatus(caixaId, "FECHADA");

        BigDecimal totalDinheiro = BigDecimal.ZERO;
        BigDecimal totalCredito  = BigDecimal.ZERO;
        BigDecimal totalDebito   = BigDecimal.ZERO;
        BigDecimal totalPix      = BigDecimal.ZERO;
        BigDecimal totalFiado    = BigDecimal.ZERO;
        BigDecimal totalVoucher  = BigDecimal.ZERO;
        BigDecimal totalVendas   = BigDecimal.ZERO;

        for (Venda venda : vendas) {
            totalVendas = totalVendas.add(venda.getTotal());
            for (PagamentoVenda pag : pagamentoRepo.findByVendaId(venda.getId())) {
                switch (pag.getFormaPagamento()) {
                    case "DINHEIRO" -> totalDinheiro = totalDinheiro.add(pag.getValor());
                    case "CREDITO"  -> totalCredito  = totalCredito.add(pag.getValor());
                    case "DEBITO"   -> totalDebito   = totalDebito.add(pag.getValor());
                    case "PIX"      -> totalPix      = totalPix.add(pag.getValor());
                    case "FIADO"    -> totalFiado    = totalFiado.add(pag.getValor());
                    case "VOUCHER"  -> totalVoucher  = totalVoucher.add(pag.getValor());
                    default -> {}
                }
            }
        }

        BigDecimal saldoEsperado = caixa.getValorAbertura()
                .add(totalDinheiro)
                .add(caixa.getTotalSuprimento())
                .subtract(caixa.getTotalSangria());

        return FechamentoCaixaDetalhadoDTO.builder()
                .caixaId(caixaId)
                .valorAbertura(caixa.getValorAbertura())
                .totalSangria(caixa.getTotalSangria())
                .totalSuprimento(caixa.getTotalSuprimento())
                .totalVendas(totalVendas)
                .totalDinheiro(totalDinheiro)
                .totalCredito(totalCredito)
                .totalDebito(totalDebito)
                .totalPix(totalPix)
                .totalFiado(totalFiado)
                .totalVoucher(totalVoucher)
                .saldoEsperado(saldoEsperado)
                .quantidadeVendas(vendas.size())
                .movimentos(sangriaRepo.findByCaixaIdOrderByCreatedAtDesc(caixaId))
                .build();
    }

    public List<SangriaCaixa> listarMovimentos(Long caixaId) {
        return sangriaRepo.findByCaixaIdOrderByCreatedAtDesc(caixaId);
    }

    private Caixa buscarCaixaAberto(Long caixaId) {
        Caixa caixa = caixaRepo.findById(caixaId)
                .orElseThrow(() -> new EntityNotFoundException("Caixa não encontrado: " + caixaId));
        if (!"ABERTO".equals(caixa.getStatus())) {
            throw new BusinessException("Caixa já está fechado.");
        }
        return caixa;
    }
}