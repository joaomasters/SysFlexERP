package com.acougue.modules.industrial.pcp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.List;

public interface OpEventoRepository extends JpaRepository<OpEvento, Long> {
    List<OpEvento> findByOrdemIdOrderByCreatedAtDescIdDesc(Long ordemId);

    @Query("SELECT e FROM OpEvento e WHERE e.statusNovo = 'CONCLUIDA' AND e.createdAt >= :de AND e.createdAt < :ate")
    List<OpEvento> conclusoesNoPeriodo(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate);
}
