package com.acougue.modules.industrial.engenharia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BomRepository extends JpaRepository<Bom, Long> {
    @Query("SELECT b FROM Bom b JOIN FETCH b.produto p WHERE (:produtoId IS NULL OR p.id = :produtoId) "
            + "ORDER BY p.nome, b.versao DESC")
    List<Bom> listar(@Param("produtoId") Long produtoId);

    @Query("SELECT b FROM Bom b WHERE b.produto.id = :produtoId AND b.status = 'ATIVA'")
    Optional<Bom> ativaDoProduto(@Param("produtoId") Long produtoId);

    @Query("SELECT COALESCE(MAX(b.versao), 0) FROM Bom b WHERE b.produto.id = :produtoId")
    int ultimaVersao(@Param("produtoId") Long produtoId);

    Optional<Bom> findFirstByProdutoIdAndStatus(Long produtoId, String status);
}
