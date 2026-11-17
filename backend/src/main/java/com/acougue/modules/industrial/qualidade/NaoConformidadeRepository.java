package com.acougue.modules.industrial.qualidade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NaoConformidadeRepository extends JpaRepository<NaoConformidade, Long> {
    @Query("SELECT n FROM NaoConformidade n LEFT JOIN FETCH n.lote LEFT JOIN FETCH n.ordem "
            + "WHERE (:status IS NULL OR n.status = :status) ORDER BY n.id DESC")
    List<NaoConformidade> listar(@Param("status") String status);

    @Query("SELECT n FROM NaoConformidade n WHERE n.lote.id = :loteId ORDER BY n.id DESC")
    List<NaoConformidade> doLote(@Param("loteId") Long loteId);

    @Query("SELECT COUNT(n) FROM NaoConformidade n WHERE n.lote.id = :loteId AND n.status = 'ABERTA'")
    long abertasDoLote(@Param("loteId") Long loteId);

    @Query(value = "SELECT nextval('ind_nc_numero_seq')", nativeQuery = true)
    long proximoNumero();
}
