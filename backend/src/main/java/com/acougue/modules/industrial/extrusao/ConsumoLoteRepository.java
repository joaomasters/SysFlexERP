package com.acougue.modules.industrial.extrusao;

import com.acougue.modules.industrial.execucao.Consumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/** Consultas de consumo por lote de matéria-prima: o saldo do lote é a quantidade recebida menos o consumido. */
public interface ConsumoLoteRepository extends JpaRepository<Consumo, Long> {
    @Query("SELECT COALESCE(SUM(c.quantidade), 0) FROM Consumo c WHERE c.loteMpId = :loteId AND c.estornado = FALSE")
    BigDecimal consumidoDoLote(@Param("loteId") Long loteId);

    @Query("SELECT c.loteMpId, COALESCE(SUM(c.quantidade), 0) FROM Consumo c WHERE c.loteMpId IN :ids AND c.estornado = FALSE "
            + "GROUP BY c.loteMpId")
    List<Object[]> consumidoDosLotes(@Param("ids") Collection<Long> ids);

    /** Consumos válidos de uma OP com o lote (para o rendimento e a rastreabilidade). */
    @Query("SELECT c FROM Consumo c JOIN FETCH c.produto WHERE c.ordem.id = :ordemId AND c.estornado = FALSE")
    List<Consumo> validosDaOrdem(@Param("ordemId") Long ordemId);

    /** Consumos válidos de um lote de matéria-prima, com a OP (rastreabilidade para frente). */
    @Query("SELECT c FROM Consumo c JOIN FETCH c.ordem WHERE c.loteMpId = :loteMpId AND c.estornado = FALSE")
    List<Consumo> validosDoLoteMp(@Param("loteMpId") Long loteMpId);
}
