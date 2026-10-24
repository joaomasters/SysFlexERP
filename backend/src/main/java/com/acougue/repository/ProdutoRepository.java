package com.acougue.repository;

import com.acougue.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    /** Renomear um fornecedor atualiza o nome guardado nos produtos vinculados a ele. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Produto p SET p.fornecedor = :nome WHERE p.fornecedorId = :fornecedorId")
    int atualizarNomeFornecedor(@Param("fornecedorId") Long fornecedorId, @Param("nome") String nome);

    Optional<Produto> findByCodigoBalanca(Integer codigoBalanca);

    Optional<Produto> findByEan13(String ean13);

    Optional<Produto> findByCodigoInterno(String codigoInterno);

    List<Produto> findByAtivoTrue();

    List<Produto> findByCodigoBalancaIsNotNullAndAtivoTrue();

    @Query("SELECT p FROM Produto p WHERE LOWER(p.nome) LIKE LOWER(CONCAT('%', :nome, '%')) AND p.ativo = true")
    List<Produto> buscarPorNome(@Param("nome") String nome);

    @Query("SELECT p FROM Produto p WHERE p.estoqueAtual <= p.estoqueMinimo AND p.ativo = true")
    List<Produto> findEstoqueAbaixoMinimo();

    List<Produto> findAllByAtivoTrue();

    // LEGADO: heurística antiga baseada em updatedAt (disparava para qualquer edição
    // do produto, não só preço). Substituída pela fila em ItemPendenteBalancaService /
    // ItemPendenteBalancaRepository (ver migration V13). Sem uso atual — mantido por
    // enquanto caso algum relatório antigo dependa dele; remover se confirmado sem uso.
    @Query("SELECT p FROM Produto p WHERE p.codigoBalanca IS NOT NULL AND p.ativo = true AND p.updatedAt > :desde")
    List<Produto> findBalancaAtualizadosDesde(@Param("desde") LocalDateTime desde);

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.codigoBalanca IS NOT NULL AND p.ativo = true AND p.updatedAt > :desde")
    long countBalancaAtualizadosDesde(@Param("desde") LocalDateTime desde);
}