package com.acougue.modules.industrial.execucao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ParadaRepository extends JpaRepository<Parada, Long> {
    Optional<Parada> findByChave(String chave);

    @Query("SELECT p FROM Parada p WHERE p.maquina.id = :maquinaId AND p.fim IS NULL")
    Optional<Parada> abertaDaMaquina(@Param("maquinaId") Long maquinaId);

    @Query("SELECT p FROM Parada p JOIN FETCH p.motivo JOIN FETCH p.maquina WHERE p.fim IS NULL")
    List<Parada> abertasComMaquina();

    @Query("SELECT p FROM Parada p WHERE p.apontamento.id = :apontamentoId AND p.fim IS NULL")
    Optional<Parada> abertaDoApontamento(@Param("apontamentoId") Long apontamentoId);

    @Query("SELECT p FROM Parada p JOIN FETCH p.motivo LEFT JOIN FETCH p.maquina WHERE p.ordemId = :ordemId ORDER BY p.inicio DESC")
    List<Parada> daOrdem(@Param("ordemId") Long ordemId);

    /** Paradas que encostam no período (as abertas também). */
    @Query("SELECT p FROM Parada p JOIN FETCH p.motivo LEFT JOIN FETCH p.maquina m "
            + "WHERE (:maquinaId IS NULL OR m.id = :maquinaId) AND p.inicio <= :ate AND (p.fim IS NULL OR p.fim >= :de) "
            + "ORDER BY p.inicio DESC")
    List<Parada> noPeriodo(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate, @Param("maquinaId") Long maquinaId);
}
