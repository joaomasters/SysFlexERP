package com.acougue.modules.industrial.extrusao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Entradas e saídas da API de extrusão. */
public final class ExtrusaoDto {

    private ExtrusaoDto() {}

    /** Símbolo químico: uma maiúscula e, opcionalmente, uma minúscula (Si, Fe, Mg, B…). */
    static final String SIMBOLO = "^[A-Z][a-z]?$";

    // ── Liga ─────────────────────────────────────────────────────────────
    public record ElementoIn(@NotBlank @Pattern(regexp = SIMBOLO, message = "Elemento inválido (use o símbolo: Si, Fe, Mg…)") String elemento,
                             @DecimalMin("0") @DecimalMax("100") BigDecimal minimo,
                             @DecimalMin("0") @DecimalMax("100") BigDecimal maximo) {}

    public record LigaIn(@NotBlank @Size(max = 20) String codigo, @NotBlank @Size(max = 120) String descricao,
                         @Size(max = 60) String norma, @Positive BigDecimal densidadeGCm3, String observacao,
                         @NotNull @Size(max = 30) List<@Valid @NotNull ElementoIn> elementos) {}

    public record ElementoOut(String elemento, BigDecimal minimo, BigDecimal maximo) {}

    public record LigaOut(Long id, String codigo, String descricao, String norma, BigDecimal densidadeGCm3,
                          String observacao, boolean ativo, List<ElementoOut> elementos) {
        static LigaOut de(Liga l) {
            return new LigaOut(l.getId(), l.getCodigo(), l.getDescricao(), l.getNorma(), l.getDensidadeGCm3(), l.getObservacao(),
                    l.getAtivo(), l.getElementos().stream().map(e -> new ElementoOut(e.getElemento(), e.getMinimo(), e.getMaximo())).toList());
        }
    }

    // ── Lote de matéria-prima ────────────────────────────────────────────
    public record ComposicaoIn(@NotBlank @Pattern(regexp = SIMBOLO, message = "Elemento inválido (use o símbolo: Si, Fe, Mg…)") String elemento,
                               @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal percentual) {}

    public record LoteMpIn(@NotBlank @Size(max = 40) String numero, @NotNull Long produtoId, @NotNull Long ligaId,
                           Long fornecedorId, @Size(max = 60) String certificado, @NotNull LocalDate dataRecebimento,
                           @NotNull @Positive BigDecimal quantidade, @Positive BigDecimal diametroMm,
                           @Positive BigDecimal comprimentoMm, String observacao,
                           @NotNull @Size(max = 30) List<@Valid @NotNull ComposicaoIn> composicao, Long versao) {}

    public record StatusLoteIn(@NotBlank String status, @NotNull Long versao, @Size(max = 500) String motivo) {}

    public record ComposicaoOut(String elemento, BigDecimal percentual, BigDecimal minimo, BigDecimal maximo, boolean dentro) {}

    public record LoteMpOut(Long id, String numero, Long produtoId, String produtoNome, String unidade, Long ligaId,
                            String ligaCodigo, Long fornecedorId, String fornecedorNome, String certificado,
                            LocalDate dataRecebimento, BigDecimal quantidade, BigDecimal consumido, BigDecimal saldo,
                            BigDecimal diametroMm, BigDecimal comprimentoMm, String status, String motivoStatus,
                            LocalDateTime statusEm, String observacao, Long versao, List<ComposicaoOut> composicao,
                            List<String> desvios) {}

    /** Lote que pode ser consumido agora (liberado e com saldo). */
    public record LoteDisponivel(Long id, String numero, String ligaCodigo, BigDecimal saldo, LocalDate dataRecebimento,
                                 String certificado) {}

    // ── Receita ──────────────────────────────────────────────────────────
    public record ParametroIn(@NotBlank @Size(max = 30) String codigo, @NotBlank @Size(max = 100) String nome,
                              @Size(max = 15) String unidade, BigDecimal alvo, BigDecimal minimo, BigDecimal maximo,
                              Boolean obrigatorio) {}

    public record ReceitaIn(@NotBlank @Size(max = 20) String codigo, @NotBlank @Size(max = 120) String descricao,
                            @NotBlank String etapa, @NotNull Long ligaId, @NotNull Long tipoMaquinaId, Long produtoId,
                            String observacao, @NotNull @Size(min = 1, max = 40) List<@Valid @NotNull ParametroIn> parametros) {}

    public record ParametroOut(Long id, String codigo, String nome, String unidade, BigDecimal alvo, BigDecimal minimo,
                               BigDecimal maximo, boolean obrigatorio) {}

    public record ReceitaOut(Long id, String codigo, String descricao, String etapa, Long ligaId, String ligaCodigo,
                             Long tipoMaquinaId, String tipoMaquinaNome, Long produtoId, String produtoNome, Integer revisao,
                             String observacao, boolean ativo, LocalDateTime updatedAt, List<ParametroOut> parametros) {
        static ReceitaOut de(Receita r) {
            return new ReceitaOut(r.getId(), r.getCodigo(), r.getDescricao(), r.getEtapa(), r.getLiga().getId(),
                    r.getLiga().getCodigo(), r.getTipoMaquina().getId(), r.getTipoMaquina().getNome(),
                    r.getProduto() == null ? null : r.getProduto().getId(), r.getProduto() == null ? null : r.getProduto().getNome(),
                    r.getRevisao(), r.getObservacao(), r.getAtivo(), r.getUpdatedAt(),
                    r.getParametros().stream().map(p -> new ParametroOut(p.getId(), p.getCodigo(), p.getNome(), p.getUnidade(),
                            p.getAlvo(), p.getMinimo(), p.getMaximo(), p.getObrigatorio())).toList());
        }
    }

    // ── Medição ──────────────────────────────────────────────────────────
    public record ValorIn(@NotBlank String codigo, @NotNull BigDecimal valor) {}

    public record MedicaoIn(@NotBlank @Size(max = 40) String chave, @NotNull Long ordemId, Long apontamentoId, Long maquinaId,
                            @NotNull Long receitaId, String origem, @Size(max = 500) String observacao,
                            @NotNull @Size(min = 1, max = 40) List<@Valid @NotNull ValorIn> valores) {}

    public record ValorOut(String codigo, String nome, String unidade, BigDecimal valor, BigDecimal alvo, BigDecimal minimo,
                           BigDecimal maximo, boolean dentroFaixa) {}

    public record MedicaoOut(Long id, Long ordemId, String ordemNumero, Long apontamentoId, String maquinaCodigo,
                             Long receitaId, String receitaCodigo, Integer receitaRevisao, String etapa, String origem,
                             LocalDateTime medidoEm, boolean foraEspecificacao, String observacao, String usuarioNome,
                             List<ValorOut> valores) {}

    // ── Perdas e rendimento ──────────────────────────────────────────────
    public record PerdaIn(@NotBlank @Size(max = 40) String chave, @NotNull Long ordemId, Long apontamentoId,
                          @NotBlank String tipo, @NotNull @Positive BigDecimal quantidadeKg, @Size(max = 500) String observacao) {}

    public record EstornoPerdaIn(@NotBlank @Size(max = 500) String motivo) {}

    public record PerdaOut(Long id, Long ordemId, Long apontamentoId, String tipo, BigDecimal quantidadeKg, String observacao,
                           String usuarioNome, LocalDateTime createdAt, boolean estornada, String motivoEstorno) {}

    /**
     * Balanço metálico da OP em kg: o que entrou de metal (consumo), o que saiu bom (produzido), as perdas
     * registradas e a diferença não apurada. Nulo quando alguma quantidade não pode ser convertida em kg.
     */
    public record Rendimento(BigDecimal entradaKg, BigDecimal produzidoKg, BigDecimal perdasKg, BigDecimal naoApuradoKg,
                             BigDecimal rendimentoPercentual, List<String> avisos) {}

    public record LoteConsumido(Long loteId, String numero, String ligaCodigo, BigDecimal quantidade) {}

    public record MetalurgiaOP(List<MedicaoOut> medicoes, List<PerdaOut> perdas, Rendimento rendimento,
                               List<LoteConsumido> lotesConsumidos) {}
}
