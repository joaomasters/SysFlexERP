package com.acougue.repository;

import com.acougue.entity.ProvedorPagamento;
import com.acougue.entity.TransacaoTerminal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransacaoTerminalRepository extends JpaRepository<TransacaoTerminal, Long> {

    /** Lock pessimista: o polling do PDV e o webhook podem atualizar a mesma transação ao mesmo tempo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransacaoTerminal t WHERE t.id = :id")
    Optional<TransacaoTerminal> findByIdParaAtualizar(@Param("id") Long id);

    List<TransacaoTerminal> findByProvedorAndIdExterno(ProvedorPagamento provedor, String idExterno);
}
