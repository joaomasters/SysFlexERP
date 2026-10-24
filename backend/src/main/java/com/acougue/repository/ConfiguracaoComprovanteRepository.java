package com.acougue.repository;

import com.acougue.entity.ConfiguracaoComprovante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracaoComprovanteRepository extends JpaRepository<ConfiguracaoComprovante, Short> {
}
