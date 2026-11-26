package com.acougue.repository;

import com.acougue.entity.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VendaRepository extends JpaRepository<Venda, Long> {

    List<Venda> findByClienteIdOrderByDataVendaDesc(Long clienteId);

    List<Venda> findByCaixaIdAndStatus(Long caixaId, String status);

    /** Extrato de faturamento: [id, dataVenda, total, troco, nfceStatus] das vendas fechadas do período. */
    @Query("SELECT v.id, v.dataVenda, v.total, v.troco, v.nfceStatus FROM Venda v " +
            "WHERE v.status = 'FECHADA' AND v.dataVenda >= :de AND v.dataVenda < :ate")
    List<Object[]> resumoFechadas(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate);

    /** Extrato de faturamento: [vendaId, formaPagamento, valor] dos pagamentos das vendas fechadas do período. */
    @Query("SELECT p.venda.id, p.formaPagamento, p.valor FROM PagamentoVenda p " +
            "WHERE p.venda.status = 'FECHADA' AND p.venda.dataVenda >= :de AND p.venda.dataVenda < :ate")
    List<Object[]> pagamentosFechadas(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate);

    /** Relatório de tickets de saída: todas as vendas do período (qualquer status), já com os itens. */
    @Query("SELECT DISTINCT v FROM Venda v LEFT JOIN FETCH v.itens " +
            "WHERE v.dataVenda BETWEEN :inicio AND :fim ORDER BY v.dataVenda DESC")
    List<Venda> findTicketsDoPeriodo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    @Query("SELECT v FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim ORDER BY v.dataVenda DESC")
    List<Venda> findByPeriodo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    // LEGADO: usada pelo antigo gerarFechamento(), que somava TODAS as vendas
    // do período (inclusive já quitadas na hora e já viradas fiado avulso),
    // causando cobrança em duplicidade. Substituída por
    // ContasAReceberRepository.buscarFiadoAvulsoElegivel (ver migration V14).
    // Sem uso atual — mantida por enquanto caso algo mais dependa dela.
    @Query("SELECT v FROM Venda v WHERE v.cliente.id = :clienteId " +
            "AND v.status = 'FECHADA' AND v.dataVenda BETWEEN :inicio AND :fim")
    List<Venda> findVendasFaturamentoCliente(
            @Param("clienteId") Long clienteId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    /** Comissões: [operadorId, qtdVendas, totalVendido, totalComissao] por operador no período. */
    @Query("SELECT v.operadorId, COUNT(v), COALESCE(SUM(v.total), 0), COALESCE(SUM(v.valorComissao), 0) " +
            "FROM Venda v WHERE v.status = 'FECHADA' AND v.operadorId IS NOT NULL " +
            "AND v.dataVenda BETWEEN :inicio AND :fim GROUP BY v.operadorId")
    List<Object[]> resumirComissoesPorOperador(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    @Query("SELECT v FROM Venda v WHERE v.status = 'FECHADA' AND v.operadorId = :operadorId " +
            "AND v.dataVenda BETWEEN :inicio AND :fim ORDER BY v.dataVenda DESC")
    List<Venda> findFechadasDoOperador(
            @Param("operadorId") Long operadorId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venda v " +
            "WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.status = 'FECHADA'")
    BigDecimal somarTotalPeriodo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );
}