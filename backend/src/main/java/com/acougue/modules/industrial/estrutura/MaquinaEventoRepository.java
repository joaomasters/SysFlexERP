package com.acougue.modules.industrial.estrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MaquinaEventoRepository extends JpaRepository<MaquinaEvento, Long> {
    List<MaquinaEvento> findByMaquinaIdOrderByCreatedAtDescIdDesc(Long maquinaId);
}
