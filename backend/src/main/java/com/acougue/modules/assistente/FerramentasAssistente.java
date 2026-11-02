package com.acougue.modules.assistente;

import com.acougue.entity.Modulo;
import com.acougue.entity.Produto;
import com.acougue.repository.ItensVendaRepository;
import com.acougue.security.Acao;
import com.acougue.security.UsuarioAutenticado;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 As únicas consultas que o assistente consegue fazer. A IA não escreve SQL nem
 escolhe tabela: só pede uma destas ferramentas, com argumentos validados aqui.

 - Rodam na requisição do usuário, pelo Hibernate: o schema é o da empresa
   dele, e não existe argumento de empresa para a IA trocar.
 - Cada ferramenta exige o módulo correspondente: quem não vê Relatórios nem
   recebe as ferramentas de vendas.
 - Transação somente leitura, e só valores agregados (nada de cliente, CPF ou
   telefone sai para o Gemini).
 */
@Component
public class FerramentasAssistente {

    static final int MAX_DIAS_PERIODO = 366;
    static final int MAX_LIMITE = 20;

    public record Ferramenta(String nome, String rotulo, Modulo modulo, String descricao, Map<String, Object> parametros) {}

    static final List<Ferramenta> TODAS = List.of(
            new Ferramenta("resumo_vendas", "Resumo de vendas", Modulo.RELATORIOS,
                    "Vendas finalizadas num período: faturamento, quantidade de vendas, ticket médio, custo dos produtos "
                            + "vendidos e lucro bruto (faturamento menos custo). Não inclui despesas como aluguel ou salários.",
                    objeto(Map.of(
                            "data_inicio", texto("Primeiro dia, no formato AAAA-MM-DD"),
                            "data_fim", texto("Último dia (incluído), no formato AAAA-MM-DD")),
                            List.of("data_inicio", "data_fim"))),
            new Ferramenta("produtos_mais_vendidos", "Produtos mais vendidos", Modulo.RELATORIOS,
                    "Produtos com maior faturamento num período, com quantidade, faturamento, custo e lucro bruto de cada um.",
                    objeto(Map.of(
                            "data_inicio", texto("Primeiro dia, no formato AAAA-MM-DD"),
                            "data_fim", texto("Último dia (incluído), no formato AAAA-MM-DD"),
                            "limite", Map.of("type", "integer", "description", "Quantos produtos (1 a 20, padrão 10)")),
                            List.of("data_inicio", "data_fim"))),
            new Ferramenta("custo_produto", "Custo de produto", Modulo.PRODUTOS,
                    "Busca produtos ativos pelo nome, código interno ou código de barras e devolve preço de custo, preço "
                            + "de venda, margem e estoque atual. Até 5 resultados.",
                    objeto(Map.of("busca", texto("Parte do nome, código interno ou código de barras")),
                            List.of("busca"))),
            new Ferramenta("estoque_baixo", "Estoque baixo", Modulo.PRODUTOS,
                    "Produtos ativos com estoque atual abaixo do estoque mínimo cadastrado. Até 20 resultados.",
                    objeto(Map.of(), List.of()))
    );

    private final EntityManager em;
    private final ItensVendaRepository itensRepo;
    private final ObjectMapper json;

    public FerramentasAssistente(EntityManager em, ItensVendaRepository itensRepo, ObjectMapper json) {
        this.em = em;
        this.itensRepo = itensRepo;
        this.json = json;
    }

    /** Só as ferramentas cujo módulo o usuário pode ver. */
    public List<Ferramenta> disponiveis(UsuarioAutenticado usuario) {
        return TODAS.stream().filter(f -> usuario.pode(f.modulo(), Acao.VER)).toList();
    }

    /** Erros de argumento voltam para a IA como resultado, para ela corrigir ou explicar ao usuário. */
    @Transactional(readOnly = true)
    public ObjectNode executar(UsuarioAutenticado usuario, String nome, JsonNode argumentos) {
        Ferramenta ferramenta = TODAS.stream().filter(f -> f.nome().equals(nome)).findFirst().orElse(null);
        if (ferramenta == null || !usuario.pode(ferramenta.modulo(), Acao.VER)) {
            return erro("Ferramenta não disponível para este usuário.");
        }
        JsonNode args = argumentos == null || argumentos.isNull() ? json.createObjectNode() : argumentos;
        try {
            return switch (nome) {
                case "resumo_vendas" -> resumoVendas(periodo(args));
                case "produtos_mais_vendidos" -> maisVendidos(periodo(args), limite(args));
                case "custo_produto" -> custoProduto(busca(args));
                case "estoque_baixo" -> estoqueBaixo();
                default -> erro("Ferramenta não disponível para este usuário.");
            };
        } catch (IllegalArgumentException e) {
            return erro(e.getMessage());
        }
    }

    record Periodo(LocalDate inicio, LocalDate fim) {}

    static Periodo periodo(JsonNode args) {
        LocalDate inicio = data(args, "data_inicio");
        LocalDate fim = data(args, "data_fim");
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("data_fim é anterior a data_inicio.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) >= MAX_DIAS_PERIODO) {
            throw new IllegalArgumentException("Período maior que " + MAX_DIAS_PERIODO + " dias.");
        }
        return new Periodo(inicio, fim);
    }

    private static LocalDate data(JsonNode args, String campo) {
        String valor = args.path(campo).asText("");
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(campo + " deve estar no formato AAAA-MM-DD.");
        }
    }

    static int limite(JsonNode args) {
        int limite = args.path("limite").asInt(10);
        return Math.max(1, Math.min(MAX_LIMITE, limite));
    }

    static String busca(JsonNode args) {
        String busca = args.path("busca").asText("").trim();
        if (busca.length() < 2 || busca.length() > 60) {
            throw new IllegalArgumentException("A busca deve ter entre 2 e 60 caracteres.");
        }
        return busca;
    }

    // Mesmas regras do Relatório de Vendas: só vendas FECHADA, custo = custo_item × quantidade
    private ObjectNode resumoVendas(Periodo p) {
        Object[] linha = em.createQuery("""
                        SELECT COUNT(v), COALESCE(SUM(v.total), 0) FROM Venda v
                        WHERE v.status = 'FECHADA' AND v.dataVenda BETWEEN :inicio AND :fim
                        """, Object[].class)
                .setParameter("inicio", p.inicio().atStartOfDay())
                .setParameter("fim", p.fim().atTime(LocalTime.MAX))
                .getSingleResult();
        long vendas = ((Number) linha[0]).longValue();
        BigDecimal faturamento = (BigDecimal) linha[1];
        BigDecimal custo = itensRepo.somarCMVPeriodo(p.inicio().atStartOfDay(), p.fim().atTime(LocalTime.MAX));
        long semCusto = em.createQuery("""
                        SELECT COUNT(i) FROM ItensVenda i
                        WHERE i.venda.status = 'FECHADA' AND i.venda.dataVenda BETWEEN :inicio AND :fim
                          AND (i.custoItem IS NULL OR i.custoItem = 0)
                        """, Long.class)
                .setParameter("inicio", p.inicio().atStartOfDay())
                .setParameter("fim", p.fim().atTime(LocalTime.MAX))
                .getSingleResult();

        ObjectNode r = json.createObjectNode();
        r.put("data_inicio", p.inicio().toString());
        r.put("data_fim", p.fim().toString());
        r.put("quantidade_vendas", vendas);
        r.put("faturamento", dinheiro(faturamento));
        r.put("ticket_medio", vendas == 0 ? BigDecimal.ZERO : dinheiro(faturamento.divide(BigDecimal.valueOf(vendas), 2, RoundingMode.HALF_UP)));
        r.put("custo_produtos_vendidos", dinheiro(custo));
        r.put("lucro_bruto", dinheiro(faturamento.subtract(custo)));
        r.put("margem_bruta_percentual", percentual(faturamento.subtract(custo), faturamento));
        // Item vendido sem custo cadastrado deixa o lucro maior do que o real
        r.put("itens_vendidos_sem_custo", semCusto);
        return r;
    }

    private ObjectNode maisVendidos(Periodo p, int limite) {
        List<Object[]> linhas = itensRepo.relatorioMargemPorProduto(p.inicio().atStartOfDay(), p.fim().atTime(LocalTime.MAX));
        ObjectNode r = json.createObjectNode();
        r.put("data_inicio", p.inicio().toString());
        r.put("data_fim", p.fim().toString());
        ArrayNode produtos = r.putArray("produtos");
        for (Object[] l : linhas.subList(0, Math.min(limite, linhas.size()))) {
            BigDecimal faturamento = valor(l[3]);
            BigDecimal custo = valor(l[4]);
            ObjectNode item = produtos.addObject();
            item.put("produto", (String) l[1]);
            item.put("quantidade", valor(l[2]).setScale(3, RoundingMode.HALF_UP));
            item.put("faturamento", dinheiro(faturamento));
            item.put("custo", dinheiro(custo));
            item.put("lucro_bruto", dinheiro(faturamento.subtract(custo)));
        }
        return r;
    }

    private ObjectNode custoProduto(String busca) {
        List<Produto> produtos = em.createQuery("""
                        SELECT p FROM Produto p
                        WHERE p.ativo = true
                          AND (LOWER(p.nome) LIKE :nome OR p.codigoInterno = :codigo OR p.ean13 = :codigo)
                        ORDER BY p.nome
                        """, Produto.class)
                .setParameter("nome", "%" + busca.toLowerCase().replace("%", "").replace("_", "") + "%")
                .setParameter("codigo", busca)
                .setMaxResults(5)
                .getResultList();
        ObjectNode r = json.createObjectNode();
        ArrayNode lista = r.putArray("produtos");
        for (Produto p : produtos) {
            ObjectNode item = lista.addObject();
            item.put("produto", p.getNome());
            item.put("codigo_interno", p.getCodigoInterno());
            item.put("unidade", p.getUnidadeMedida());
            if (p.getPrecoCusto() == null) {
                item.putNull("preco_custo");
            } else {
                item.put("preco_custo", dinheiro(p.getPrecoCusto()));
            }
            item.put("preco_venda", dinheiro(p.getPrecoVenda()));
            if (p.getPrecoCusto() != null) {
                item.put("margem_percentual", percentual(p.getPrecoVenda().subtract(p.getPrecoCusto()), p.getPrecoVenda()));
            }
            item.put("estoque_atual", valor(p.getEstoqueAtual()).setScale(3, RoundingMode.HALF_UP));
        }
        if (produtos.isEmpty()) {
            r.put("observacao", "Nenhum produto ativo encontrado com essa busca.");
        }
        return r;
    }

    private ObjectNode estoqueBaixo() {
        List<Produto> produtos = em.createQuery("""
                        SELECT p FROM Produto p
                        WHERE p.ativo = true AND p.estoqueMinimo > 0 AND p.estoqueAtual < p.estoqueMinimo
                        ORDER BY p.nome
                        """, Produto.class)
                .setMaxResults(MAX_LIMITE)
                .getResultList();
        ObjectNode r = json.createObjectNode();
        ArrayNode lista = r.putArray("produtos");
        for (Produto p : produtos) {
            ObjectNode item = lista.addObject();
            item.put("produto", p.getNome());
            item.put("unidade", p.getUnidadeMedida());
            item.put("estoque_atual", valor(p.getEstoqueAtual()).setScale(3, RoundingMode.HALF_UP));
            item.put("estoque_minimo", valor(p.getEstoqueMinimo()).setScale(3, RoundingMode.HALF_UP));
        }
        return r;
    }

    private ObjectNode erro(String mensagem) {
        ObjectNode r = json.createObjectNode();
        r.put("erro", mensagem);
        return r;
    }

    private static BigDecimal valor(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }

    private static BigDecimal dinheiro(BigDecimal v) {
        return valor(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) return BigDecimal.ZERO;
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static Map<String, Object> texto(String descricao) {
        return Map.of("type", "string", "description", descricao);
    }

    private static Map<String, Object> objeto(Map<String, Object> propriedades, List<String> obrigatorios) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new java.util.TreeMap<>(propriedades));
        schema.put("required", obrigatorios);
        return schema;
    }
}
