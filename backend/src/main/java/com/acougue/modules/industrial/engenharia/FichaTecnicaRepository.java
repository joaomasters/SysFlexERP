package com.acougue.modules.industrial.engenharia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FichaTecnicaRepository extends JpaRepository<FichaTecnica, Long> {
    @Query("SELECT f FROM FichaTecnica f JOIN FETCH f.produto p ORDER BY p.nome")
    List<FichaTecnica> listarTodas();

    Optional<FichaTecnica> findByProdutoId(Long produtoId);

    List<FichaTecnica> findByProdutoIdIn(Collection<Long> produtoIds);
}
