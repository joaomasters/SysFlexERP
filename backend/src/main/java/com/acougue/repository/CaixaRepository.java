package com.acougue.repository;

import com.acougue.entity.Caixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CaixaRepository extends JpaRepository<Caixa, Long> {

    Optional<Caixa> findFirstByOperadorIdAndStatus(Long operadorId, String status);

    Optional<Caixa> findFirstByStatusOrderByDataAberturaDesc(String status);

    List<Caixa> findAllByOrderByDataAberturaDesc();

    /** Caixas fechados no período (relatório de sobras e faltas). */
    List<Caixa> findByStatusAndDataFechamentoBetweenOrderByDataFechamentoDesc(String status, LocalDateTime inicio, LocalDateTime fim);
}