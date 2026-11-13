package com.acougue.modules.industrial.estrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SetorRepository extends JpaRepository<Setor, Long> {
    @Query("SELECT s FROM Setor s JOIN FETCH s.unidade ORDER BY s.unidade.codigo, s.codigo")
    List<Setor> listarTodos();
    Optional<Setor> findByUnidadeIdAndCodigoIgnoreCase(Long unidadeId, String codigo);
}
