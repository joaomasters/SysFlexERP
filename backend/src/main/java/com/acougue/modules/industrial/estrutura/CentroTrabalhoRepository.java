package com.acougue.modules.industrial.estrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CentroTrabalhoRepository extends JpaRepository<CentroTrabalho, Long> {
    @Query("SELECT c FROM CentroTrabalho c JOIN FETCH c.setor s JOIN FETCH s.unidade ORDER BY c.codigo")
    List<CentroTrabalho> listarTodos();
    Optional<CentroTrabalho> findByCodigoIgnoreCase(String codigo);
}
