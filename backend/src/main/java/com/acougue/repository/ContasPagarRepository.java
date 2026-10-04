package com.acougue.repository;

import com.acougue.entity.ContasPagar;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContasPagarRepository extends JpaRepository<ContasPagar, Long> {
    List<ContasPagar> findByStatusOrderByDataVencimentoAsc(String status);
    List<ContasPagar> findByDataVencimentoBetweenOrderByDataVencimentoAsc(LocalDate inicio, LocalDate fim);
    List<ContasPagar> findByDataVencimentoBeforeAndStatusOrderByDataVencimentoAsc(LocalDate data, String status);

    /** Carrega a conta com lock de escrita — ver ContasAReceberRepository#buscarParaAtualizar. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ContasPagar c WHERE c.id = :id")
    Optional<ContasPagar> buscarParaAtualizar(@Param("id") Long id);
}
