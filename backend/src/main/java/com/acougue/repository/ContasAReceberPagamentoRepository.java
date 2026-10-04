package com.acougue.repository;

import com.acougue.entity.ContasAReceberPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ContasAReceberPagamentoRepository extends JpaRepository<ContasAReceberPagamento, Long> {

    /**
     * Histórico de recebimentos, agrupável por conta (ordenado por conta e, dentro
     * dela, cronologicamente). clienteId e contaId são filtros opcionais.
     */
    @Query("SELECT p FROM ContasAReceberPagamento p JOIN FETCH p.conta c JOIN FETCH c.cliente cl " +
            "WHERE p.dataPagamento BETWEEN :inicio AND :fim " +
            "AND (:clienteId IS NULL OR cl.id = :clienteId) " +
            "AND (:contaId IS NULL OR c.id = :contaId) " +
            "ORDER BY c.id ASC, p.dataPagamento ASC, p.id ASC")
    List<ContasAReceberPagamento> buscarHistorico(@Param("inicio") LocalDateTime inicio,
                                                  @Param("fim") LocalDateTime fim,
                                                  @Param("clienteId") Long clienteId,
                                                  @Param("contaId") Long contaId);
}
