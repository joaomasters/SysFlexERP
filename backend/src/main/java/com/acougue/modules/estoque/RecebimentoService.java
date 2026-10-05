package com.acougue.modules.estoque;

import com.acougue.entity.Produto;
import com.acougue.entity.RecebimentoItem;
import com.acougue.entity.RecebimentoMercadoria;
import com.acougue.modules.estoque.dto.RecebimentoDTO;
import com.acougue.repository.ProdutoRepository;
import com.acougue.repository.RecebimentoRepository;
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
public class RecebimentoService {

    private final RecebimentoRepository recebimentoRepo;
    private final ProdutoRepository     produtoRepo;
    private final EstoqueService        estoqueService;

    public List<RecebimentoMercadoria> listar() {
        return recebimentoRepo.findAllByOrderByCreatedAtDesc();
    }

    /** Listagem filtrada por período de entrada e, opcionalmente, fornecedor (busca parcial). */
    public List<RecebimentoMercadoria> listar(LocalDateTime inicio, LocalDateTime fim, String fornecedor) {
        return recebimentoRepo.buscarComFiltros(inicio, fim, fornecedor == null ? "" : fornecedor.trim());
    }

    public RecebimentoMercadoria buscar(Long id) {
        return recebimentoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Recebimento não encontrado: " + id));
    }

    @Transactional
    public RecebimentoMercadoria registrar(RecebimentoDTO dto, Long usuarioId) {
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

            String docRef = "NF" + (dto.numeroNf() != null ? dto.numeroNf() : "S/N");
            estoqueService.entrada(produto, item.quantidade(), custoUnit,
                    "ENTRADA_COMPRA", docRef, usuarioId, item.dataValidade());
        }

        return recebimentoRepo.save(rec);
    }

    @Transactional
    public RecebimentoMercadoria uploadXml(Long id, String xml) {
        RecebimentoMercadoria rec = buscar(id);
        rec.setXmlNf(xml);
        return recebimentoRepo.save(rec);
    }
}