package com.acougue.modules.industrial.estrutura;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Entradas e saídas da API da estrutura industrial. As entidades JPA não saem do backend. */
public final class EstruturaDto {

    private EstruturaDto() {}

    // ── Unidade, setor e centro de trabalho ──────────────────────────────
    public record UnidadeIn(@NotBlank @Size(max = 20) String codigo, @NotBlank @Size(max = 120) String nome,
                            @Size(max = 60) String cidade, @Size(max = 2) String uf) {}

    public record UnidadeOut(Long id, String codigo, String nome, String cidade, String uf, boolean ativo) {
        static UnidadeOut de(Unidade u) {
            return new UnidadeOut(u.getId(), u.getCodigo(), u.getNome(), u.getCidade(), u.getUf(), u.getAtivo());
        }
    }

    public record SetorIn(@NotNull Long unidadeId, @NotBlank @Size(max = 20) String codigo,
                          @NotBlank @Size(max = 120) String nome) {}

    public record SetorOut(Long id, Long unidadeId, String unidadeNome, String codigo, String nome, boolean ativo) {
        static SetorOut de(Setor s) {
            return new SetorOut(s.getId(), s.getUnidade().getId(), s.getUnidade().getNome(), s.getCodigo(), s.getNome(),
                    s.getAtivo());
        }
    }

    public record CentroIn(@NotNull Long setorId, @NotBlank @Size(max = 20) String codigo,
                           @NotBlank @Size(max = 120) String nome, @PositiveOrZero BigDecimal custoHora) {}

    public record CentroOut(Long id, Long setorId, String setorNome, Long unidadeId, String unidadeNome, String codigo,
                            String nome, BigDecimal custoHora, boolean ativo) {
        static CentroOut de(CentroTrabalho c) {
            Setor s = c.getSetor();
            return new CentroOut(c.getId(), s.getId(), s.getNome(), s.getUnidade().getId(), s.getUnidade().getNome(),
                    c.getCodigo(), c.getNome(), c.getCustoHora(), c.getAtivo());
        }
    }

    // ── Turno ────────────────────────────────────────────────────────────
    public record TurnoIn(@NotBlank @Size(max = 20) String codigo, @NotBlank @Size(max = 60) String nome,
                          @NotNull LocalTime horaInicio, @NotNull LocalTime horaFim,
                          @Pattern(regexp = "^[01]{7}$", message = "Dias da semana inválidos") String diasSemana) {}

    public record TurnoOut(Long id, String codigo, String nome, LocalTime horaInicio, LocalTime horaFim,
                           String diasSemana, long minutos, boolean ativo) {
        static TurnoOut de(Turno t) {
            return new TurnoOut(t.getId(), t.getCodigo(), t.getNome(), t.getHoraInicio(), t.getHoraFim(),
                    t.getDiasSemana(), Turno.minutos(t.getHoraInicio(), t.getHoraFim()), t.getAtivo());
        }
    }

    // ── Tipo de máquina e parâmetros ─────────────────────────────────────
    public record ParametroIn(Long id, @NotBlank @Size(max = 30) String codigo, @NotBlank @Size(max = 100) String nome,
                              @Size(max = 15) String unidade, String tipoValor, BigDecimal minimo, BigDecimal maximo,
                              Boolean obrigatorio) {}

    public record ParametroOut(Long id, String codigo, String nome, String unidade, String tipoValor,
                               BigDecimal minimo, BigDecimal maximo, boolean obrigatorio) {
        static ParametroOut de(TipoMaquinaParametro p) {
            return new ParametroOut(p.getId(), p.getCodigo(), p.getNome(), p.getUnidade(), p.getTipoValor(),
                    p.getMinimo(), p.getMaximo(), p.getObrigatorio());
        }
    }

    public record TipoMaquinaIn(@NotBlank @Size(max = 20) String codigo, @NotBlank @Size(max = 100) String nome,
                                @NotBlank String categoria, @Valid List<ParametroIn> parametros) {}

    public record TipoMaquinaOut(Long id, String codigo, String nome, String categoria, boolean ativo,
                                 List<ParametroOut> parametros) {
        static TipoMaquinaOut de(TipoMaquina t) {
            return new TipoMaquinaOut(t.getId(), t.getCodigo(), t.getNome(), t.getCategoria(), t.getAtivo(),
                    t.getParametros().stream().map(ParametroOut::de).toList());
        }
    }

    // ── Máquina ──────────────────────────────────────────────────────────
    public record ValorParametroIn(@NotNull Long parametroId, BigDecimal valorNumero, @Size(max = 200) String valorTexto) {}

    public record ValorParametroOut(Long parametroId, String codigo, String nome, String unidade,
                                    BigDecimal valorNumero, String valorTexto) {}

    public record MaquinaIn(@NotBlank @Size(max = 20) String codigo, @NotBlank @Size(max = 150) String descricao,
                            @NotNull Long tipoMaquinaId, @NotNull Long centroTrabalhoId,
                            @Positive BigDecimal capacidade, String unidadeCapacidade,
                            @PositiveOrZero BigDecimal custoHora,
                            @Size(max = 80) String fabricante, @Size(max = 80) String modelo,
                            @Size(max = 60) String numeroSerie, @Min(1900) @Max(2100) Short anoFabricacao,
                            String observacao, @Valid List<ValorParametroIn> parametros, List<Long> turnoIds,
                            /** Versão que a tela carregou (bloqueio otimista); nula ao criar. */
                            Long versao) {}

    public record MaquinaOut(Long id, String codigo, String descricao, Long tipoMaquinaId, String tipoMaquinaNome,
                             String categoria, Long centroTrabalhoId, String centroTrabalhoNome, String setorNome,
                             String unidadeNome, BigDecimal capacidade, String unidadeCapacidade, String status,
                             BigDecimal custoHora, BigDecimal horimetro, String fabricante, String modelo,
                             String numeroSerie, Short anoFabricacao, String observacao, Long versao,
                             List<ValorParametroOut> parametros, List<Long> turnoIds) {}

    public record MudarStatusIn(@NotBlank String status, @NotNull Long versao, @Size(max = 500) String motivo) {}

    public record HorimetroIn(@NotNull @PositiveOrZero BigDecimal valor, @NotNull Long versao) {}

    public record ManutencaoIn(@NotBlank String tipo, @NotBlank @Size(max = 2000) String descricao,
                               LocalDateTime inicio, LocalDateTime fim, @PositiveOrZero BigDecimal custo) {}

    public record EventoOut(Long id, String tipo, String statusAnterior, String statusNovo, BigDecimal horimetro,
                            BigDecimal custo, LocalDateTime inicio, LocalDateTime fim, String descricao,
                            String usuarioNome, LocalDateTime createdAt) {}

    // ── Ferramenta / matriz ──────────────────────────────────────────────
    public record FerramentaIn(@NotBlank @Size(max = 30) String codigo, @NotBlank @Size(max = 150) String descricao,
                               @NotBlank String tipo, @Size(max = 10) String revisao, Long tipoMaquinaId,
                               @Positive Integer vidaUtilCiclos, @PositiveOrZero Integer ciclosUtilizados,
                               @PositiveOrZero BigDecimal custoAquisicao, String observacao) {}

    public record FerramentaOut(Long id, String codigo, String descricao, String tipo, String revisao,
                                Long tipoMaquinaId, String tipoMaquinaNome, Integer vidaUtilCiclos,
                                int ciclosUtilizados, BigDecimal percentualVidaUsada, String status,
                                BigDecimal custoAquisicao, String observacao) {}

    public record StatusIn(@NotBlank String status) {}

    // ── Alocação de operadores ───────────────────────────────────────────
    public record AlocacaoIn(@NotNull Long usuarioId, @NotNull Long centroTrabalhoId, @NotNull Long turnoId,
                             @Size(max = 60) String funcao, @NotNull LocalDate vigenteDe, LocalDate vigenteAte) {}

    public record AlocacaoOut(Long id, Long usuarioId, String usuarioNome, Long centroTrabalhoId,
                              String centroTrabalhoNome, Long turnoId, String turnoNome, String funcao,
                              LocalDate vigenteDe, LocalDate vigenteAte, boolean vigente) {}

    public record EncerrarAlocacaoIn(@NotNull LocalDate vigenteAte) {}

    public record OperadorOut(Long id, String nome) {}
}
