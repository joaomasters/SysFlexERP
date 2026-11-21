package com.acougue.modules.industrial.pcp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OpOperacaoRepository extends JpaRepository<OpOperacao, Long> {
    /** Operações ainda não concluídas de um centro, em OPs que podem ser apontadas (fila do chão de fábrica). */
    @Query("SELECT op FROM OpOperacao op JOIN FETCH op.ordem o JOIN FETCH o.produto LEFT JOIN FETCH op.tipoMaquina "
            + "LEFT JOIN FETCH op.maquina WHERE op.centroTrabalho.id = :centroId AND op.status <> 'CONCLUIDA' "
            + "AND o.status IN :status")
    List<OpOperacao> fila(@Param("centroId") Long centroId, @Param("status") Collection<String> status);
}
