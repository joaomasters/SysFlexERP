package com.acougue.repository;

import com.acougue.entity.NotaFiscalSaida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotaFiscalSaidaRepository extends JpaRepository<NotaFiscalSaida, Long> {
    List<NotaFiscalSaida> findAllByOrderByCreatedAtDesc();
    List<NotaFiscalSaida> findByClienteIdOrderByCreatedAtDesc(Long clienteId);

    // Mesmos filtros do Recebimento de Mercadoria: período + busca parcial por nome
    // (aqui, do cliente). String vazia = sem filtro de nome; LEFT JOIN mantém as notas
    // sem cliente (consumidor final) quando não há filtro de nome.
    @Query("SELECT n FROM NotaFiscalSaida n LEFT JOIN n.cliente c " +
            "WHERE n.dataEmissao BETWEEN :inicio AND :fim " +
            "AND (:cliente = '' OR LOWER(c.nome) LIKE LOWER(CONCAT('%', :cliente, '%'))) " +
            "ORDER BY n.dataEmissao DESC")
    List<NotaFiscalSaida> buscarComFiltros(@Param("inicio") LocalDateTime inicio,
                                           @Param("fim") LocalDateTime fim,
                                           @Param("cliente") String cliente);
}
