package com.acougue.modules.industrial.pcp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpEventoRepository extends JpaRepository<OpEvento, Long> {
    List<OpEvento> findByOrdemIdOrderByCreatedAtDescIdDesc(Long ordemId);
}
