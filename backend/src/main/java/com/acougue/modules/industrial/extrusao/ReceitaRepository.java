package com.acougue.modules.industrial.extrusao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReceitaRepository extends JpaRepository<Receita, Long> {
    @Query("SELECT r FROM Receita r JOIN FETCH r.liga JOIN FETCH r.tipoMaquina LEFT JOIN FETCH r.produto "
            + "WHERE (:tipoMaquinaId IS NULL OR r.tipoMaquina.id = :tipoMaquinaId) AND (:ligaId IS NULL OR r.liga.id = :ligaId) "
            + "ORDER BY r.codigo")
    List<Receita> listar(@Param("tipoMaquinaId") Long tipoMaquinaId, @Param("ligaId") Long ligaId);

    Optional<Receita> findByCodigoIgnoreCase(String codigo);
}
