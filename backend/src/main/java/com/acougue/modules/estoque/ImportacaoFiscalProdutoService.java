package com.acougue.modules.estoque;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.repository.ProdutoRepository;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 Atualização em lote dos dados fiscais (NCM, CEST, CFOP, origem, CSOSN) e da descrição dos produtos,
 a partir de uma planilha. Só o SUPER_ADMIN da empresa.

 - A planilha é lida no navegador; aqui chegam as linhas já separadas por coluna.
 - O produto é achado pelo código interno. Célula em branco = mantém o valor atual (nunca apaga).
 - Primeiro uma prévia (aplicar = false) diz o que muda e o que tem erro; aplicar grava só as linhas
   válidas, tudo numa transação.
 - O Excel tira o zero da esquerda de número: NCM com 7 dígitos e CEST com 6 voltam a ter o zero.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportacaoFiscalProdutoService {

    static final int MAX_LINHAS = 20_000;
    private static final Set<String> CSOSNS = Set.of("101", "102", "103", "201", "202", "203", "300", "400", "500", "900");

    public record LinhaPlanilha(Integer linha, String codigoInterno, String descricao, String ncm, String cest,
                                String cfop, String origem, String csosn) {}

    public record ResultadoLinha(int linha, String codigoInterno, String produto, String status, String mensagem) {}

    public record Resultado(boolean aplicado, int total, int alterados, int semAlteracao, int erros,
                            int aindaIncompletos, List<ResultadoLinha> linhas) {}

    public static final String ALTERAR = "ALTERAR";
    public static final String SEM_ALTERACAO = "SEM_ALTERACAO";
    public static final String ERRO = "ERRO";

    private final ProdutoRepository produtoRepo;

    /** Valores já conferidos de uma linha; null = célula em branco (mantém o atual). */
    private record Valores(String descricao, String ncm, String cest, String cfop, String origem, String csosn) {}

    @Transactional
    public Resultado importar(List<LinhaPlanilha> linhas, boolean aplicar) {
        UsuarioAutenticado usuario = exigirSuperAdmin();
        if (linhas == null || linhas.isEmpty()) {
            throw new BusinessException("A planilha não tem linhas de produto.");
        }
        if (linhas.size() > MAX_LINHAS) {
            throw new BusinessException("A planilha tem " + linhas.size() + " linhas; o máximo é " + MAX_LINHAS + ".");
        }

        List<ResultadoLinha> resultado = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        int alterados = 0, semAlteracao = 0, erros = 0, incompletos = 0;

        for (int i = 0; i < linhas.size(); i++) {
            LinhaPlanilha l = linhas.get(i);
            int numero = l.linha() != null ? l.linha() : i + 2;   // linha 1 = cabeçalho
            String codigo = limpar(l.codigoInterno());
            if (codigo == null) {
                resultado.add(new ResultadoLinha(numero, null, null, ERRO, "Sem código interno."));
                erros++;
                continue;
            }
            if (!vistos.add(codigo.toUpperCase())) {
                resultado.add(new ResultadoLinha(numero, codigo, null, ERRO, "Código repetido na planilha."));
                erros++;
                continue;
            }
            Produto p = produtoRepo.findByCodigoInterno(codigo)
                    .or(() -> produtoRepo.findByCodigoInterno(codigo.toUpperCase()))
                    .orElse(null);
            if (p == null) {
                resultado.add(new ResultadoLinha(numero, codigo, null, ERRO, "Produto não encontrado."));
                erros++;
                continue;
            }

            Valores v;
            try {
                v = conferir(l);
            } catch (BusinessException e) {
                resultado.add(new ResultadoLinha(numero, codigo, p.getNome(), ERRO, e.getMessage()));
                erros++;
                continue;
            }

            List<String> mudancas = new ArrayList<>();
            if (v.descricao() != null && !v.descricao().equals(p.getNome())) {
                mudancas.add("Descrição → " + v.descricao());
                if (aplicar) p.setNome(v.descricao());
            }
            if (v.ncm() != null && !v.ncm().equals(p.getNcm())) {
                mudancas.add("NCM → " + v.ncm());
                if (aplicar) p.setNcm(v.ncm());
            }
            if (v.cest() != null && !v.cest().equals(p.getCest())) {
                mudancas.add("CEST → " + v.cest());
                if (aplicar) p.setCest(v.cest());
            }
            if (v.cfop() != null && !v.cfop().equals(p.getCfop())) {
                mudancas.add("CFOP → " + v.cfop());
                if (aplicar) p.setCfop(v.cfop());
            }
            if (v.origem() != null && !v.origem().equals(p.getOrigem())) {
                mudancas.add("Origem → " + v.origem());
                if (aplicar) p.setOrigem(v.origem());
            }
            if (v.csosn() != null && !v.csosn().equals(p.getCsosn())) {
                mudancas.add("CSOSN → " + v.csosn());
                if (aplicar) p.setCsosn(v.csosn());
            }

            // Como o produto fica depois da linha: o que ainda impede sair em nota
            List<String> faltando = new ArrayList<>();
            if (primeiro(v.ncm(), p.getNcm()) == null) faltando.add("NCM");
            if (primeiro(v.cfop(), p.getCfop()) == null) faltando.add("CFOP");
            if (primeiro(v.csosn(), p.getCsosn()) == null) faltando.add("CSOSN");
            String aviso = faltando.isEmpty() ? "" : " Ainda falta: " + String.join(", ", faltando) + ".";
            if (!faltando.isEmpty()) incompletos++;

            if (mudancas.isEmpty()) {
                resultado.add(new ResultadoLinha(numero, codigo, p.getNome(), SEM_ALTERACAO, ("Nada muda." + aviso).trim()));
                semAlteracao++;
            } else {
                if (aplicar) produtoRepo.save(p);
                resultado.add(new ResultadoLinha(numero, codigo, p.getNome(), ALTERAR, String.join("; ", mudancas) + "." + aviso));
                alterados++;
            }
        }

        if (aplicar) {
            log.info("Planilha fiscal de produtos aplicada por {} em {}: {} produto(s) alterado(s), {} erro(s)",
                    usuario.getLogin(), usuario.getTenantSchema(), alterados, erros);
        }
        return new Resultado(aplicar, linhas.size(), alterados, semAlteracao, erros, incompletos, resultado);
    }

    private static Valores conferir(LinhaPlanilha l) {
        String descricao = limpar(l.descricao()) == null ? null : ProdutoService.normalizarNome(l.descricao());

        String ncm = digitos(l.ncm());
        if (ncm != null && ncm.length() == 7) ncm = "0" + ncm;
        if (ncm != null && ncm.length() != 8) throw new BusinessException("NCM \"" + l.ncm().trim() + "\" deve ter 8 dígitos.");

        String cest = digitos(l.cest());
        if (cest != null && cest.length() == 6) cest = "0" + cest;
        if (cest != null && cest.length() != 7) throw new BusinessException("CEST \"" + l.cest().trim() + "\" deve ter 7 dígitos.");

        String cfop = digitos(l.cfop());
        if (cfop != null && !cfop.matches("[1-7]\\d{3}")) throw new BusinessException("CFOP \"" + l.cfop().trim() + "\" inválido.");

        String origem = digitos(l.origem());
        if (origem != null && !origem.matches("[0-8]")) throw new BusinessException("Origem \"" + l.origem().trim() + "\" inválida (0 a 8).");

        String csosn = digitos(l.csosn());
        if (csosn != null && !CSOSNS.contains(csosn)) throw new BusinessException("CSOSN \"" + l.csosn().trim() + "\" inválido.");

        return new Valores(descricao, ncm, cest, cfop, origem, csosn);
    }

    private static String limpar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Só os dígitos ("0201.30.00" → "02013000"); em branco → null. */
    private static String digitos(String s) {
        if (s == null) return null;
        String d = s.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    private static String primeiro(String a, String b) {
        return a != null ? a : b;
    }

    private static UsuarioAutenticado exigirSuperAdmin() {
        UsuarioAutenticado usuario = ContextoUsuario.atual();
        if (!usuario.isSuperAdmin() || usuario.isJarvis() || usuario.isSomenteLeitura()) {
            throw new AccessDeniedException("Apenas o SUPER_ADMIN da empresa pode importar a planilha fiscal de produtos.");
        }
        return usuario;
    }
}
