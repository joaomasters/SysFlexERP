package com.acougue.modules.industrial.execucao;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Entradas e saídas da API de execução (chão de fábrica). */
public final class ExecucaoDto {

    private ExecucaoDto() {}

    // ── Motivos de parada ────────────────────────────────────────────────
    public record MotivoIn(@NotBlank @Size(max = 20) String codigo, @NotBlank @Size(max = 100) String descricao,
                           @NotBlank String categoria, Boolean planejada) {}

    public record MotivoOut(Long id, String codigo, String descricao, String categoria, boolean planejada, boolean ativo) {
        static MotivoOut de(MotivoParada m) {
            return new MotivoOut(m.getId(), m.getCodigo(), m.getDescricao(), m.getCategoria(), m.getPlanejada(), m.getAtivo());
        }
    }

    // ── Comandos do chão de fábrica ──────────────────────────────────────
    /** A {@code chave} é gerada pelo tablet: reenviar o mesmo comando não duplica o registro. */
    public record IniciarIn(@NotBlank @Size(max = 40) String chave, @NotNull Long operacaoId, Long maquinaId,
                            @Size(max = 500) String observacao) {}

    public record PausarIn(@NotBlank @Size(max = 40) String chave, @NotNull Long motivoId, @Size(max = 500) String observacao) {}

    public record FinalizarIn(@NotBlank @Size(max = 40) String chave, @NotNull @PositiveOrZero BigDecimal quantidadeBoa,
                              @NotNull @PositiveOrZero BigDecimal quantidadeRefugo, @Size(max = 200) String motivoRefugo,
                              boolean concluirOperacao, @Size(max = 500) String observacao) {}

    public record EstornoIn(@NotBlank @Size(max = 500) String motivo) {}

    public record ParadaIn(@NotBlank @Size(max = 40) String chave, @NotNull Long maquinaId, @NotNull Long motivoId,
                           @Size(max = 500) String observacao) {}

    public record ConsumoIn(@NotBlank @Size(max = 40) String chave, @NotNull Long produtoId,
                            @NotNull @Positive BigDecimal quantidade, @Size(max = 40) String loteMaterial,
                            Long apontamentoId, @Size(max = 500) String observacao) {}

    // ── Saídas ───────────────────────────────────────────────────────────
    public record ParadaOut(Long id, Long maquinaId, String maquinaCodigo, Long motivoId, String motivoDescricao,
                            String categoria, boolean planejada, Long apontamentoId, Long ordemId, LocalDateTime inicio,
                            LocalDateTime fim, BigDecimal minutos, String observacao, String usuarioNome) {}

    public record ApontamentoOut(Long id, Long ordemId, String ordemNumero, String produtoNome, String unidade,
                                 Long operacaoId, Integer operacaoSequencia, String operacaoDescricao, String centroNome,
                                 Long maquinaId, String maquinaCodigo, Long operadorId, String operadorNome, String status,
                                 LocalDateTime inicio, LocalDateTime fim, BigDecimal minutosParado,
                                 BigDecimal minutosTrabalhados, BigDecimal quantidadeBoa, BigDecimal quantidadeRefugo,
                                 String motivoRefugo, String lote, boolean concluiuOperacao, boolean entradaEstoque,
                                 boolean ultimaOperacao, ParadaOut paradaAberta, String observacao, String motivoEstorno) {}

    public record ConsumoOut(Long id, Long produtoId, String codigo, String nome, String unidade, BigDecimal quantidade,
                             BigDecimal custoUnitario, String loteMaterial, Long apontamentoId, String observacao,
                             String usuarioNome, LocalDateTime createdAt, boolean estornado, String motivoEstorno) {}

    /** Uma operação disponível para apontar num centro de trabalho. */
    public record FilaItem(Long operacaoId, Long ordemId, String ordemNumero, String ordemStatus, Short prioridade,
                           LocalDate fimPrevisto, boolean atrasada, String produtoNome, String unidade,
                           BigDecimal quantidadeOrdem, Integer sequencia, String descricao, String operacaoStatus,
                           Long tipoMaquinaId, String tipoMaquinaNome, Long maquinaPlanejadaId,
                           String maquinaPlanejadaCodigo, BigDecimal produzidoBoa, BigDecimal refugo,
                           boolean ultimaOperacao, String instrucoes) {}

    public record Recurso(Long id, String codigo, String nome) {}

    public record MaquinaChao(Long id, String codigo, String descricao, Long centroTrabalhoId, Long tipoMaquinaId,
                              String status, ParadaOut paradaAberta, Long apontamentoAbertoId, String ordemEmUso) {}

    public record ChaoRecursos(List<Recurso> centros, List<MaquinaChao> maquinas, List<MotivoOut> motivos) {}

    /** Andamento de uma operação: produzido, refugo, tempo real e o que está entre ela e a próxima (WIP). */
    public record OperacaoAndamento(Long operacaoId, Integer sequencia, String descricao, String centroNome, String status,
                                    BigDecimal tempoPrevistoMin, BigDecimal minutosTrabalhados, BigDecimal minutosParado,
                                    BigDecimal quantidadeBoa, BigDecimal quantidadeRefugo, BigDecimal emProcesso,
                                    long apontamentosAbertos) {}

    public record MaterialAndamento(Long produtoId, String codigo, String nome, String unidade, BigDecimal previsto,
                                    BigDecimal consumido, BigDecimal saldoEstoque, BigDecimal sugerido) {}

    public record ExecucaoOut(Long ordemId, String numero, String status, BigDecimal quantidade, String unidade,
                              BigDecimal produzido, BigDecimal refugo, BigDecimal percentualProduzido,
                              List<OperacaoAndamento> operacoes, List<MaterialAndamento> materiais,
                              List<ApontamentoOut> apontamentos, List<ConsumoOut> consumos, List<ParadaOut> paradas) {}
}
