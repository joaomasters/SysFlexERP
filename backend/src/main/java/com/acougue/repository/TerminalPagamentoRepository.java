package com.acougue.repository;

import com.acougue.entity.TerminalPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TerminalPagamentoRepository extends JpaRepository<TerminalPagamento, Long> {

    List<TerminalPagamento> findAllByOrderByAtivoDescNomeAsc();

    List<TerminalPagamento> findByAtivoTrueOrderByNomeAsc();
}
