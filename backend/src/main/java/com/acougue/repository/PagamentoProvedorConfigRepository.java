package com.acougue.repository;

import com.acougue.entity.PagamentoProvedorConfig;
import com.acougue.entity.ProvedorPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PagamentoProvedorConfigRepository extends JpaRepository<PagamentoProvedorConfig, ProvedorPagamento> {
}
