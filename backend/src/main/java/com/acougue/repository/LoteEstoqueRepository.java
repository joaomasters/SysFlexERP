package com.acougue.repository;

import com.acougue.entity.LoteEstoque;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoteEstoqueRepository extends JpaRepository<LoteEstoque, Long> {

    /**
     * Lotes com saldo de um produto, na ordem de baixa FEFO (vence primeiro, sai primeiro;
     * empate pela ordem de chegada). Com lock de escrita: duas baixas simultâneas do mesmo
     * produto não podem consumir o mesmo saldo do lote.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LoteEstoque l " +
            "WHERE l.produto.id = :produtoId AND l.quantidadeAtual > 0 " +
            "ORDER BY l.dataValidade ASC, l.id ASC")
    List<LoteEstoque> findDisponiveisParaBaixa(@Param("produtoId") Long produtoId);

    /** Todos os lotes com saldo, do que vence primeiro para o que vence depois (consulta/alertas). */
    @Query("SELECT l FROM LoteEstoque l JOIN FETCH l.produto p " +
            "WHERE l.quantidadeAtual > 0 " +
            "ORDER BY l.dataValidade ASC, l.id ASC")
    List<LoteEstoque> findAllDisponiveis();
}
