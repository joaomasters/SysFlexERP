package com.acougue.modules.estoque;

import com.acougue.entity.LoteEstoque;
import com.acougue.exception.BusinessException;
import com.acougue.modules.estoque.dto.LoteValidadeDTO;
import com.acougue.repository.LoteEstoqueRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Consulta de validade dos lotes em estoque e geração dos alertas (sino de notificações).
 *
 * Janela de alerta: lote que vence em até {@code estoque.validade.dias-alerta} dias (padrão 3)
 * ou que já venceu. Configurável no application.yml sem mexer em código.
 */
@Slf4j
@Service
public class ValidadeService {

    private final LoteEstoqueRepository loteRepo;
    private final int diasAlerta;

    public ValidadeService(LoteEstoqueRepository loteRepo,
                           @Value("${estoque.validade.dias-alerta:3}") int diasAlerta) {
        this.loteRepo = loteRepo;
        this.diasAlerta = diasAlerta;
    }

    /** Todos os lotes com saldo, do que vence primeiro para o que vence depois. */
    @Transactional(readOnly = true)
    public List<LoteValidadeDTO> listar() {
        return loteRepo.findAllDisponiveis().stream().map(this::paraDto).toList();
    }

    /** Só o que exige atenção: vencidos e vencendo dentro da janela de alerta. */
    @Transactional(readOnly = true)
    public List<LoteValidadeDTO> alertas() {
        return listar().stream()
                .filter(l -> !LoteValidadeDTO.OK.equals(l.situacao()))
                .toList();
    }

    /**
     * Corrige a validade de um lote já cadastrado (ex: data digitada errada no recebimento).
     * Vale a mesma regra da entrada: no máximo {@value RegraValidade#MAX_ANOS} anos a partir
     * do dia em que o lote entrou. Como a baixa é FEFO, mudar a validade muda a ordem em que
     * os lotes do produto são consumidos — é o comportamento esperado.
     */
    @Transactional
    public LoteValidadeDTO editarValidade(Long loteId, LocalDate novaValidade) {
        if (novaValidade == null) {
            throw new BusinessException("Informe a nova data de validade.");
        }
        LoteEstoque lote = loteRepo.findById(loteId)
                .orElseThrow(() -> new EntityNotFoundException("Lote não encontrado: " + loteId));

        RegraValidade.validarLimite(novaValidade, dataEntrada(lote));

        LocalDate anterior = lote.getDataValidade();
        lote.setDataValidade(novaValidade);
        loteRepo.save(lote);
        log.info("[Validade] Lote #{} (produto #{}): validade {} -> {}",
                loteId, lote.getProduto().getId(), anterior, novaValidade);
        return paraDto(lote);
    }

    /** Dia em que o lote entrou no estoque (lotes sem registro de criação usam hoje). */
    private static LocalDate dataEntrada(LoteEstoque lote) {
        return lote.getCreatedAt() != null ? lote.getCreatedAt().toLocalDate() : LocalDate.now();
    }

    private LoteValidadeDTO paraDto(LoteEstoque lote) {
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), lote.getDataValidade());
        String situacao = dias < 0 ? LoteValidadeDTO.VENCIDO
                : dias <= diasAlerta ? LoteValidadeDTO.VENCE_EM_BREVE
                : LoteValidadeDTO.OK;
        return new LoteValidadeDTO(
                lote.getId(),
                lote.getProduto().getId(),
                lote.getProduto().getNome(),
                lote.getProduto().getUnidadeMedida(),
                lote.getQuantidadeAtual(),
                lote.getDataValidade(),
                dias,
                situacao,
                lote.getDocumentoRef(),
                dataEntrada(lote));
    }
}