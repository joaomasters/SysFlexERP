package com.acougue.repository;

import com.acougue.entity.ConfiguracaoFiscal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracaoFiscalRepository extends JpaRepository<ConfiguracaoFiscal, Short> {

    /** Com trava de escrita: dois caixas emitindo ao mesmo tempo não pegam o mesmo número de nota. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ConfiguracaoFiscal c WHERE c.id = 1")
    Optional<ConfiguracaoFiscal> travar();
}
