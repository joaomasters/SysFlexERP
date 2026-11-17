package com.acougue.modules.industrial.qualidade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LoteGenealogiaRepository extends JpaRepository<LoteGenealogia, Long> {
    @Query("SELECT g FROM LoteGenealogia g JOIN FETCH g.lotePai WHERE g.lote.id = :loteId")
    List<LoteGenealogia> pais(@Param("loteId") Long loteId);

    @Query("SELECT g FROM LoteGenealogia g JOIN FETCH g.lote WHERE g.lotePai.id = :loteId")
    List<LoteGenealogia> filhos(@Param("loteId") Long loteId);

    boolean existsByLotePaiId(Long lotePaiId);
}
