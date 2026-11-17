package com.acougue.modules.industrial.gestao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Saídas de custos por OP, MRP e indicadores (tudo calculado; nada é gravado). */
public final class GestaoDto {

    private GestaoDto() {}

    // ── Custo da OP ──────────────────────────────────────────────────────
    public record Parcela(BigDecimal materiais, BigDecimal processo, BigDecimal total, BigDecimal unitario) {}

    public record LinhaMaterial(Long produtoId, String nome, String unidade, BigDecimal quantidadePrevista,
                                BigDecimal quantidadeConsumida, BigDecimal custoPrevisto, BigDecimal custoReal) {}

    public record LinhaProcesso(Integer sequencia, String descricao, String centroNome, BigDecimal custoHora,
                                BigDecimal minutosPrevistos, BigDecimal minutosReais, BigDecimal custoPrevisto, BigDecimal custoReal) {}

    public record CustoOP(Long ordemId, String numero, String produtoNome, String unidade, String status, BigDecimal quantidade,
                          BigDecimal produzido, Parcela estimado, Parcela real, BigDecimal desvioPercentual,
                          List<LinhaMaterial> materiais, List<LinhaProcesso> processo, List<String> avisos) {}

    public record CustoResumo(Long ordemId, String numero, String produtoNome, String unidade, String status,
                              BigDecimal quantidade, BigDecimal produzido, BigDecimal estimadoUnitario,
                              BigDecimal realUnitario, BigDecimal desvioPercentual, int avisos) {}

    // ── MRP ──────────────────────────────────────────────────────────────
    public record SugestaoMrp(Long produtoId, String codigo, String nome, String unidade, String tipo, int nivel,
                              BigDecimal necessidadeBruta, BigDecimal estoque, BigDecimal emProducao,
                              BigDecimal estoqueMinimo, BigDecimal liquida, BigDecimal sugerida, LocalDate necessarioEm,
                              LocalDate iniciarEm, List<String> origens, String observacao) {}

    public record ResultadoMrp(LocalDate calculadoEm, int ordensConsideradas, boolean incluiuEstoqueMinimo,
                               List<SugestaoMrp> sugestoes, List<String> avisos) {}

    // ── Indicadores ──────────────────────────────────────────────────────
    public record ProducaoProduto(Long produtoId, String nome, String unidade, BigDecimal boa, BigDecimal refugo) {}

    public record ParadaMotivo(String motivo, String categoria, boolean planejada, BigDecimal minutos, long ocorrencias) {}

    public record ParadaMaquina(String maquinaCodigo, BigDecimal minutosPerda, BigDecimal minutosPlanejados) {}

    /** OEE de uma máquina: só calculado quando os dados permitem; senão, os motivos dizem o que falta. */
    public record OeeMaquina(Long maquinaId, String codigo, String descricao, BigDecimal minutosPlanejados,
                             BigDecimal minutosOperando, long apontamentos, BigDecimal disponibilidade,
                             BigDecimal performance, BigDecimal qualidade, BigDecimal oee, boolean confiavel,
                             List<String> motivos) {}

    public record Painel(LocalDate de, LocalDate ate,
                         BigDecimal refugoPercentual, long apontamentos, List<ProducaoProduto> producao,
                         long opsConcluidas, long opsConcluidasNoPrazo, long opsAbertasAtrasadas, long opsAbertas,
                         BigDecimal minutosParadaPerda, BigDecimal minutosParadaPlanejada, List<ParadaMotivo> paradasPorMotivo,
                         List<ParadaMaquina> paradasPorMaquina,
                         long inspecoesAprovadas, long inspecoesReprovadas, long ncsAbertas, long lotesBloqueados, long lotesEmQuarentena,
                         long expedicoesCarregadas, BigDecimal kgExpedidos, boolean kgCompleto,
                         List<OeeMaquina> oee) {}
}
