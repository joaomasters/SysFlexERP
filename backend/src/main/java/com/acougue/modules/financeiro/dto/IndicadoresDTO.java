package com.acougue.modules.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Respostas dos relatórios de indicadores (IndicadoresService). */
public final class IndicadoresDTO {

    private IndicadoresDTO() {}

    /** Vendas fechadas por hora (0–23) e por dia da semana (1 = segunda … 7 = domingo). */
    public record Horarios(List<Faixa> porHora, List<Faixa> porDiaSemana) {}

    public record Faixa(int chave, long quantidade, BigDecimal total) {}

    /** Curva ABC: produtos do maior para o menor faturamento, com o acumulado e a classe. */
    public record ProdutoAbc(Long produtoId, String nome, String tipoProduto, BigDecimal quantidade,
                             BigDecimal faturamento, BigDecimal custo, BigDecimal margem, BigDecimal percentualMargem,
                             BigDecimal participacao, BigDecimal acumulado, String classe) {}

    public record MargemTipo(String tipoProduto, BigDecimal faturamento, BigDecimal custo, BigDecimal margem,
                             BigDecimal percentualMargem, BigDecimal participacao, long produtos) {}

    public record Operador(Long operadorId, String nome, long vendas, BigDecimal total, BigDecimal ticketMedio,
                           BigDecimal descontos, long canceladas) {}

    public record FechamentoCaixa(Long caixaId, String operador, LocalDateTime abertura, LocalDateTime fechamento,
                                  BigDecimal esperado, BigDecimal informado, BigDecimal diferenca) {}

    public record Inadimplencia(BigDecimal totalEmAberto, BigDecimal totalVencido, List<FaixaAtraso> faixas,
                                List<Devedor> maioresDevedores) {}

    public record FaixaAtraso(String faixa, long contas, BigDecimal valor) {}

    public record Devedor(Long clienteId, String nome, long contas, BigDecimal valor, BigDecimal vencido,
                          LocalDate vencimentoMaisAntigo) {}
}
