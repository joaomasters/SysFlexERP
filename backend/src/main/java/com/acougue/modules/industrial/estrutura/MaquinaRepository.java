package com.acougue.modules.industrial.estrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface MaquinaRepository extends JpaRepository<Maquina, Long> {
    @Query("SELECT m FROM Maquina m JOIN FETCH m.tipoMaquina JOIN FETCH m.centroTrabalho c JOIN FETCH c.setor s "
            + "JOIN FETCH s.unidade ORDER BY m.codigo")
    List<Maquina> listarTodas();
    Optional<Maquina> findByCodigoIgnoreCase(String codigo);
    long countByCentroTrabalhoIdAndStatusNot(Long centroTrabalhoId, String status);

    /** Máquinas que contam na capacidade (todas menos as inativas), já com o calendário de turnos. */
    @Query("SELECT DISTINCT m FROM Maquina m JOIN FETCH m.centroTrabalho LEFT JOIN FETCH m.turnos WHERE m.status <> 'INATIVA'")
    List<Maquina> ativasComTurnos();
}
