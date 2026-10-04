package com.acougue.repository;

import com.acougue.entity.ContasAReceber;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContasAReceberRepository extends JpaRepository<ContasAReceber, Long> {

    List<ContasAReceber> findByClienteIdAndStatusOrderByDataVencimentoAsc(Long clienteId, String status);

    List<ContasAReceber> findByClienteId(Long clienteId);

    /**
     * Carrega a conta com lock de escrita. Usado ao registrar pagamento: o
     * saldo anterior/posterior gravado no histórico depende do valor_pago
     * atual — sem o lock, dois recebimentos simultâneos leriam o mesmo saldo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ContasAReceber c WHERE c.id = :id")
    Optional<ContasAReceber> buscarParaAtualizar(@Param("id") Long id);

    List<ContasAReceber> findByStatusOrderByDataVencimentoAsc(String status);

    Optional<ContasAReceber> findByFaturamentoId(Long faturamentoId);

    List<ContasAReceber> findByDataVencimentoBeforeAndStatusIn(LocalDate data, List<String> statuses);

    @Query("SELECT COALESCE(SUM(c.valor - c.valorPago), 0) FROM ContasAReceber c " +
            "WHERE c.cliente.id = :clienteId AND c.status IN ('ABERTO', 'PARCIAL')")
    BigDecimal saldoAberto(@Param("clienteId") Long clienteId);

    /**
     * Contas de fiado avulso (lançadas pelo PDV por venda) elegíveis pra
     * entrar num fechamento: ainda em aberto/parcial, com venda vinculada
     * dentro do período, e que ainda não foram agrupadas em nenhum outro
     * fechamento (nem já SÃO elas mesmas a conta consolidada de um).
     */
    @Query("SELECT c FROM ContasAReceber c WHERE c.cliente.id = :clienteId " +
            "AND c.status IN ('ABERTO', 'PARCIAL') " +
            "AND c.faturamento IS NULL AND c.absorvidoPorFaturamento IS NULL " +
            "AND c.venda IS NOT NULL AND c.venda.dataVenda BETWEEN :inicio AND :fim")
    List<ContasAReceber> buscarFiadoAvulsoElegivel(@Param("clienteId") Long clienteId,
                                                   @Param("inicio") LocalDateTime inicio,
                                                   @Param("fim") LocalDateTime fim);
}