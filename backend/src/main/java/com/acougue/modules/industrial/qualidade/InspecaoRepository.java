package com.acougue.modules.industrial.qualidade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InspecaoRepository extends JpaRepository<Inspecao, Long> {
    Optional<Inspecao> findByChave(String chave);

    @Query("SELECT DISTINCT i FROM Inspecao i JOIN FETCH i.plano LEFT JOIN FETCH i.valores WHERE i.lote.id = :loteId "
            + "ORDER BY i.createdAt DESC")
    List<Inspecao> doLote(@Param("loteId") Long loteId);
}
