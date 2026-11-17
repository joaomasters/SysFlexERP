package com.acougue.modules.industrial.estrutura;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MaquinaParametroRepository extends JpaRepository<MaquinaParametro, Long> {
    boolean existsByParametroId(Long parametroId);
}
