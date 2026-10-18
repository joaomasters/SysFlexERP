package com.acougue.repository;

import com.acougue.entity.PagamentoVenda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PagamentoVendaRepository extends JpaRepository<PagamentoVenda, Long> {

    List<PagamentoVenda> findByVendaId(Long vendaId);

    boolean existsByTransacaoTerminalId(Long transacaoTerminalId);

    @Query("SELECT p.formaPagamento, COALESCE(SUM(p.valor), 0) FROM PagamentoVenda p " +
           "WHERE p.dataPagamento BETWEEN :inicio AND :fim " +
           "GROUP BY p.formaPagamento")
    List<Object[]> totaisPorFormaPagamento(
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    @Query("SELECT COALESCE(SUM(p.valor), 0) FROM PagamentoVenda p " +
           "WHERE p.formaPagamento = 'DINHEIRO' AND p.dataPagamento BETWEEN :inicio AND :fim")
    BigDecimal totalDinheiroPeriodo(
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );
}
