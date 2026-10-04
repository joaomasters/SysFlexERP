package com.acougue.modules.financeiro;

import com.acougue.entity.Cliente;
import com.acougue.repository.ClienteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepo;

    public List<Cliente> listarAtivos() {
        return clienteRepo.findByAtivoTrue();
    }

    /** Clientes elegíveis pra fiado/faturamento — exclui o genérico VAREJO (CONSUMIDOR). */
    public List<Cliente> listarFaturaveis() {
        return clienteRepo.findByAtivoTrueAndTipoClienteNot("VAREJO");
    }

    public List<Cliente> buscarPorNome(String nome) {
        return clienteRepo.buscarPorNome(nome);
    }

    public Cliente buscarPorId(Long id) {
        return clienteRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado: " + id));
    }

    @Transactional
    public Cliente criar(Cliente cliente, Long usuarioId) {
        cliente.setId(null); // garante criação, nunca sobrescreve por engano
        cliente.setCriadoPorId(usuarioId); // autoria vem do token, nunca do corpo da requisição
        return clienteRepo.save(cliente);
    }

    @Transactional
    public Cliente atualizar(Long id, Cliente dados) {
        Cliente existente = buscarPorId(id);
        existente.setNome(dados.getNome());
        existente.setCpfCnpj(dados.getCpfCnpj());
        existente.setTipoPessoa(dados.getTipoPessoa());
        existente.setTelefone(dados.getTelefone());
        existente.setEmail(dados.getEmail());
        existente.setEndereco(dados.getEndereco());
        existente.setTipoCliente(dados.getTipoCliente());
        existente.setLimiteCredito(dados.getLimiteCredito());
        // saldoFiadoAtual NÃO é editável manualmente aqui — é atualizado pelo
        // fluxo de vendas/faturamento (fiado), editar à mão corromperia o saldo real.
        return clienteRepo.save(existente);
    }

    @Transactional
    public void inativar(Long id) {
        Cliente c = buscarPorId(id);
        c.setAtivo(false);
        clienteRepo.save(c);
    }

    @Transactional
    public void reativar(Long id) {
        Cliente c = buscarPorId(id);
        c.setAtivo(true);
        clienteRepo.save(c);
    }
}