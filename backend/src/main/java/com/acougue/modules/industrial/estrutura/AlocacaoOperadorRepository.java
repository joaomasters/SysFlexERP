package com.acougue.modules.industrial.estrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface AlocacaoOperadorRepository extends JpaRepository<AlocacaoOperador, Long> {
    @Query("SELECT a FROM AlocacaoOperador a JOIN FETCH a.centroTrabalho JOIN FETCH a.turno "
            + "ORDER BY a.vigenteDe DESC, a.id DESC")
    List<AlocacaoOperador> listarTodas();
    List<AlocacaoOperador> findByUsuarioIdAndTurnoId(Long usuarioId, Long turnoId);
}
