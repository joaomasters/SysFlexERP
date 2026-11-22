package com.acougue.repository;

import com.acougue.entity.CaixaCedula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaixaCedulaRepository extends JpaRepository<CaixaCedula, Long> {
    List<CaixaCedula> findByCaixaIdOrderByMomentoAscDenominacaoDesc(Long caixaId);
}
