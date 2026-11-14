package com.acougue.modules.industrial.pcp;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Entradas e saídas da API de ordens de produção e planejamento. */
public final class PcpDto {

    private PcpDto() {}

    public record OrdemIn(@NotNull Long produtoId, @NotNull @Positive BigDecimal quantidade,
                          @Min(1) @Max(5) Integer prioridade, @NotNull LocalDate inicioPrevisto,
                          @NotNull LocalDate fimPrevisto, Long clienteId, @Size(max = 40) String pedidoReferencia,
                          String observacao, Long versao) {}

    public record StatusIn(@NotBlank String status, @NotNull Long versao, @Size(max = 500) String motivo) {}

    public record MaquinaOpcao(Long id, String codigo, String descricao, Long centroTrabalhoId, Long tipoMaquinaId,
                               String status) {}

    public record MaquinaOperacaoIn(Long maquinaId, @NotNull Long versao) {}

    public record OrdemResumo(Long id, String numero, Long produtoId, String produtoCodigo, String produtoNome,
                              String unidade, BigDecimal quantidade, String status, Short prioridade,
                              LocalDate inicioPrevisto, LocalDate fimPrevisto, String clienteNome,
                              String pedidoReferencia, boolean atrasada) {}

    public record MaterialOut(Long produtoId, String codigo, String nome, String unidade, BigDecimal quantidadeLiquida,
                              BigDecimal perdaPercentual, BigDecimal quantidadePrevista, BigDecimal saldoEstoque,
                              BigDecimal falta) {}

    public record OperacaoOut(Long id, Integer sequencia, String descricao, Long centroTrabalhoId,
                              String centroTrabalhoNome, Long tipoMaquinaId, String tipoMaquinaNome, Long maquinaId,
                              String maquinaCodigo, String maquinaDescricao, BigDecimal tempoSetupMin,
                              BigDecimal tempoPrevistoMin, String status) {}

    public record EventoOut(Long id, String statusAnterior, String statusNovo, String motivo, String usuarioNome,
                            LocalDateTime createdAt) {}

    /**
     * Detalhe da OP. Antes da liberação, materiais e operações são uma prévia calculada da engenharia ativa
     * ({@code previa = true}); depois, são os congelados na OP.
     */
    public record OrdemOut(OrdemResumo ordem, Long clienteId, String observacao, Long versao,
                           Integer bomVersao, Integer roteiroVersao, LocalDateTime liberadaEm, boolean previa,
                           List<String> pendenciasLiberacao, List<String> proximosStatus,
                           List<MaterialOut> materiais, List<OperacaoOut> operacoes, List<EventoOut> eventos) {}

    // ── Carga dos centros de trabalho ────────────────────────────────────
    public record CargaOp(Long ordemId, String numero, String produtoNome, String status, BigDecimal minutos,
                          boolean estimada) {}

    public record CargaCentro(Long centroId, String centroCodigo, String centroNome, int maquinas,
                              BigDecimal capacidadeMin, BigDecimal cargaMin, BigDecimal ocupacaoPercentual,
                              List<CargaOp> ordens) {}

    public record CargaOut(LocalDate de, LocalDate ate, List<CargaCentro> centros, List<String> ordensSemRoteiro) {}
}
