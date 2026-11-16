package com.acougue.modules.industrial.extrusao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LigaRepository extends JpaRepository<Liga, Long> {
    List<Liga> findAllByOrderByCodigoAsc();

    Optional<Liga> findByCodigoIgnoreCase(String codigo);
}
