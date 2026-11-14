package com.acougue.modules.industrial.estrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface TipoMaquinaRepository extends JpaRepository<TipoMaquina, Long> {
    @Query("SELECT DISTINCT t FROM TipoMaquina t LEFT JOIN FETCH t.parametros ORDER BY t.codigo")
    List<TipoMaquina> listarTodos();
    Optional<TipoMaquina> findByCodigoIgnoreCase(String codigo);
}
