package com.acougue.modules.industrial.pcp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface OrdemProducaoRepository extends JpaRepository<OrdemProducao, Long> {
    @Query("SELECT o FROM OrdemProducao o JOIN FETCH o.produto LEFT JOIN FETCH o.cliente "
            + "WHERE (:status IS NULL OR o.status = :status) ORDER BY o.id DESC")
    List<OrdemProducao> listar(@Param("status") String status);

    /** OPs abertas cujo período previsto encosta em [de, ate]. */
    @Query("SELECT o FROM OrdemProducao o JOIN FETCH o.produto WHERE o.status IN :status "
            + "AND o.inicioPrevisto <= :ate AND o.fimPrevisto >= :de")
    List<OrdemProducao> noPeriodo(@Param("status") Collection<String> status, @Param("de") LocalDate de,
                                  @Param("ate") LocalDate ate);

    @Query(value = "SELECT nextval('ind_op_numero_seq')", nativeQuery = true)
    long proximoNumero();
}
