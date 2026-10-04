package com.acougue.repository;

import com.acougue.entity.ContasPagarPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ContasPagarPagamentoRepository extends JpaRepository<ContasPagarPagamento, Long> {

    /**
     * Histórico de pagamentos, agrupável por conta. contaId é filtro opcional;
     * fornecedor é busca parcial (string vazia = sem filtro).
     */
    @Query("SELECT p FROM ContasPagarPagamento p JOIN FETCH p.conta c " +
            "WHERE p.dataPagamento BETWEEN :inicio AND :fim " +
            "AND (:contaId IS NULL OR c.id = :contaId) " +
            "AND (:fornecedor = '' OR LOWER(c.fornecedor) LIKE LOWER(CONCAT('%', :fornecedor, '%'))) " +
            "ORDER BY c.id ASC, p.dataPagamento ASC, p.id ASC")
    List<ContasPagarPagamento> buscarHistorico(@Param("inicio") LocalDateTime inicio,
                                               @Param("fim") LocalDateTime fim,
                                               @Param("contaId") Long contaId,
                                               @Param("fornecedor") String fornecedor);
}
