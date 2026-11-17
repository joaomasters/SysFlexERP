package com.acougue.modules.fiscal.parametros;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EnquadramentoRepository extends JpaRepository<Enquadramento, Long> {

    List<Enquadramento> findAllByOrderByInicioDesc();

    @Query("SELECT e FROM Enquadramento e WHERE e.inicio <= :dia AND (e.fim IS NULL OR e.fim >= :dia)")
    Optional<Enquadramento> vigenteEm(@Param("dia") LocalDate dia);

    Optional<Enquadramento> findFirstByOrderByInicioDesc();
}
