package com.acougue.modules.estoque;

import com.acougue.entity.InventarioFisico;
import com.acougue.entity.InventarioFisicoItem;
import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.repository.InventarioFisicoItemRepository;
import com.acougue.repository.InventarioFisicoRepository;
import com.acougue.repository.ProdutoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventarioService {

    private final InventarioFisicoRepository     inventarioRepo;
    private final InventarioFisicoItemRepository itemRepo;
    private final ProdutoRepository              produtoRepo;
    private final EstoqueService                 estoqueService;

    @Transactional
    public InventarioFisico abrirInventario(Long usuarioId, String observacao) {
        inventarioRepo.findFirstByStatusOrderByCreatedAtDesc("ABERTO").ifPresent(i -> {
            throw new BusinessException("Já existe um inventário aberto (ID " + i.getId() + "). Finalize-o antes de abrir outro.");
        });

        InventarioFisico inventario = InventarioFisico.builder()
                .usuarioId(usuarioId)
                .observacao(observacao)
                .dataInicio(LocalDateTime.now())
                .build();
        inventario = inventarioRepo.save(inventario);

        List<Produto> produtos = produtoRepo.findAllByAtivoTrue();
        for (Produto p : produtos) {
            itemRepo.save(InventarioFisicoItem.builder()
                    .inventario(inventario)
                    .produto(p)
                    .saldoSistema(p.getEstoqueAtual())
                    .build());
        }
        return inventarioRepo.findById(inventario.getId()).orElseThrow();
    }

    @Transactional
    public InventarioFisicoItem contarItem(Long inventarioId, Long produtoId, BigDecimal saldoContado) {
        InventarioFisicoItem item = itemRepo.findByInventarioIdAndProdutoId(inventarioId, produtoId)
                .orElseThrow(() -> new EntityNotFoundException("Item não encontrado no inventário"));
        item.setSaldoContado(saldoContado);
        item.setDivergencia(saldoContado.subtract(item.getSaldoSistema()));
        return itemRepo.save(item);
    }

    @Transactional
    public InventarioFisico finalizarInventario(Long inventarioId, Long usuarioId) {
        InventarioFisico inventario = inventarioRepo.findById(inventarioId)
                .orElseThrow(() -> new EntityNotFoundException("Inventário não encontrado: " + inventarioId));
        if (!"ABERTO".equals(inventario.getStatus())) {
            throw new BusinessException("Inventário não está aberto.");
        }

        List<InventarioFisicoItem> itens = itemRepo.findByInventarioIdOrderByProdutoNome(inventarioId);
        for (InventarioFisicoItem item : itens) {
            if (item.getSaldoContado() != null && item.getDivergencia() != null
                    && item.getDivergencia().compareTo(BigDecimal.ZERO) != 0) {
                String tipo = item.getDivergencia().compareTo(BigDecimal.ZERO) > 0
                        ? "AJUSTE_POSITIVO" : "AJUSTE_NEGATIVO";
                estoqueService.ajuste(item.getProduto(), item.getDivergencia().abs(),
                        tipo, "INVENTARIO#" + inventarioId, usuarioId);
            }
        }

        inventario.setStatus("FINALIZADO");
        inventario.setDataFim(LocalDateTime.now());
        inventario.setFechadoPorId(usuarioId);
        return inventarioRepo.save(inventario);
    }

    @Transactional
    public InventarioFisico cancelarInventario(Long inventarioId, Long usuarioId) {
        InventarioFisico inventario = inventarioRepo.findById(inventarioId)
                .orElseThrow(() -> new EntityNotFoundException("Inventário não encontrado: " + inventarioId));
        if (!"ABERTO".equals(inventario.getStatus())) {
            throw new BusinessException("Somente inventários abertos podem ser cancelados.");
        }
        inventario.setStatus("CANCELADO");
        inventario.setFechadoPorId(usuarioId);
        return inventarioRepo.save(inventario);
    }

    public List<InventarioFisico> listarTodos() {
        return inventarioRepo.findAllByOrderByCreatedAtDesc();
    }

    public List<InventarioFisico> listarPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        return inventarioRepo.buscarPorPeriodo(inicio, fim);
    }

    public List<InventarioFisicoItem> listarItens(Long inventarioId) {
        return itemRepo.findByInventarioIdOrderByProdutoNome(inventarioId);
    }

    public InventarioFisico buscarPorId(Long id) {
        return inventarioRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Inventário não encontrado: " + id));
    }
}