package com.acougue.modules.industrial.extrusao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MedicaoRepository extends JpaRepository<Medicao, Long> {
    Optional<Medicao> findByChave(String chave);

    long countByOrdemIdAndForaEspecificacaoTrue(Long ordemId);

    @Query("SELECT DISTINCT m FROM Medicao m JOIN FETCH m.receita LEFT JOIN FETCH m.maquina LEFT JOIN FETCH m.valores "
            + "WHERE m.ordem.id = :ordemId ORDER BY m.medidoEm DESC")
    List<Medicao> daOrdem(@Param("ordemId") Long ordemId);

    @Query("SELECT DISTINCT m FROM Medicao m JOIN FETCH m.ordem JOIN FETCH m.receita LEFT JOIN FETCH m.maquina "
            + "LEFT JOIN FETCH m.valores WHERE m.medidoEm >= :de AND m.medidoEm < :ate "
            + "AND (:somenteFora = FALSE OR m.foraEspecificacao = TRUE) ORDER BY m.medidoEm DESC")
    List<Medicao> noPeriodo(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate, @Param("somenteFora") boolean somenteFora);
}
