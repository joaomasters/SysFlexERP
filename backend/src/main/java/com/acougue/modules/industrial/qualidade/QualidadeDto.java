package com.acougue.modules.industrial.qualidade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Entradas e saídas da API de qualidade e rastreabilidade. */
public final class QualidadeDto {

    private QualidadeDto() {}

    // ── Lote ─────────────────────────────────────────────────────────────
    public record LoteOut(Long id, String codigo, Long produtoId, String produtoNome, String unidade, Long ordemId,
                          String ordemNumero, String origem, BigDecimal quantidadeInicial, BigDecimal saldo,
                          BigDecimal reservado, BigDecimal disponivel, String situacao, String motivoSituacao,
                          LocalDateTime situacaoEm, LocalDateTime createdAt, Long versao, boolean temPlano) {}

    public record SituacaoIn(@NotBlank String situacao, @NotNull Long versao, @Size(max = 500) String motivo) {}

    public record DivisaoIn(@NotNull @Size(min = 1, max = 50) List<@NotNull @Positive BigDecimal> quantidades) {}

    public record ParteUniao(@NotNull Long loteId, @NotNull @Positive BigDecimal quantidade) {}

    public record UniaoIn(@NotNull @Size(min = 2, max = 50) List<@Valid @NotNull ParteUniao> partes) {}

    public record MovimentoOut(String tipo, BigDecimal quantidade, String documento, String usuarioNome, LocalDateTime createdAt) {}

    /** Etiqueta do lote: QR (SVG) com o código, para leitura na conferência e na rastreabilidade. */
    public record EtiquetaOut(String codigo, String produtoNome, String unidade, BigDecimal quantidade, String ordemNumero,
                              LocalDate data, String situacao, String qrSvg) {}

    // ── Plano de inspeção ────────────────────────────────────────────────
    public record CaracteristicaIn(@NotBlank @Size(max = 30) String codigo, @NotBlank @Size(max = 100) String nome,
                                   @NotBlank String tipo, @Size(max = 15) String unidade, BigDecimal minimo,
                                   BigDecimal maximo, Boolean obrigatorio) {}

    public record PlanoIn(@NotBlank @Size(max = 20) String codigo, @NotBlank @Size(max = 120) String descricao,
                          @NotNull Long produtoId, String observacao,
                          @NotNull @Size(min = 1, max = 40) List<@Valid @NotNull CaracteristicaIn> caracteristicas) {}

    public record CaracteristicaOut(Long id, String codigo, String nome, String tipo, String unidade, BigDecimal minimo,
                                    BigDecimal maximo, boolean obrigatorio) {}

    public record PlanoOut(Long id, String codigo, String descricao, Long produtoId, String produtoNome, Integer revisao,
                           String observacao, boolean ativo, LocalDateTime updatedAt, List<CaracteristicaOut> caracteristicas) {
        static PlanoOut de(PlanoInspecao p) {
            return new PlanoOut(p.getId(), p.getCodigo(), p.getDescricao(), p.getProduto().getId(), p.getProduto().getNome(),
                    p.getRevisao(), p.getObservacao(), p.getAtivo(), p.getUpdatedAt(),
                    p.getCaracteristicas().stream().map(c -> new CaracteristicaOut(c.getId(), c.getCodigo(), c.getNome(), c.getTipo(),
                            c.getUnidade(), c.getMinimo(), c.getMaximo(), c.getObrigatorio())).toList());
        }
    }

    // ── Inspeção ─────────────────────────────────────────────────────────
    /** MEDIDA: informe valorNumero. ATRIBUTO: informe conforme (true/false). */
    public record ValorIn(@NotBlank String codigo, BigDecimal valorNumero, Boolean conforme) {}

    public record InspecaoIn(@NotBlank @Size(max = 40) String chave, @NotNull Long loteId, @Positive Integer amostra,
                             @Size(max = 1000) String observacao, @NotNull @Size(min = 1, max = 40) List<@Valid @NotNull ValorIn> valores) {}

    public record ValorOut(String codigo, String nome, String tipo, String unidade, BigDecimal valorNumero, BigDecimal minimo,
                           BigDecimal maximo, boolean conforme) {}

    public record InspecaoOut(Long id, Long loteId, String loteCodigo, String planoCodigo, Integer planoRevisao, String resultado,
                              Integer amostra, String observacao, String inspetorNome, LocalDateTime createdAt,
                              List<ValorOut> valores, String naoConformidadeNumero) {}

    // ── Não conformidade ─────────────────────────────────────────────────
    public record NcIn(@NotBlank String origem, Long loteId, Long ordemId, @NotBlank @Size(max = 2000) String descricao) {}

    public record EncerrarNcIn(String disposicao, @NotBlank @Size(max = 2000) String causa,
                               @NotBlank @Size(max = 2000) String acaoCorretiva, @NotNull Long versao) {}

    public record NcOut(Long id, String numero, String origem, Long loteId, String loteCodigo, Long ordemId, String ordemNumero,
                        Long inspecaoId, String descricao, String status, String disposicao, String causa, String acaoCorretiva,
                        String abertaPorNome, LocalDateTime createdAt, String encerradaPorNome, LocalDateTime encerradaEm,
                        Long versao) {}

    // ── Rastreabilidade ──────────────────────────────────────────────────
    public record OrigemMaterial(Long loteMpId, String numero, String material, String ligaCodigo, String fornecedor,
                                 String certificado, BigDecimal quantidade, String ordemNumero) {}

    public record Destino(String expedicaoNumero, String cliente, String clienteDocumento, LocalDateTime carregadaEm,
                          String loteCodigo, BigDecimal quantidade) {}

    public record LoteResumo(Long id, String codigo, String produtoNome, String situacao, BigDecimal quantidadeInicial,
                             BigDecimal saldo, String ordemNumero, int nivel) {}

    public record RastreioLote(LoteOut lote, String qrSvg, List<LoteResumo> ascendentes, List<LoteResumo> descendentes,
                               List<OrigemMaterial> origens, List<Destino> destinos, List<InspecaoOut> inspecoes,
                               List<NcOut> naoConformidades, List<MovimentoOut> movimentos, long medicoesForaDaFaixa) {}

    public record RastreioLoteMp(Long loteMpId, String numero, String material, String ligaCodigo, String fornecedor,
                                 String certificado, String status, List<String> ordens, List<LoteResumo> lotesProduzidos,
                                 List<Destino> destinos) {}

    public record ResultadoBusca(String tipo, Long id, String codigo, String descricao) {}
}
