package com.acougue.modules.pdv;

import com.acougue.entity.*;
import com.acougue.exception.BusinessException;
import com.acougue.modules.balanca.EanBalancaParser;
import com.acougue.modules.balanca.dto.EanParseResult;
import com.acougue.modules.estoque.EstoqueService;
import com.acougue.modules.pdv.dto.*;
import com.acougue.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PdvService {

    private final VendaRepository          vendaRepo;
    private final ItensVendaRepository     itensRepo;
    private final PagamentoVendaRepository pagRepo;
    private final ProdutoRepository        produtoRepo;
    private final ClienteRepository        clienteRepo;
    private final CaixaRepository          caixaRepo;
    private final ContasAReceberRepository contasRepo;
    private final EstoqueService estoqueService;
    private final EanBalancaParser         eanParser;
    private final CaixaService             caixaService;

    // ── Caixa ──────────────────────────────────────────────────

    @Transactional
    public Caixa abrirCaixa(Long operadorId, BigDecimal valorAbertura) {
        if (valorAbertura == null || valorAbertura.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Valor de abertura não pode ser negativo.");
        }
        caixaRepo.findFirstByOperadorIdAndStatus(operadorId, "ABERTO")
                .ifPresent(c -> { throw new BusinessException("Operador já possui caixa aberto."); });
        return caixaRepo.save(Caixa.builder()
                .operadorId(operadorId)
                .valorAbertura(valorAbertura)
                .status("ABERTO")
                .build());
    }

    @Transactional
    public Caixa fecharCaixa(Long caixaId, BigDecimal valorInformado) {
        if (valorInformado == null || valorInformado.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Valor informado não pode ser negativo.");
        }

        Caixa caixa = caixaRepo.findById(caixaId)
                .orElseThrow(() -> new EntityNotFoundException("Caixa não encontrado: " + caixaId));
        if (!"ABERTO".equals(caixa.getStatus())) {
            throw new BusinessException("Caixa já está fechado.");
        }

        // Sem essa trava, uma comanda em andamento (ainda ABERTA) some do
        // sistema pra sempre: ao fechar o caixa, nada mais aponta pra ela —
        // a tela de PDV só lista comandas do caixa ABERTO do operador atual.
        List<Venda> abertas = vendaRepo.findByCaixaIdAndStatus(caixaId, "ABERTA");
        if (!abertas.isEmpty()) {
            throw new BusinessException(String.format(
                    "Existe(m) %d comanda(s) em aberto nesse caixa. Finalize ou cancele antes de fechar.",
                    abertas.size()));
        }

        // Registra, no momento do fechamento, o saldo que o sistema esperava
        // encontrar — sem isso valorCalculado ficava sempre nulo e não dava
        // pra auditar depois se sobrou/faltou dinheiro na gaveta.
        BigDecimal saldoEsperado = caixaService.calcularFechamento(caixaId).getSaldoEsperado();

        caixa.setValorFechamentoInformado(valorInformado);
        caixa.setValorCalculado(saldoEsperado);
        caixa.setDataFechamento(LocalDateTime.now());
        caixa.setStatus("FECHADO");
        return caixaRepo.save(caixa);
    }

    // ── Venda ──────────────────────────────────────────────────

    @Transactional
    public Venda abrirVenda(AbrirVendaDTO dto) {
        Caixa caixa = caixaRepo.findById(dto.getCaixaId())
                .orElseThrow(() -> new EntityNotFoundException("Caixa não encontrado: " + dto.getCaixaId()));
        if (!"ABERTO".equals(caixa.getStatus())) {
            throw new BusinessException("Caixa está fechado. Abra um caixa antes de vender.");
        }
        Cliente cliente = dto.getClienteId() != null
                ? clienteRepo.findById(dto.getClienteId()).orElse(null)
                : null;

        Venda venda = Venda.builder()
                .tipoVenda("PDV")
                .status("ABERTA")
                .caixa(caixa)
                .cliente(cliente)
                .operadorId(dto.getOperadorId())
                .subtotal(BigDecimal.ZERO)
                .desconto(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .troco(BigDecimal.ZERO)
                .build();
        return vendaRepo.save(venda);
    }

    /**
     * Decodifica o barcode e retorna ItemVendaDTO pronto para adicionar ao cupom.
     */
    public ItemVendaDTO processarBarcode(String ean13) {
        if (ean13.startsWith("2") && ean13.length() == 13) {
            return processarEanBalanca(ean13);
        }
        return processarEanPadrao(ean13);
    }

    @Transactional
    public Venda adicionarItem(Long vendaId, ItemVendaDTO dto) {
        Venda venda = buscarVendaAberta(vendaId);
        Produto produto = produtoRepo.findById(dto.getProdutoId())
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + dto.getProdutoId()));

        BigDecimal total = dto.getTotalItem() != null
                ? dto.getTotalItem()
                : dto.getQuantidade().multiply(produto.getPrecoVenda()).setScale(2, RoundingMode.HALF_UP);

        ItensVenda item = ItensVenda.builder()
                .venda(venda)
                .produto(produto)
                .ean13Lido(dto.getEan13Lido())
                .quantidade(dto.getQuantidade())
                .precoUnitario(produto.getPrecoVenda())
                .descontoItem(BigDecimal.ZERO)
                .totalItem(total)
                .custoItem(produto.getPrecoCusto())
                .tipoEntrada(dto.getTipoEntrada())
                .build();
        itensRepo.save(item);

        recalcularTotais(venda);
        return vendaRepo.save(venda);
    }

    @Transactional
    public Venda removerItem(Long vendaId, Long itemId) {
        Venda venda = buscarVendaAberta(vendaId);
        itensRepo.deleteById(itemId);
        recalcularTotais(venda);
        return vendaRepo.save(venda);
    }

    @Transactional
    public Venda fecharVenda(FecharVendaDTO dto) {
        Venda venda = buscarVendaAberta(dto.getVendaId());

        BigDecimal totalPago = dto.getPagamentos().stream()
                .map(PagamentoDTO::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPago.compareTo(venda.getTotal()) < 0) {
            throw new BusinessException(String.format(
                    "Pagamento insuficiente. Venda: R$ %.2f | Pago: R$ %.2f",
                    venda.getTotal(), totalPago));
        }

        for (PagamentoDTO pag : dto.getPagamentos()) {
            PagamentoVenda pagVenda = PagamentoVenda.builder()
                    .venda(venda)
                    .formaPagamento(pag.getFormaPagamento())
                    .valor(pag.getValor())
                    .nsu(pag.getNsu())
                    .autorizacao(pag.getAutorizacao())
                    .build();
            pagRepo.save(pagVenda);

            if ("FIADO".equals(pag.getFormaPagamento())) {
                lancarContaAReceber(venda, pag.getValor());
            }
        }

        // Baixar estoque de cada item vendido
        List<ItensVenda> itens = itensRepo.findByVendaId(venda.getId());
        for (ItensVenda item : itens) {
            estoqueService.saida(item.getProduto(), item.getQuantidade(),
                    "SAIDA_VENDA", "VENDA#" + venda.getId(), venda.getOperadorId());
        }

        BigDecimal troco = totalPago.subtract(venda.getTotal()).max(BigDecimal.ZERO);
        venda.setTroco(troco);
        venda.setStatus("FECHADA");

        return vendaRepo.save(venda);
    }

    @Transactional
    public Venda cancelarVenda(Long vendaId) {
        Venda venda = buscarVendaAberta(vendaId);
        venda.setStatus("CANCELADA");
        return vendaRepo.save(venda);
    }

    /**
     * Lista todas as comandas (vendas com status ABERTA) de um caixa —
     * usado pela tela de PDV para mostrar as comandas abertas simultaneamente
     * e permitir alternar entre elas.
     */
    public List<Venda> listarVendasAbertas(Long caixaId) {
        return vendaRepo.findByCaixaIdAndStatus(caixaId, "ABERTA");
    }

    /**
     * Busca uma comanda específica em aberto — usado quando o operador clica
     * numa comanda da lista para retomar/continuar aquela venda.
     */
    public Venda buscarComanda(Long vendaId) {
        return buscarVendaAberta(vendaId);
    }

    // ── Privados ───────────────────────────────────────────────

    private ItemVendaDTO processarEanBalanca(String ean13) {
        int codigoBalanca = Integer.parseInt(ean13.substring(1, 6));
        Produto produto = produtoRepo.findByCodigoBalanca(codigoBalanca)
                .orElseThrow(() -> new EntityNotFoundException("Produto não cadastrado para PLU: " + codigoBalanca));

        EanParseResult result = eanParser.parse(ean13, produto.getPrecoVenda());

        BigDecimal total = result.getValorTotal() != null
                ? result.getValorTotal()
                : result.getPesoKg().multiply(produto.getPrecoVenda()).setScale(2, RoundingMode.HALF_UP);

        return ItemVendaDTO.builder()
                .produtoId(produto.getId())
                .nomeProduto(produto.getNome())
                .unidadeMedida(produto.getUnidadeMedida())
                .quantidade(result.getPesoKg())
                .precoUnitario(produto.getPrecoVenda())
                .totalItem(total)
                .tipoEntrada("BARCODE")
                .ean13Lido(ean13)
                .build();
    }

    private ItemVendaDTO processarEanPadrao(String ean13) {
        Produto produto = produtoRepo.findByEan13(ean13)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + ean13));

        return ItemVendaDTO.builder()
                .produtoId(produto.getId())
                .nomeProduto(produto.getNome())
                .unidadeMedida(produto.getUnidadeMedida())
                .quantidade(BigDecimal.ONE)
                .precoUnitario(produto.getPrecoVenda())
                .totalItem(produto.getPrecoVenda())
                .tipoEntrada("BARCODE")
                .ean13Lido(ean13)
                .build();
    }

    private void recalcularTotais(Venda venda) {
        BigDecimal subtotal = itensRepo.findByVendaId(venda.getId()).stream()
                .map(ItensVenda::getTotalItem)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        venda.setSubtotal(subtotal);
        venda.setTotal(subtotal.subtract(venda.getDesconto()).setScale(2, RoundingMode.HALF_UP));
    }

    private void lancarContaAReceber(Venda venda, BigDecimal valor) {
        if (venda.getCliente() == null) {
            throw new BusinessException("Pagamento FIADO exige cliente identificado na venda.");
        }
        ContasAReceber conta = ContasAReceber.builder()
                .cliente(venda.getCliente())
                .venda(venda)
                .descricao("Fiado — Venda #" + venda.getId())
                .valor(valor)
                .status("ABERTO")
                .build();
        contasRepo.save(conta);
    }

    private Venda buscarVendaAberta(Long id) {
        return vendaRepo.findById(id)
                .filter(v -> "ABERTA".equals(v.getStatus()))
                .orElseThrow(() -> new EntityNotFoundException("Venda não encontrada ou já encerrada: " + id));
    }
}