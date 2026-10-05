package com.acougue.modules.fiscal;

import com.acougue.entity.Cliente;
import com.acougue.entity.NotaFiscalSaida;
import com.acougue.entity.NotaFiscalSaidaItem;
import com.acougue.entity.Produto;
import com.acougue.modules.estoque.EstoqueService;
import com.acougue.modules.fiscal.dto.NotaFiscalSaidaDTO;
import com.acougue.repository.ClienteRepository;
import com.acougue.repository.NotaFiscalSaidaRepository;
import com.acougue.repository.ProdutoRepository;
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
public class NotaFiscalService {

    private final NotaFiscalSaidaRepository notaRepo;
    private final ClienteRepository         clienteRepo;
    private final ProdutoRepository         produtoRepo;
    private final EstoqueService estoqueService;

    public List<NotaFiscalSaida> listar() {
        return notaRepo.findAllByOrderByCreatedAtDesc();
    }

    /** Listagem filtrada por período de emissão e, opcionalmente, nome do cliente (busca parcial). */
    public List<NotaFiscalSaida> listar(LocalDateTime inicio, LocalDateTime fim, String cliente) {
        return notaRepo.buscarComFiltros(inicio, fim, cliente == null ? "" : cliente.trim());
    }

    public NotaFiscalSaida buscar(Long id) {
        return notaRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("NF não encontrada: " + id));
    }

    @Transactional
    public NotaFiscalSaida criar(NotaFiscalSaidaDTO dto, Long usuarioId) {
        Cliente cliente = dto.clienteId() != null
                ? clienteRepo.findById(dto.clienteId())
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado: " + dto.clienteId()))
                : null;

        NotaFiscalSaida nf = NotaFiscalSaida.builder()
                .usuarioCriacaoId(usuarioId)
                .cliente(cliente)
                .numeroNf(dto.numeroNf())
                .serieNf(dto.serieNf() != null ? dto.serieNf() : "1")
                .naturezaOperacao(dto.naturezaOperacao() != null ? dto.naturezaOperacao() : "VENDA DE MERCADORIAS")
                .observacao(dto.observacao())
                .status("PENDENTE")
                .build();

        BigDecimal totalProdutos = BigDecimal.ZERO;

        if (dto.itens() != null) {
            for (NotaFiscalSaidaDTO.ItemDTO item : dto.itens()) {
                Produto produto = item.produtoId() != null
                        ? produtoRepo.findById(item.produtoId()).orElse(null)
                        : null;

                BigDecimal total = item.valorUnitario().multiply(item.quantidade())
                        .setScale(2, RoundingMode.HALF_UP);
                totalProdutos = totalProdutos.add(total);

                String desc = item.descricao() != null ? item.descricao()
                        : (produto != null ? produto.getNome() : "Item");

                NotaFiscalSaidaItem nfItem = NotaFiscalSaidaItem.builder()
                        .nota(nf)
                        .produto(produto)
                        .descricao(desc)
                        .quantidade(item.quantidade())
                        .valorUnitario(item.valorUnitario())
                        .valorTotal(total)
                        .build();
                nf.getItens().add(nfItem);
            }
        }

        nf.setValorProdutos(totalProdutos);
        nf.setValorDesconto(BigDecimal.ZERO);
        nf.setValorTotal(totalProdutos);

        return notaRepo.save(nf);
    }

    @Transactional
    public NotaFiscalSaida atualizarStatus(Long id, String status, Long usuarioId) {
        NotaFiscalSaida nf = buscar(id);
        aplicarTransicaoDeEstoque(nf, status, usuarioId);
        nf.setStatus(status);
        return notaRepo.save(nf);
    }

    @Transactional
    public NotaFiscalSaida uploadXml(Long id, String xml, Long usuarioId) {
        NotaFiscalSaida nf = buscar(id);
        nf.setXmlNf(xml);
        if ("PENDENTE".equals(nf.getStatus())) {
            aplicarTransicaoDeEstoque(nf, "EMITIDA", usuarioId);
            nf.setStatus("EMITIDA");
        }
        return notaRepo.save(nf);
    }

    /*
     Movimenta o estoque conforme a mudança de status da NF de saída:
     vira EMITIDA (e não estava antes) → baixa o estoque de cada item vinculado a um produto
     estava EMITIDA e vira CANCELADA → devolve a quantidade ao estoque, SEM alterar o
     custo médio (a mercadoria nunca foi "comprada" de novo, só voltou pra prateleira)
     Itens sem produto vinculado (texto livre na nota) são ignorados — não há estoque a controlar.

     O usuário que efetiva a saída (vira EMITIDA) fica registrado na nota e também nas
     movimentações de estoque geradas; no estorno, as movimentações levam quem cancelou.
     */
    private void aplicarTransicaoDeEstoque(NotaFiscalSaida nf, String novoStatus, Long usuarioId) {
        String statusAnterior = nf.getStatus();
        if (statusAnterior.equals(novoStatus)) return;

        boolean vaiEmitir   = "EMITIDA".equals(novoStatus) && !"EMITIDA".equals(statusAnterior);
        boolean vaiCancelar = "CANCELADA".equals(novoStatus) && "EMITIDA".equals(statusAnterior);

        if (vaiEmitir) {
            nf.setUsuarioEmissaoId(usuarioId);
            for (NotaFiscalSaidaItem item : nf.getItens()) {
                if (item.getProduto() == null) continue;
                estoqueService.saida(item.getProduto(), item.getQuantidade(),
                        "SAIDA_NF", "NF#" + nf.getId(), usuarioId);
            }
        } else if (vaiCancelar) {
            for (NotaFiscalSaidaItem item : nf.getItens()) {
                if (item.getProduto() == null) continue;
                estoqueService.entrada(item.getProduto(), item.getQuantidade(), null,
                        "ENTRADA_ESTORNO_NF", "NF#" + nf.getId(), usuarioId);
            }
        }
    }
}