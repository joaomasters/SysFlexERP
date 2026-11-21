package com.acougue.modules.industrial.expedicao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Entradas e saídas da API de expedição. */
public final class ExpedicaoDto {

    private ExpedicaoDto() {}

    public record ItemIn(@NotNull Long produtoId, @NotNull @Positive BigDecimal quantidade) {}

    public record ExpedicaoIn(@NotNull Long clienteId, @Size(max = 40) String pedidoReferencia, @NotNull LocalDate dataPrevista,
                              @Size(max = 120) String transportadora, @Size(max = 10) String placa,
                              @Size(max = 120) String motorista, String observacao,
                              @NotNull @Size(min = 1, max = 100) List<@Valid @NotNull ItemIn> itens, Long versao) {}

    public record ReservaIn(@NotNull Long itemId, @NotNull Long loteId, @NotNull @Positive BigDecimal quantidade) {}

    public record ConferenciaIn(@NotBlank @Size(max = 30) String codigoLote) {}

    public record VersaoIn(@NotNull Long versao) {}

    public record CancelarIn(@NotNull Long versao, @NotBlank @Size(max = 500) String motivo) {}

    public record ReservaOut(Long id, Long loteId, String loteCodigo, String loteSituacao, BigDecimal quantidade,
                             boolean conferida, LocalDateTime conferidaEm) {}

    public record ItemOut(Long id, Long produtoId, String produtoNome, String unidade, BigDecimal quantidade,
                          BigDecimal reservado, BigDecimal pendente, BigDecimal pesoKg, List<ReservaOut> reservas) {}

    public record ExpedicaoResumo(Long id, String numero, Long clienteId, String clienteNome, String pedidoReferencia,
                                  LocalDate dataPrevista, String status, int itens, Long notaFiscalId) {}

    public record ExpedicaoOut(Long id, String numero, Long clienteId, String clienteNome, String clienteDocumento,
                               String clienteEndereco, String pedidoReferencia, LocalDate dataPrevista, String status,
                               String transportadora, String placa, String motorista, String observacao,
                               LocalDateTime carregadaEm, String carregadaPorNome, Long notaFiscalId, String motivoCancelamento,
                               Long versao, BigDecimal pesoTotalKg, boolean pesoCompleto, List<ItemOut> itens,
                               List<String> pendencias) {}

    public record ClienteOpcao(Long id, String nome, String documento) {}

    public record NotaGerada(Long notaFiscalId, String status) {}
}
