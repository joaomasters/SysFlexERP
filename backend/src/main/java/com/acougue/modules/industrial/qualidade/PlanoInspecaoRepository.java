package com.acougue.modules.industrial.qualidade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlanoInspecaoRepository extends JpaRepository<PlanoInspecao, Long> {
    @Query("SELECT p FROM PlanoInspecao p JOIN FETCH p.produto ORDER BY p.codigo")
    List<PlanoInspecao> listar();

    Optional<PlanoInspecao> findByCodigoIgnoreCase(String codigo);

    @Query("SELECT p FROM PlanoInspecao p WHERE p.produto.id = :produtoId AND p.ativo = TRUE")
    Optional<PlanoInspecao> ativoDoProduto(@Param("produtoId") Long produtoId);
}
