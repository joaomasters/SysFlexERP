package com.acougue.modules.pdv;

import com.acougue.entity.*;
import com.acougue.exception.BusinessException;
import com.acougue.exception.LimiteCreditoExcedidoException;
import com.acougue.modules.pdv.dto.ContagemCedulasDTO;
import com.acougue.modules.pdv.dto.FechamentoCaixaDetalhadoDTO;
import com.acougue.modules.pdv.dto.SangriaDTO;
import com.acougue.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class CaixaService {

    private final CaixaRepository          caixaRepo;
    private final SangriaCaixaRepository   sangriaRepo;
    private final VendaRepository          vendaRepo;
    private final PagamentoVendaRepository pagamentoRepo;
    private final CaixaCedulaRepository    cedulaRepo;

    /** Cédulas e moedas em circulação no Brasil (R$). */
    private static final List<BigDecimal> DENOMINACOES = List.of(
            new BigDecimal("200.00"), new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal("20.00"),
            new BigDecimal("10.00"), new BigDecimal("5.00"), new BigDecimal("2.00"), new BigDecimal("1.00"),
            new BigDecimal("0.50"), new BigDecimal("0.25"), new BigDecimal("0.10"), new BigDecimal("0.05"));

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

    public List<CaixaCedula> listarCedulas(Long caixaId) {
        return cedulaRepo.findByCaixaIdOrderByMomentoAscDenominacaoDesc(caixaId);
    }

    /**
     * Grava a contagem de cédulas/moedas de um momento (ABERTURA ou FECHAMENTO). A contagem é opcional:
     * sem itens não grava nada. Com itens, cada denominação precisa existir e a soma precisa bater com
     * o valor total informado — o servidor não confia no total calculado pela tela.
     *
     * @return resumo para a auditoria (ex.: "2×R$ 50,00, 1×R$ 20,00"), ou null se não houve contagem
     */
    @Transactional
    public String registrarContagem(Long caixaId, String momento, BigDecimal valorTotal, ContagemCedulasDTO contagem) {
        if (contagem == null || contagem.getCedulas() == null) return null;

        Map<BigDecimal, Integer> porDenominacao = new TreeMap<>(java.util.Comparator.reverseOrder());
        for (ContagemCedulasDTO.Item it : contagem.getCedulas()) {
            if (it.getQuantidade() == null || it.getQuantidade() <= 0) continue;
            BigDecimal valor = it.getValor().setScale(2, java.math.RoundingMode.HALF_UP);
            if (DENOMINACOES.stream().noneMatch(d -> d.compareTo(valor) == 0)) {
                throw new BusinessException("Denominação inválida na contagem: " + LimiteCreditoExcedidoException.brl(valor));
            }
            porDenominacao.merge(valor, it.getQuantidade(), Integer::sum);
        }
        if (porDenominacao.isEmpty()) return null;

        BigDecimal soma = BigDecimal.ZERO;
        for (Map.Entry<BigDecimal, Integer> e : porDenominacao.entrySet()) {
            soma = soma.add(e.getKey().multiply(BigDecimal.valueOf(e.getValue())));
        }
        if (soma.compareTo(valorTotal.setScale(2, java.math.RoundingMode.HALF_UP)) != 0) {
            throw new BusinessException(String.format(
                    "A contagem de cédulas (%s) não confere com o valor informado (%s).",
                    LimiteCreditoExcedidoException.brl(soma), LimiteCreditoExcedidoException.brl(valorTotal)));
        }

        List<String> partes = new ArrayList<>();
        for (Map.Entry<BigDecimal, Integer> e : porDenominacao.entrySet()) {
            cedulaRepo.save(CaixaCedula.builder()
                    .caixaId(caixaId).momento(momento).denominacao(e.getKey()).quantidade(e.getValue()).build());
            partes.add(e.getValue() + "×" + LimiteCreditoExcedidoException.brl(e.getKey()));
        }
        return String.join(", ", partes);
    }
}
