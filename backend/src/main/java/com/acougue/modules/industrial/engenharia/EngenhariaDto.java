package com.acougue.modules.industrial.engenharia;

import com.acougue.entity.Produto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Entradas e saídas da API de engenharia de produto. As entidades JPA não saem do backend. */
public final class EngenhariaDto {

    private EngenhariaDto() {}

    /** Produto como aparece nas telas industriais (sem preços: quem vê a engenharia não precisa da margem). */
    public record ProdutoOpcao(Long id, String codigoInterno, String nome, String unidadeMedida, String ean13,
                               BigDecimal estoqueAtual, String tipoItem) {
        static ProdutoOpcao de(Produto p, String tipoItem) {
            return new ProdutoOpcao(p.getId(), p.getCodigoInterno(), p.getNome(), p.getUnidadeMedida(), p.getEan13(),
                    p.getEstoqueAtual(), tipoItem);
        }
    }

    /** Centros de trabalho e tipos de máquina ativos, para montar o roteiro sem exigir acesso à estrutura. */
    public record Recurso(Long id, String codigo, String nome) {}

    public record Recursos(List<Recurso> centros, List<Recurso> tiposMaquina) {}

    // ── Ficha técnica ────────────────────────────────────────────────────
    public record FichaIn(@NotBlank String tipoItem, @Size(max = 40) String codigoDesenho,
                          @Size(max = 10) String revisaoDesenho, @Positive BigDecimal pesoUnitarioKg,
                          @Positive BigDecimal pesoLinearKgM, @Positive BigDecimal comprimentoMm,
                          @Positive BigDecimal loteMinimo, @Positive BigDecimal loteMultiplo,
                          @PositiveOrZero @Max(3650) Integer prazoProducaoDias, Boolean controlaLote, String observacao) {}

    public record FichaOut(Long id, Long produtoId, String produtoCodigo, String produtoNome, String unidade,
                           String tipoItem, String codigoDesenho, String revisaoDesenho, BigDecimal pesoUnitarioKg,
                           BigDecimal pesoLinearKgM, BigDecimal comprimentoMm, BigDecimal loteMinimo,
                           BigDecimal loteMultiplo, Integer prazoProducaoDias, boolean controlaLote,
                           String observacao, LocalDateTime updatedAt) {
        static FichaOut de(FichaTecnica f) {
            Produto p = f.getProduto();
            return new FichaOut(f.getId(), p.getId(), p.getCodigoInterno(), p.getNome(), p.getUnidadeMedida(),
                    f.getTipoItem(), f.getCodigoDesenho(), f.getRevisaoDesenho(), f.getPesoUnitarioKg(),
                    f.getPesoLinearKgM(), f.getComprimentoMm(), f.getLoteMinimo(), f.getLoteMultiplo(),
                    f.getPrazoProducaoDias(), f.getControlaLote(), f.getObservacao(), f.getUpdatedAt());
        }
    }

    // ── Estrutura de produto (BOM) ───────────────────────────────────────
    public record BomItemIn(@NotNull Long componenteId, @NotNull @Positive BigDecimal quantidade,
                            @DecimalMin("0") @DecimalMax(value = "100", inclusive = false) BigDecimal perdaPercentual,
                            @Size(max = 200) String observacao) {}

    public record BomIn(@NotNull Long produtoId, @NotNull @Positive BigDecimal quantidadeBase, String observacao,
                        @NotNull @Size(max = 200) List<@Valid @NotNull BomItemIn> itens) {}

    public record BomItemOut(Long id, Integer sequencia, Long componenteId, String componenteCodigo,
                             String componenteNome, String unidade, BigDecimal quantidade, BigDecimal perdaPercentual,
                             String observacao) {
        static BomItemOut de(BomItem i) {
            Produto c = i.getComponente();
            return new BomItemOut(i.getId(), i.getSequencia(), c.getId(), c.getCodigoInterno(), c.getNome(),
                    c.getUnidadeMedida(), i.getQuantidade(), i.getPerdaPercentual(), i.getObservacao());
        }
    }

    public record BomResumo(Long id, Long produtoId, String produtoCodigo, String produtoNome, String unidade,
                            Integer versao, String status, BigDecimal quantidadeBase, LocalDate vigenciaInicio,
                            LocalDate vigenciaFim, int itens) {
        static BomResumo de(Bom b) {
            Produto p = b.getProduto();
            return new BomResumo(b.getId(), p.getId(), p.getCodigoInterno(), p.getNome(), p.getUnidadeMedida(),
                    b.getVersao(), b.getStatus(), b.getQuantidadeBase(), b.getVigenciaInicio(), b.getVigenciaFim(),
                    b.getItens().size());
        }
    }

    public record BomOut(Long id, Long produtoId, String produtoCodigo, String produtoNome, String unidade,
                         Integer versao, String status, BigDecimal quantidadeBase, LocalDate vigenciaInicio,
                         LocalDate vigenciaFim, String observacao, LocalDateTime ativadaEm, List<BomItemOut> itens) {
        static BomOut de(Bom b) {
            Produto p = b.getProduto();
            return new BomOut(b.getId(), p.getId(), p.getCodigoInterno(), p.getNome(), p.getUnidadeMedida(),
                    b.getVersao(), b.getStatus(), b.getQuantidadeBase(), b.getVigenciaInicio(), b.getVigenciaFim(),
                    b.getObservacao(), b.getAtivadaEm(), b.getItens().stream().map(BomItemOut::de).toList());
        }
    }

    /** Uma linha da explosão multinível: quanto de cada componente vai para a quantidade pedida do produto. */
    public record ExplosaoLinha(int nivel, Long produtoId, String codigo, String nome, String unidade,
                                BigDecimal quantidade, Integer versaoEstrutura) {}

    // ── Roteiro ──────────────────────────────────────────────────────────
    public record OperacaoIn(@NotNull @Positive @Max(9999) Integer sequencia, @NotBlank @Size(max = 120) String descricao,
                             @NotNull Long centroTrabalhoId, Long tipoMaquinaId,
                             @NotNull @PositiveOrZero BigDecimal tempoSetupMin,
                             @NotNull @PositiveOrZero BigDecimal tempoPorUnidadeMin, String instrucoes) {}

    public record RoteiroIn(@NotNull Long produtoId, String observacao,
                            @NotNull @Size(max = 100) List<@Valid @NotNull OperacaoIn> operacoes) {}

    public record OperacaoOut(Long id, Integer sequencia, String descricao, Long centroTrabalhoId,
                              String centroTrabalhoNome, Long tipoMaquinaId, String tipoMaquinaNome,
                              BigDecimal tempoSetupMin, BigDecimal tempoPorUnidadeMin, String instrucoes) {
        static OperacaoOut de(RoteiroOperacao o) {
            return new OperacaoOut(o.getId(), o.getSequencia(), o.getDescricao(), o.getCentroTrabalho().getId(),
                    o.getCentroTrabalho().getNome(), o.getTipoMaquina() == null ? null : o.getTipoMaquina().getId(),
                    o.getTipoMaquina() == null ? null : o.getTipoMaquina().getNome(), o.getTempoSetupMin(),
                    o.getTempoPorUnidadeMin(), o.getInstrucoes());
        }
    }

    public record RoteiroResumo(Long id, Long produtoId, String produtoCodigo, String produtoNome, Integer versao,
                                String status, LocalDate vigenciaInicio, LocalDate vigenciaFim, int operacoes) {
        static RoteiroResumo de(Roteiro r) {
            Produto p = r.getProduto();
            return new RoteiroResumo(r.getId(), p.getId(), p.getCodigoInterno(), p.getNome(), r.getVersao(),
                    r.getStatus(), r.getVigenciaInicio(), r.getVigenciaFim(), r.getOperacoes().size());
        }
    }

    public record RoteiroOut(Long id, Long produtoId, String produtoCodigo, String produtoNome, String unidade,
                             Integer versao, String status, LocalDate vigenciaInicio, LocalDate vigenciaFim,
                             String observacao, LocalDateTime ativadoEm, List<OperacaoOut> operacoes) {
        static RoteiroOut de(Roteiro r) {
            Produto p = r.getProduto();
            return new RoteiroOut(r.getId(), p.getId(), p.getCodigoInterno(), p.getNome(), p.getUnidadeMedida(),
                    r.getVersao(), r.getStatus(), r.getVigenciaInicio(), r.getVigenciaFim(), r.getObservacao(),
                    r.getAtivadoEm(), r.getOperacoes().stream().map(OperacaoOut::de).toList());
        }
    }
}
