package com.acougue.modules.industrial.expedicao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface ExpedicaoRepository extends JpaRepository<Expedicao, Long> {
    @Query("SELECT e FROM Expedicao e JOIN FETCH e.cliente WHERE (:status IS NULL OR e.status = :status) ORDER BY e.id DESC")
    List<Expedicao> listar(@Param("status") String status);

    @Query(value = "SELECT nextval('ind_expedicao_numero_seq')", nativeQuery = true)
    long proximoNumero();

    /** Quanto do lote está reservado em expedições ainda não carregadas nem canceladas. */
    @Query("SELECT COALESCE(SUM(r.quantidade), 0) FROM ExpedicaoReserva r "
            + "WHERE r.lote.id = :loteId AND r.item.expedicao.status IN ('ABERTA', 'CONFERIDA')")
    BigDecimal reservadoDoLote(@Param("loteId") Long loteId);

    @Query("SELECT r.lote.id, COALESCE(SUM(r.quantidade), 0) FROM ExpedicaoReserva r "
            + "WHERE r.lote.id IN :ids AND r.item.expedicao.status IN ('ABERTA', 'CONFERIDA') GROUP BY r.lote.id")
    List<Object[]> reservadoDosLotes(@Param("ids") Collection<Long> ids);

    /** Reservas carregadas de uma lista de lotes: a ponta "cliente" da rastreabilidade. */
    @Query("SELECT r FROM ExpedicaoReserva r JOIN FETCH r.item i JOIN FETCH i.expedicao e JOIN FETCH e.cliente "
            + "WHERE r.lote.id IN :ids AND e.status = 'CARREGADA'")
    List<ExpedicaoItem.Reserva> carregadasDosLotes(@Param("ids") Collection<Long> ids);
}
