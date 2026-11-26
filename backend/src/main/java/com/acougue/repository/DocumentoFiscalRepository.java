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

    /** Documentos de um período pela data de emissão (ou de criação, se não chegou a ser emitido). */
    @Query("""
            SELECT d FROM DocumentoFiscal d
            WHERE (:modelo IS NULL OR d.modelo = :modelo)
              AND (:status IS NULL OR d.status = :status)
              AND COALESCE(d.emitidaEm, d.createdAt) >= :de
              AND COALESCE(d.emitidaEm, d.createdAt) < :ate
            ORDER BY COALESCE(d.emitidaEm, d.createdAt) DESC
            """)
    List<DocumentoFiscal> buscarPeriodo(@Param("modelo") Short modelo, @Param("status") String status,
                                        @Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate);

    /** Notas de produção com XML (autorizadas, inclusive as canceladas depois), em ordem de série e número. */
    @Query("""
            SELECT d FROM DocumentoFiscal d
            WHERE d.ambiente = 'PRODUCAO' AND d.xml IS NOT NULL
              AND d.emitidaEm >= :de AND d.emitidaEm < :ate
            ORDER BY d.modelo, d.serie, d.numero
            """)
    List<DocumentoFiscal> comXmlNoPeriodo(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate);

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
