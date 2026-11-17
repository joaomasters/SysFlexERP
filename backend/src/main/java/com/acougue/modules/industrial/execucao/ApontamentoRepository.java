package com.acougue.modules.industrial.execucao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApontamentoRepository extends JpaRepository<Apontamento, Long> {
    Optional<Apontamento> findByChave(String chave);

    @Query("SELECT a FROM Apontamento a WHERE a.maquina.id = :maquinaId AND a.status IN ('EM_ANDAMENTO', 'PAUSADO')")
    Optional<Apontamento> abertoDaMaquina(@Param("maquinaId") Long maquinaId);

    @Query("SELECT a FROM Apontamento a WHERE a.operadorId = :operadorId AND a.status IN ('EM_ANDAMENTO', 'PAUSADO')")
    Optional<Apontamento> abertoDoOperador(@Param("operadorId") Long operadorId);

    @Query("SELECT a FROM Apontamento a JOIN FETCH a.operacao LEFT JOIN FETCH a.maquina WHERE a.ordem.id = :ordemId "
            + "ORDER BY a.inicio DESC, a.id DESC")
    List<Apontamento> daOrdem(@Param("ordemId") Long ordemId);

    @Query("SELECT a FROM Apontamento a WHERE a.ordem.id IN :ordemIds AND a.status <> 'ESTORNADO'")
    List<Apontamento> validosDasOrdens(@Param("ordemIds") Collection<Long> ordemIds);

    @Query("SELECT COUNT(a) FROM Apontamento a WHERE a.ordem.id = :ordemId AND a.status IN ('EM_ANDAMENTO', 'PAUSADO')")
    long abertosDaOrdem(@Param("ordemId") Long ordemId);

    @Query("SELECT COUNT(a) FROM Apontamento a WHERE a.ordem.id = :ordemId AND a.status <> 'ESTORNADO'")
    long validosDaOrdem(@Param("ordemId") Long ordemId);

    long countByOrdemId(Long ordemId);

    /** Apontamentos finalizados no período (pelo fim), com operação, OP, produto e máquina. */
    @Query("SELECT a FROM Apontamento a JOIN FETCH a.operacao op JOIN FETCH a.ordem o JOIN FETCH o.produto LEFT JOIN FETCH a.maquina "
            + "WHERE a.status = 'FINALIZADO' AND a.fim >= :de AND a.fim < :ate")
    List<Apontamento> finalizadosNoPeriodo(@Param("de") java.time.LocalDateTime de, @Param("ate") java.time.LocalDateTime ate);

    @Query("SELECT COUNT(a) FROM Apontamento a WHERE a.ordem.id = :ordemId AND a.lote IS NOT NULL")
    long lotesDaOrdem(@Param("ordemId") Long ordemId);
}
