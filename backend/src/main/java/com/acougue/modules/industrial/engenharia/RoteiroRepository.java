package com.acougue.modules.industrial.engenharia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoteiroRepository extends JpaRepository<Roteiro, Long> {
    @Query("SELECT r FROM Roteiro r JOIN FETCH r.produto p WHERE (:produtoId IS NULL OR p.id = :produtoId) "
            + "ORDER BY p.nome, r.versao DESC")
    List<Roteiro> listar(@Param("produtoId") Long produtoId);

    @Query("SELECT r FROM Roteiro r WHERE r.produto.id = :produtoId AND r.status = 'ATIVO'")
    Optional<Roteiro> ativoDoProduto(@Param("produtoId") Long produtoId);

    @Query("SELECT COALESCE(MAX(r.versao), 0) FROM Roteiro r WHERE r.produto.id = :produtoId")
    int ultimaVersao(@Param("produtoId") Long produtoId);

    Optional<Roteiro> findFirstByProdutoIdAndStatus(Long produtoId, String status);
}
