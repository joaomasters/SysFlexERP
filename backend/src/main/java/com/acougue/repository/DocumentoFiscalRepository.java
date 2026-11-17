package com.acougue.repository;

import com.acougue.entity.DocumentoFiscal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentoFiscalRepository extends JpaRepository<DocumentoFiscal, Long> {

    @Query("""
            SELECT d FROM DocumentoFiscal d
            WHERE (:modelo IS NULL OR d.modelo = :modelo)
              AND (:status IS NULL OR d.status = :status)
              AND d.createdAt >= :desde
            ORDER BY d.createdAt DESC
            """)
    List<DocumentoFiscal> buscar(@Param("modelo") Short modelo, @Param("status") String status,
                                 @Param("desde") LocalDateTime desde);

    /** A NFC-e mais recente da venda (a última tentativa, autorizada ou não). */
    Optional<DocumentoFiscal> findFirstByVendaIdAndModeloOrderByIdDesc(Long vendaId, Short modelo);

    Optional<DocumentoFiscal> findFirstByVendaIdAndModeloAndStatus(Long vendaId, Short modelo, String status);

    Optional<DocumentoFiscal> findFirstByVendaIdAndModeloAndStatusIn(Long vendaId, Short modelo, Collection<String> status);

    @Query("SELECT MAX(d.numero) FROM DocumentoFiscal d WHERE d.modelo = :modelo AND d.serie = :serie AND d.ambiente = :ambiente")
    Integer maiorNumero(@Param("modelo") Short modelo, @Param("serie") Short serie, @Param("ambiente") String ambiente);

    @Query("SELECT MIN(d.numero) FROM DocumentoFiscal d WHERE d.modelo = :modelo AND d.serie = :serie AND d.ambiente = :ambiente")
    Integer menorNumero(@Param("modelo") Short modelo, @Param("serie") Short serie, @Param("ambiente") String ambiente);

    List<DocumentoFiscal> findByModeloAndSerieAndAmbienteAndNumeroBetweenOrderByNumero(Short modelo, Short serie, String ambiente,
                                                                                      Integer de, Integer ate);
}
