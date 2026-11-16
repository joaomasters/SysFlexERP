package com.acougue.modules.industrial.extrusao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PerdaRepository extends JpaRepository<Perda, Long> {
    Optional<Perda> findByChave(String chave);

    @Query("SELECT p FROM Perda p WHERE p.ordem.id = :ordemId ORDER BY p.createdAt DESC, p.id DESC")
    List<Perda> daOrdem(@Param("ordemId") Long ordemId);
}
