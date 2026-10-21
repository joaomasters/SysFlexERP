package com.acougue.modules.estoque;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.entity.RecebimentoItem;
import com.acougue.entity.RecebimentoMercadoria;
import com.acougue.modules.estoque.dto.RecebimentoDTO;
import com.acougue.repository.ProdutoRepository;
import com.acougue.repository.RecebimentoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecebimentoService {

    private final RecebimentoRepository recebimentoRepo;
    private final ProdutoRepository     produtoRepo;
    private final EstoqueService        estoqueService;

    /** Dias para cancelar uma entrada (7 = prazo adotado no Paraná). */
    @Value("${recebimento.prazo-cancelamento-dias:7}")
    private int prazoCancelamentoDias = 7;

    public List<RecebimentoMercadoria> listar() {
        return preencherPrazo(recebimentoRepo.findAllByOrderByCreatedAtDesc());
    }

    /** Listagem filtrada por período de entrada e, opcionalmente, fornecedor (busca parcial). */
    public List<RecebimentoMercadoria> listar(LocalDateTime inicio, LocalDateTime fim, String fornecedor) {
        return preencherPrazo(recebimentoRepo.buscarComFiltros(inicio, fim, fornecedor == null ? "" : fornecedor.trim()));
    }

    public RecebimentoMercadoria buscar(Long id) {
        RecebimentoMercadoria rec = recebimentoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Recebimento não encontrado: " + id));
        preencherPrazo(List.of(rec));
        return rec;
    }

    @Transactional
    public RecebimentoMercadoria registrar(RecebimentoDTO dto, Long usuarioId) {
        // Validade acima de 3 anos da entrada quase sempre é erro de digitação:
        // barra antes de gravar qualquer coisa.
        LocalDate dataEntrada = LocalDate.now();
        for (RecebimentoDTO.ItemDTO item : dto.itens()) {
            RegraValidade.validarLimite(item.dataValidade(), dataEntrada);
        }

        RecebimentoMercadoria rec = RecebimentoMercadoria.builder()
                .usuarioId(usuarioId)
                .fornecedor(dto.fornecedor())
                .numeroNf(dto.numeroNf())
                .serieNf(dto.serieNf() != null ? dto.serieNf() : "1")
                .chaveNf(dto.chaveNf())
                .dataEmissao(dto.dataEmissao())
                .valorTotal(dto.valorTotal())
                .observacao(dto.observacao())
                .xmlNf(dto.xmlNf())
                .status("CONFERIDO")
                .build();
        rec = recebimentoRepo.save(rec);   // id já agora: identifica os lotes desta entrada
        String docRef = docRefEntrada(rec);

        for (RecebimentoDTO.ItemDTO item : dto.itens()) {
            Produto produto = produtoRepo.findById(item.produtoId())
                    .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + item.produtoId()));

            BigDecimal custoUnit = item.custoUnitario() != null ? item.custoUnitario() : BigDecimal.ZERO;
            BigDecimal custoTotal = custoUnit.multiply(item.quantidade()).setScale(4, RoundingMode.HALF_UP);

            RecebimentoItem ri = RecebimentoItem.builder()
                    .recebimento(rec)
                    .produto(produto)
                    .quantidade(item.quantidade())
                    .custoUnitario(custoUnit)
                    .custoTotal(custoTotal)
                    .dataValidade(item.dataValidade())
                    .build();
            rec.getItens().add(ri);

            estoqueService.entrada(produto, item.quantidade(), custoUnit,
                    "ENTRADA_COMPRA", docRef, usuarioId, item.dataValidade());
        }

        return preencherPrazo(List.of(recebimentoRepo.save(rec))).get(0);
    }

    // ── Cancelamento ───────────────────────────────────────────

    /**
     * Cancela a entrada dentro do prazo: estorna o estoque de cada item (custo
     * médio e lote inclusos) e marca o recebimento como CANCELADO, guardando
     * quem, quando e por quê. Não apaga nada — o histórico continua auditável.
     * Se algum produto já teve saída depois da entrada, nada é estornado.
     */
    @Transactional
    public RecebimentoMercadoria cancelar(Long id, String motivo, Long usuarioId) {
        RecebimentoMercadoria rec = recebimentoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Recebimento não encontrado: " + id));
        if (STATUS_CANCELADO.equals(rec.getStatus())) {
            throw new BusinessException("Esta entrada já foi cancelada.");
        }
        if (motivo == null || motivo.trim().length() < 5) {
            throw new BusinessException("Informe o motivo do cancelamento (mínimo 5 caracteres).");
        }
        LocalDateTime limite = limiteCancelamento(rec);
        if (LocalDateTime.now().isAfter(limite)) {
            throw new BusinessException("Prazo de cancelamento encerrado: entradas só podem ser canceladas em até "
                    + prazoCancelamentoDias + " dias (limite era " + limite.toLocalDate() + ").");
        }
        conferirSaldoParaEstorno(rec);

        List<String> refsEntrada = List.of(docRefEntrada(rec), docRefLegado(rec));
        String refEstorno = "CANC-REC" + rec.getId();
        for (RecebimentoItem item : rec.getItens()) {
            estoqueService.estornarEntrada(item.getProduto(), item.getQuantidade(), item.getCustoUnitario(),
                    refsEntrada, refEstorno, usuarioId);
        }
        rec.setStatus(STATUS_CANCELADO);
        rec.setCanceladoEm(LocalDateTime.now());
        rec.setCanceladoPorId(usuarioId);
        rec.setMotivoCancelamento(motivo.trim());
        return preencherPrazo(List.of(recebimentoRepo.save(rec))).get(0);
    }

    /** Confere TODOS os produtos antes de mexer em qualquer um, e lista os que não têm saldo. */
    private void conferirSaldoParaEstorno(RecebimentoMercadoria rec) {
        java.util.Map<Produto, BigDecimal> porProduto = new java.util.LinkedHashMap<>();
        for (RecebimentoItem item : rec.getItens()) {
            porProduto.merge(item.getProduto(), item.getQuantidade(), BigDecimal::add);
        }
        List<String> semSaldo = porProduto.entrySet().stream()
                .filter(e -> e.getKey().getEstoqueAtual().compareTo(e.getValue()) < 0)
                .map(e -> String.format("%s (entrada %.3f, estoque atual %.3f)", e.getKey().getNome(),
                        e.getValue(), e.getKey().getEstoqueAtual()))
                .toList();
        if (!semSaldo.isEmpty()) {
            throw new BusinessException("Não é possível cancelar: parte da mercadoria já saiu do estoque — "
                    + String.join("; ", semSaldo) + ". Corrija pelo Inventário.");
        }
    }

    LocalDateTime limiteCancelamento(RecebimentoMercadoria rec) {
        LocalDateTime base = rec.getDataRecebimento() != null ? rec.getDataRecebimento() : rec.getCreatedAt();
        return base.plusDays(prazoCancelamentoDias);
    }

    private List<RecebimentoMercadoria> preencherPrazo(List<RecebimentoMercadoria> recs) {
        for (RecebimentoMercadoria r : recs) {
            boolean cancelavel = !STATUS_CANCELADO.equals(r.getStatus())
                    && (r.getDataRecebimento() != null || r.getCreatedAt() != null);
            r.setCancelavelAte(cancelavel ? limiteCancelamento(r) : null);
        }
        return recs;
    }

    static final String STATUS_CANCELADO = "CANCELADO";

    /** Documento das entradas novas: único por recebimento (o número da NF pode repetir ou faltar). */
    static String docRefEntrada(RecebimentoMercadoria rec) {
        return docRefLegado(rec) + "/REC" + rec.getId();
    }

    /** Formato usado antes desta versão — para cancelar entradas antigas. */
    static String docRefLegado(RecebimentoMercadoria rec) {
        return "NF" + (rec.getNumeroNf() != null ? rec.getNumeroNf() : "S/N");
    }

    @Transactional
    public RecebimentoMercadoria uploadXml(Long id, String xml) {
        RecebimentoMercadoria rec = buscar(id);
        rec.setXmlNf(xml);
        return recebimentoRepo.save(rec);
    }
}