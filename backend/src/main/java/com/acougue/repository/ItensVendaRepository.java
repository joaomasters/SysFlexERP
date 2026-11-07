package com.acougue.repository;

import com.acougue.entity.ItensVenda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ItensVendaRepository extends JpaRepository<ItensVenda, Long> {

    List<ItensVenda> findByVendaId(Long vendaId);

    @Query("SELECT COALESCE(SUM(i.custoItem * i.quantidade), 0) FROM ItensVenda i " +
           "WHERE i.venda.dataVenda BETWEEN :inicio AND :fim AND i.venda.status = 'FECHADA'")
    BigDecimal somarCMVPeriodo(
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    @Query("SELECT i.produto.id, i.produto.nome, SUM(i.quantidade), SUM(i.totalItem), " +
           "SUM(i.custoItem * i.quantidade) FROM ItensVenda i " +
           "WHERE i.venda.dataVenda BETWEEN :inicio AND :fim AND i.venda.status = 'FECHADA' " +
           "GROUP BY i.produto.id, i.produto.nome ORDER BY SUM(i.totalItem) DESC")
    List<Object[]> relatorioMargemPorProduto(
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    /** Curva ABC e margem por tipo: [produtoId, nome, tipoProduto, quantidade, faturamento, custo]. */
    @Query("SELECT i.produto.id, i.produto.nome, i.produto.tipoProduto, SUM(i.quantidade), SUM(i.totalItem), " +
           "COALESCE(SUM(i.custoItem * i.quantidade), 0) FROM ItensVenda i " +
           "WHERE i.venda.dataVenda BETWEEN :inicio AND :fim AND i.venda.status = 'FECHADA' " +
           "GROUP BY i.produto.id, i.produto.nome, i.produto.tipoProduto")
    List<Object[]> vendasPorProduto(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Descontos dados nos itens, por operador: [operadorId, soma dos descontos]. */
    @Query("SELECT i.venda.operadorId, COALESCE(SUM(i.descontoItem), 0) FROM ItensVenda i " +
           "WHERE i.venda.dataVenda BETWEEN :inicio AND :fim AND i.venda.status = 'FECHADA' " +
           "GROUP BY i.venda.operadorId")
    List<Object[]> descontosPorOperador(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}
