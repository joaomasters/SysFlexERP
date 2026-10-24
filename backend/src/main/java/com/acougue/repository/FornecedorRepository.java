package com.acougue.repository;

import com.acougue.entity.Fornecedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FornecedorRepository extends JpaRepository<Fornecedor, Long> {

    List<Fornecedor> findAllByOrderByNomeAsc();

    List<Fornecedor> findByAtivoTrueOrderByNomeAsc();

    /** Mesmo critério do índice único do banco: sem diferenciar maiúsculas nem espaços nas pontas. */
    @Query("SELECT f FROM Fornecedor f WHERE LOWER(TRIM(f.nome)) = LOWER(TRIM(:nome))")
    Optional<Fornecedor> buscarPorNomeExato(@Param("nome") String nome);
}