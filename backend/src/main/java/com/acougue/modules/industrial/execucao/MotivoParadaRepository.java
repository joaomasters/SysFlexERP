package com.acougue.modules.industrial.execucao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MotivoParadaRepository extends JpaRepository<MotivoParada, Long> {
    List<MotivoParada> findAllByOrderByCodigoAsc();

    Optional<MotivoParada> findByCodigoIgnoreCase(String codigo);
}
