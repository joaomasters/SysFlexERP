package com.acougue.modules.industrial.extrusao;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoteMpRepository extends JpaRepository<LoteMp, Long> {
    @Query("SELECT l FROM LoteMp l JOIN FETCH l.produto p JOIN FETCH l.liga LEFT JOIN FETCH l.fornecedor "
            + "WHERE (:produtoId IS NULL OR p.id = :produtoId) AND (:status IS NULL OR l.status = :status) "
            + "ORDER BY l.dataRecebimento DESC, l.id DESC")
    List<LoteMp> listar(@Param("produtoId") Long produtoId, @Param("status") String status);

    @Query("SELECT l FROM LoteMp l WHERE l.produto.id = :produtoId AND UPPER(l.numero) = UPPER(:numero)")
    Optional<LoteMp> doProdutoComNumero(@Param("produtoId") Long produtoId, @Param("numero") String numero);

    boolean existsByProdutoId(Long produtoId);

    List<LoteMp> findTop20ByNumeroContainingIgnoreCaseOrderByIdDesc(String numero);

    /** Trava o lote até o fim da transação: dois consumos ao mesmo tempo não passam do saldo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LoteMp l WHERE l.id = :id")
    Optional<LoteMp> travar(@Param("id") Long id);
}
