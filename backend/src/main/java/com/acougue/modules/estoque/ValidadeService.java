package com.acougue.modules.estoque;

import com.acougue.entity.LoteEstoque;
import com.acougue.modules.estoque.dto.LoteValidadeDTO;
import com.acougue.repository.LoteEstoqueRepository;
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
                lote.getDocumentoRef());
    }
}
