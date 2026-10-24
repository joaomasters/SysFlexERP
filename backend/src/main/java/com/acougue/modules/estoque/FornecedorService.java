package com.acougue.modules.estoque;

import com.acougue.entity.Fornecedor;
import com.acougue.exception.BusinessException;
import com.acougue.repository.FornecedorRepository;
import com.acougue.repository.ProdutoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FornecedorService {

    private final FornecedorRepository fornecedorRepo;
    private final ProdutoRepository produtoRepo;

    public List<Fornecedor> listar(boolean incluirInativos) {
        return incluirInativos ? fornecedorRepo.findAllByOrderByNomeAsc()
                : fornecedorRepo.findByAtivoTrueOrderByNomeAsc();
    }

    public Fornecedor buscarPorId(Long id) {
        return fornecedorRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fornecedor não encontrado: " + id));
    }

    @Transactional
    public Fornecedor criar(Fornecedor dados, Long usuarioId) {
        normalizar(dados);
        garantirNomeLivre(dados.getNome(), null);
        dados.setId(null); // criação nunca sobrescreve um existente
        dados.setAtivo(true);
        dados.setCriadoPorId(usuarioId); // autoria vem do token
        return fornecedorRepo.save(dados);
    }

    @Transactional
    public Fornecedor atualizar(Long id, Fornecedor dados) {
        Fornecedor existente = buscarPorId(id);
        normalizar(dados);
        garantirNomeLivre(dados.getNome(), id);

        boolean renomeado = !existente.getNome().equals(dados.getNome());
        existente.setNome(dados.getNome());
        existente.setTipoPessoa(dados.getTipoPessoa());
        existente.setCpfCnpj(dados.getCpfCnpj());
        existente.setTelefone(dados.getTelefone());
        existente.setEmail(dados.getEmail());
        existente.setContato(dados.getContato());
        existente.setEndereco(dados.getEndereco());
        Fornecedor salvo = fornecedorRepo.save(existente);

        // produtos.fornecedor guarda o nome do fornecedor vinculado: acompanha o renomear.
        if (renomeado) {
            produtoRepo.atualizarNomeFornecedor(id, salvo.getNome());
        }
        return salvo;
    }

    @Transactional
    public void inativar(Long id) {
        Fornecedor f = buscarPorId(id);
        f.setAtivo(false); // produtos já vinculados continuam apontando para ele; só some do dropdown
        fornecedorRepo.save(f);
    }

    @Transactional
    public void reativar(Long id) {
        Fornecedor f = buscarPorId(id);
        f.setAtivo(true);
        fornecedorRepo.save(f);
    }

    private void normalizar(Fornecedor f) {
        if (f.getNome() == null || f.getNome().isBlank()) {
            throw new BusinessException("Informe o nome do fornecedor.");
        }
        f.setNome(f.getNome().trim());
        if (f.getTipoPessoa() == null || !List.of("PF", "PJ").contains(f.getTipoPessoa())) {
            f.setTipoPessoa("PJ");
        }
    }

    private void garantirNomeLivre(String nome, Long idAtual) {
        fornecedorRepo.buscarPorNomeExato(nome).ifPresent(outro -> {
            if (!outro.getId().equals(idAtual)) {
                throw new BusinessException("Já existe um fornecedor chamado \"" + outro.getNome() + "\".");
            }
        });
    }
}