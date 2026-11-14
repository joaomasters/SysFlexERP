package com.acougue.modules.industrial.execucao;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.estoque.EstoqueService;
import com.acougue.modules.industrial.execucao.ExecucaoDto.*;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import com.acougue.modules.industrial.pcp.OrdemProducaoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Consumo real de material da OP: cada registro é uma saída de estoque ("SAIDA_CONSUMO_PRODUCAO") com o
 * custo médio do momento; o estorno devolve o material com o mesmo custo. Só materiais da OP (os que vieram
 * da estrutura na liberação) podem ser consumidos.
 */
@Service
@RequiredArgsConstructor
public class ConsumoService {

    static final List<String> OP_ACEITA_CONSUMO = List.of("LIBERADA", "EM_PRODUCAO", "PAUSADA", "CONCLUIDA");

    private final ConsumoRepository consumoRepo;
    private final OrdemProducaoRepository ordemRepo;
    private final ApontamentoRepository apontamentoRepo;
    private final EstoqueService estoque;
    private final ExecucaoMapper mapper;

    static String documento(Consumo c) {
        return c.getOrdem().getNumero() + "/CS" + c.getId();
    }

    @Transactional
    public ConsumoOut consumir(Long ordemId, ConsumoIn in, Long usuarioId) {
        Optional<Consumo> repetido = consumoRepo.findByChave(in.chave());
        if (repetido.isPresent()) return mapper.consumo(repetido.get(), mapper.nomes(List.of(usuarioId)));

        OrdemProducao ordem = ordemRepo.findById(ordemId).orElseThrow(() -> new EntityNotFoundException("OP não encontrada: " + ordemId));
        if (!OP_ACEITA_CONSUMO.contains(ordem.getStatus())) {
            throw new BusinessException("A OP " + ordem.getNumero() + " não aceita consumo (" + ordem.getStatus().toLowerCase() + ").");
        }
        Produto produto = ordem.getMateriais().stream().map(m -> m.getProduto())
                .filter(p -> p.getId().equals(in.produtoId())).findFirst()
                .orElseThrow(() -> new BusinessException("Este material não faz parte da OP " + ordem.getNumero()
                        + ". Para usar um substituto, mude a estrutura e libere uma nova OP."));
        if (in.apontamentoId() != null) {
            Apontamento a = apontamentoRepo.findById(in.apontamentoId())
                    .orElseThrow(() -> new EntityNotFoundException("Apontamento não encontrado: " + in.apontamentoId()));
            if (!a.getOrdem().getId().equals(ordem.getId())) throw new BusinessException("O apontamento é de outra OP.");
        }
        Consumo c = consumoRepo.saveAndFlush(Consumo.builder().chave(in.chave()).ordem(ordem).produto(produto)
                .quantidade(in.quantidade()).custoUnitario(produto.getPrecoCusto())
                .loteMaterial(ParadaService.texto(in.loteMaterial())).apontamentoId(in.apontamentoId())
                .observacao(ParadaService.texto(in.observacao())).usuarioId(usuarioId).build());
        // Sem saldo suficiente o EstoqueService recusa com a mensagem de estoque, e nada é gravado.
        estoque.saida(produto, in.quantidade(), "SAIDA_CONSUMO_PRODUCAO", documento(c), usuarioId);
        return mapper.consumo(c, mapper.nomes(List.of(usuarioId)));
    }

    @Transactional
    public ConsumoOut estornar(Long id, EstornoIn in, Long usuarioId) {
        Consumo c = consumoRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Consumo não encontrado: " + id));
        if (!Boolean.TRUE.equals(c.getEstornado())) {
            OrdemProducao ordem = c.getOrdem();
            if (List.of("ENCERRADA", "CANCELADA").contains(ordem.getStatus())) {
                throw new BusinessException("A OP " + ordem.getNumero() + " está " + ordem.getStatus().toLowerCase() + ".");
            }
            estoque.entrada(c.getProduto(), c.getQuantidade(), c.getCustoUnitario(), "ENTRADA_ESTORNO_CONSUMO",
                    documento(c) + "-EST", usuarioId);
            c.setEstornado(true);
            c.setEstornadoEm(LocalDateTime.now());
            c.setEstornadoPorId(usuarioId);
            c.setMotivoEstorno(in.motivo().trim());
            c = consumoRepo.save(c);
        }
        return mapper.consumo(c, mapper.nomes(List.of(c.getUsuarioId())));
    }
}
