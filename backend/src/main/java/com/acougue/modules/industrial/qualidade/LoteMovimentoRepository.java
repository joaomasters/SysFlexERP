package com.acougue.modules.industrial.qualidade;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoteMovimentoRepository extends JpaRepository<LoteMovimento, Long> {
    List<LoteMovimento> findByLoteIdOrderByCreatedAtAscIdAsc(Long loteId);
}
