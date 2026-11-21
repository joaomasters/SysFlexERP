package com.acougue.modules.fiscal.saneamento;

import com.acougue.entity.Cliente;
import com.acougue.entity.Fornecedor;
import com.acougue.entity.Produto;
import com.acougue.modules.estoque.DocumentoFiscal;
import com.acougue.modules.fiscal.emissao.ConfiguracaoFiscalService;
import com.acougue.modules.fiscal.emissao.dto.ConfiguracaoFiscalDTO;
import com.acougue.repository.ClienteRepository;
import com.acougue.repository.FornecedorRepository;
import com.acougue.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Saneamento cadastral fiscal: o que hoje impede (ou vai impedir) nota, escrituração e apuração.
 * Os números saem do banco no momento da consulta — nada é estimado. Cada grupo diz o impacto e
 * traz até {@value #EXEMPLOS} registros para o usuário corrigir.
 */
@Service
@RequiredArgsConstructor
public class SaneamentoCadastralService {

    static final int EXEMPLOS = 20;

    private final ConfiguracaoFiscalService configuracao;
    private final ProdutoRepository produtoRepo;
    private final ClienteRepository clienteRepo;
    private final FornecedorRepository fornecedorRepo;

    public record Registro(Long id, String nome, String detalhe) {}

    /** severidade: BLOQUEIA = impede emitir nota hoje; ATENCAO = dado que a escrituração/apuração vai exigir. */
    public record Grupo(String codigo, String titulo, String severidade, String impacto, String ondeCorrigir,
                        long quantidade, List<Registro> exemplos) {}

    public record Saneamento(LocalDateTime geradoEm, long totalPendencias, List<Grupo> grupos) {}

    @Transactional(readOnly = true)
    public Saneamento calcular() {
        List<Grupo> grupos = new ArrayList<>();

        ConfiguracaoFiscalDTO c = configuracao.buscar();
        Set<String> emitente = new LinkedHashSet<>(c.pendenciasNfce());
        emitente.addAll(c.pendenciasNfe());
        grupos.add(new Grupo("EMITENTE", "Dados da empresa emitente", "BLOQUEIA",
                "Sem eles a emissão de NFC-e/NF-e não pode ser ligada.", "Fiscal → Emissão de Notas → Configuração",
                emitente.size(), emitente.stream().map(t -> new Registro(null, t, null)).toList()));

        boolean simples = c.regimeTributario() != 3;
        List<Registro> produtos = new ArrayList<>();
        for (Produto p : produtoRepo.ativosComDadosFiscaisIncompletos()) {
            List<String> faltam = new ArrayList<>();
            if (vazio(p.getNcm())) faltam.add("NCM");
            if (vazio(p.getCfop())) faltam.add("CFOP");
            if (simples && vazio(p.getCsosn())) faltam.add("CSOSN");
            if (!faltam.isEmpty()) produtos.add(new Registro(p.getId(), p.getNome(), "Falta " + String.join(", ", faltam)));
        }
        grupos.add(grupo("PRODUTO_FISCAL", "Produtos ativos sem dados fiscais", "BLOQUEIA",
                "O produto não sai em nota: a venda dele fica só no cupom não fiscal.", "Estoque → Produtos → aba Fiscal",
                produtos));

        List<Registro> clientes = clienteRepo.findByAtivoTrue().stream()
                .filter(x -> !DocumentoFiscal.normalizar(x.getCpfCnpj()).isEmpty() && !documentoValido(x.getCpfCnpj(), x.getTipoPessoa()))
                .map(x -> new Registro(x.getId(), x.getNome(), rotulo(x.getTipoPessoa()) + " inválido: " + x.getCpfCnpj()))
                .toList();
        grupos.add(grupo("CLIENTE_DOCUMENTO", "Clientes com CPF/CNPJ inválido", "BLOQUEIA",
                "NF-e para o cliente e identificação na NFC-e são recusadas.", "Financeiro → Clientes", clientes));

        List<Fornecedor> fornecedores = fornecedorRepo.findByAtivoTrueOrderByNomeAsc();
        List<Registro> fornInvalido = new ArrayList<>();
        List<Registro> fornSemDoc = new ArrayList<>();
        for (Fornecedor f : fornecedores) {
            if (DocumentoFiscal.normalizar(f.getCpfCnpj()).isEmpty()) {
                fornSemDoc.add(new Registro(f.getId(), f.getNome(), "Sem " + rotulo(f.getTipoPessoa())));
            } else if (!documentoValido(f.getCpfCnpj(), f.getTipoPessoa())) {
                fornInvalido.add(new Registro(f.getId(), f.getNome(), rotulo(f.getTipoPessoa()) + " inválido: " + f.getCpfCnpj()));
            }
        }
        grupos.add(grupo("FORNECEDOR_DOCUMENTO", "Fornecedores com CPF/CNPJ inválido", "ATENCAO",
                "A nota de entrada não casa com o fornecedor; crédito e escrituração ficam sem participante válido.",
                "Estoque → Fornecedores", fornInvalido));
        grupos.add(grupo("FORNECEDOR_SEM_DOCUMENTO", "Fornecedores sem CPF/CNPJ", "ATENCAO",
                "Notas de entrada desse fornecedor não podem ser conferidas pelo emitente.",
                "Estoque → Fornecedores", fornSemDoc));

        long total = grupos.stream().mapToLong(Grupo::quantidade).sum();
        return new Saneamento(LocalDateTime.now(), total, grupos);
    }

    private static Grupo grupo(String codigo, String titulo, String severidade, String impacto, String onde, List<Registro> todos) {
        return new Grupo(codigo, titulo, severidade, impacto, onde, todos.size(), todos.stream().limit(EXEMPLOS).toList());
    }

    static boolean documentoValido(String valor, String tipoPessoa) {
        return "PF".equals(tipoPessoa) ? DocumentoFiscal.cpfValido(valor) : DocumentoFiscal.cnpjValido(valor);
    }

    private static String rotulo(String tipoPessoa) {
        return "PF".equals(tipoPessoa) ? "CPF" : "CNPJ";
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
