package com.acougue.modules.financeiro;

import com.acougue.entity.Cliente;
import com.acougue.repository.ClienteRepository;
import com.acougue.repository.ContasAReceberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepo;
    private final ContasAReceberRepository contasRepo;

    public List<Cliente> listarAtivos() {
        return comSaldos(clienteRepo.findByAtivoTrue());
    }

    /** Clientes elegíveis pra fiado/faturamento — exclui o genérico VAREJO (CONSUMIDOR). */
    public List<Cliente> listarFaturaveis() {
        return comSaldos(clienteRepo.findByAtivoTrueAndTipoClienteNot("VAREJO"));
    }

    public List<Cliente> buscarPorNome(String nome) {
        return comSaldos(clienteRepo.buscarPorNome(nome));
    }

    public Cliente buscarPorId(Long id) {
        return comSaldo(carregar(id));
    }

    private Cliente carregar(Long id) {
        return clienteRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado: " + id));
    }

    /** Preenche o saldo de fiado em aberto (calculado das contas a receber) em um cliente. */
    private Cliente comSaldo(Cliente c) {
        c.setSaldoFiadoAtual(contasRepo.saldoAberto(c.getId()).setScale(2, RoundingMode.HALF_UP));
        return c;
    }

    /** Preenche o saldo de vários clientes com UMA consulta (evita uma ida ao banco por cliente). */
    private List<Cliente> comSaldos(List<Cliente> clientes) {
        if (clientes.isEmpty()) return clientes;
        Map<Long, BigDecimal> saldos = new HashMap<>();
        for (Object[] linha : contasRepo.saldosAbertosPorCliente()) {
            saldos.put(((Number) linha[0]).longValue(), new BigDecimal(linha[1].toString()));
        }
        clientes.forEach(c -> c.setSaldoFiadoAtual(
                saldos.getOrDefault(c.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)));
        return clientes;
    }

    @Transactional
    public Cliente criar(Cliente cliente, Long usuarioId) {
        cliente.setId(null); // garante criação, nunca sobrescreve por engano
        cliente.setCriadoPorId(usuarioId); // autoria vem do token, nunca do corpo da requisição
        Cliente salvo = clienteRepo.save(cliente);
        // Saldo de fiado só nasce das vendas/faturamento — nunca informado no cadastro
        salvo.setSaldoFiadoAtual(BigDecimal.ZERO.setScale(2));
        return salvo;
    }

    @Transactional
    public Cliente atualizar(Long id, Cliente dados) {
        Cliente existente = carregar(id);
        existente.setNome(dados.getNome());
        existente.setCpfCnpj(dados.getCpfCnpj());
        existente.setTipoPessoa(dados.getTipoPessoa());
        existente.setTelefone(dados.getTelefone());
        existente.setEmail(dados.getEmail());
        existente.setEndereco(dados.getEndereco());
        existente.setTipoCliente(dados.getTipoCliente());
        existente.setLimiteCredito(dados.getLimiteCredito());
        // O saldo de fiado não é editável: é sempre calculado das contas a receber.
        return comSaldo(clienteRepo.save(existente));
    }

    @Transactional
    public void inativar(Long id) {
        Cliente c = carregar(id);
        c.setAtivo(false);
        clienteRepo.save(c);
    }

    @Transactional
    public void reativar(Long id) {
        Cliente c = carregar(id);
        c.setAtivo(true);
        clienteRepo.save(c);
    }
}