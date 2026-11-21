package com.acougue.modules.industrial.qualidade;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoteRepository extends JpaRepository<Lote, Long> {
    Optional<Lote> findByCodigoIgnoreCase(String codigo);

    Optional<Lote> findByApontamentoId(Long apontamentoId);

    List<Lote> findTop20ByCodigoContainingIgnoreCaseOrderByIdDesc(String codigo);

    @Query("SELECT l FROM Lote l JOIN FETCH l.produto p LEFT JOIN FETCH l.ordem "
            + "WHERE (:situacao IS NULL OR l.situacao = :situacao) AND (:produtoId IS NULL OR p.id = :produtoId) "
            + "ORDER BY l.createdAt DESC, l.id DESC")
    List<Lote> listar(@Param("situacao") String situacao, @Param("produtoId") Long produtoId);

    List<Lote> findByOrdemIdOrderByIdAsc(Long ordemId);

    List<Lote> findByOrdemIdIn(Collection<Long> ordemIds);

    /** Lotes liberados com saldo de um produto, do mais antigo para o mais novo (FIFO). */
    @Query("SELECT l FROM Lote l WHERE l.produto.id = :produtoId AND l.situacao = 'LIBERADO' AND l.saldo > 0 "
            + "ORDER BY l.createdAt ASC, l.id ASC")
    List<Lote> liberadosFifo(@Param("produtoId") Long produtoId);

    /** Trava o lote até o fim da transação (reserva, divisão, união e carregamento não passam do saldo). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lote l WHERE l.id = :id")
    Optional<Lote> travar(@Param("id") Long id);

    @Query(value = "SELECT nextval('ind_lote_seq')", nativeQuery = true)
    long proximoNumero();
}
