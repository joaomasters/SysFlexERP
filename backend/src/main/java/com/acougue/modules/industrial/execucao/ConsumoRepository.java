package com.acougue.modules.industrial.execucao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConsumoRepository extends JpaRepository<Consumo, Long> {
    Optional<Consumo> findByChave(String chave);

    @Query("SELECT c FROM Consumo c JOIN FETCH c.produto WHERE c.ordem.id = :ordemId ORDER BY c.createdAt DESC, c.id DESC")
    List<Consumo> daOrdem(@Param("ordemId") Long ordemId);

    @Query("SELECT COUNT(c) FROM Consumo c WHERE c.ordem.id = :ordemId AND c.estornado = FALSE")
    long validosDaOrdem(@Param("ordemId") Long ordemId);
}
