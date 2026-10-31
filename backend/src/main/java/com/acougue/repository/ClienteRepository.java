package com.acougue.repository;

import com.acougue.entity.Cliente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    List<Cliente> findByAtivoTrue();

    List<Cliente> findByTipoClienteAndAtivoTrue(String tipoCliente);

    List<Cliente> findByAtivoTrueAndTipoClienteNot(String tipoCliente);

    Optional<Cliente> findByCpfCnpj(String cpfCnpj);

    /**
     * Carrega o cliente com lock de escrita. Usado ao fechar venda no fiado: a checagem do
     * limite lê o saldo em aberto e depois lança a conta — sem o lock, duas vendas fiado
     * simultâneas do mesmo cliente leriam o mesmo saldo e ambas passariam do limite.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cliente c WHERE c.id = :id")
    Optional<Cliente> buscarParaAtualizar(@Param("id") Long id);

    @Query("SELECT c FROM Cliente c WHERE LOWER(c.nome) LIKE LOWER(CONCAT('%', :nome, '%')) AND c.ativo = true")
    List<Cliente> buscarPorNome(@Param("nome") String nome);
}