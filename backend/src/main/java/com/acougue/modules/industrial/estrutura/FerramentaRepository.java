package com.acougue.modules.industrial.estrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FerramentaRepository extends JpaRepository<Ferramenta, Long> {
    @Query("SELECT f FROM Ferramenta f LEFT JOIN FETCH f.tipoMaquina ORDER BY f.codigo, f.revisao")
    List<Ferramenta> listarTodas();
    @Query("SELECT f FROM Ferramenta f WHERE UPPER(f.codigo) = UPPER(:codigo) "
            + "AND COALESCE(f.revisao, '') = COALESCE(:revisao, '')")
    Optional<Ferramenta> buscarPorCodigoERevisao(@Param("codigo") String codigo, @Param("revisao") String revisao);
}
