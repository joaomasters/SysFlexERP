package com.acougue.modules.industrial.estrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UnidadeRepository extends JpaRepository<Unidade, Long> {
    List<Unidade> findAllByOrderByCodigoAsc();
    Optional<Unidade> findByCodigoIgnoreCase(String codigo);
}
