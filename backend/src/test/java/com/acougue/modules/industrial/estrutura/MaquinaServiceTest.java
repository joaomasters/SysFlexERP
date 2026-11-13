package com.acougue.modules.industrial.estrutura;

import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.estrutura.EstruturaDto.*;
import com.acougue.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Máquinas industriais: parâmetros, status e histórico")
class MaquinaServiceTest {

    private final TipoMaquinaRepository tipoRepo = mock(TipoMaquinaRepository.class);
    private final MaquinaRepository maquinaRepo = mock(MaquinaRepository.class);
    private final MaquinaParametroRepository maquinaParametroRepo = mock(MaquinaParametroRepository.class);
    private final MaquinaEventoRepository eventoRepo = mock(MaquinaEventoRepository.class);
    private final CentroTrabalhoRepository centroRepo = mock(CentroTrabalhoRepository.class);
    private final TurnoRepository turnoRepo = mock(TurnoRepository.class);
    private final UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);

    private MaquinaService service;
    private TipoMaquina forno;
    private TipoMaquinaParametro tempMax;
    private TipoMaquinaParametro combustivel;
    private CentroTrabalho centro;

    @BeforeEach
    void setUp() {
        service = new MaquinaService(tipoRepo, maquinaRepo, maquinaParametroRepo, eventoRepo, centroRepo, turnoRepo, usuarioRepo);
        forno = TipoMaquina.builder().id(1L).codigo("FORNO-TAR").nome("Forno de tarugos").categoria("FORNO").build();
        tempMax = TipoMaquinaParametro.builder().id(10L).tipoMaquina(forno).codigo("TEMP_MAX").nome("Temperatura máxima")
                .unidade("°C").minimo(new BigDecimal("0")).maximo(new BigDecimal("650")).obrigatorio(true).ordem((short) 0).build();
        combustivel = TipoMaquinaParametro.builder().id(11L).tipoMaquina(forno).codigo("COMBUSTIVEL").nome("Combustível")
                .tipoValor("TEXTO").ordem((short) 1).build();
        forno.setParametros(new ArrayList<>(List.of(tempMax, combustivel)));
        Unidade unidade = Unidade.builder().id(1L).codigo("FAB1").nome("Fábrica 1").build();
        Setor setor = Setor.builder().id(1L).unidade(unidade).codigo("EXT").nome("Extrusão").build();
        centro = CentroTrabalho.builder().id(1L).setor(setor).codigo("CT-EXT").nome("Linha de extrusão").build();

        when(tipoRepo.findById(1L)).thenReturn(Optional.of(forno));
        when(centroRepo.findById(1L)).thenReturn(Optional.of(centro));
        when(maquinaRepo.findByCodigoIgnoreCase(any())).thenReturn(Optional.empty());
        when(maquinaRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(turnoRepo.findAllById(any())).thenReturn(List.of());
    }

    private MaquinaIn entrada(List<ValorParametroIn> parametros) {
        return new MaquinaIn("f-01", "Forno 1", 1L, 1L, new BigDecimal("1500"), "KG_H", null, null, null, null, null,
                null, parametros, List.of(), null);
    }

    @Nested
    @DisplayName("Parâmetros técnicos do tipo")
    class Parametros {

        @Test
        void gravaOsValoresDentroDaFaixaComOCodigoEmMaiusculas() {
            MaquinaOut out = service.salvarMaquina(null, entrada(List.of(
                    new ValorParametroIn(10L, new BigDecimal("520"), null),
                    new ValorParametroIn(11L, null, "Gás natural"))), 5L);

            assertThat(out.codigo()).isEqualTo("F-01");
            assertThat(out.parametros()).extracting(ValorParametroOut::codigo).containsExactly("TEMP_MAX", "COMBUSTIVEL");
            assertThat(out.parametros().get(0).valorNumero()).isEqualByComparingTo("520");
            assertThat(out.parametros().get(1).valorTexto()).isEqualTo("Gás natural");
        }

        @Test
        void recusaValorForaDaFaixaConfigurada() {
            assertThatThrownBy(() -> service.salvarMaquina(null,
                    entrada(List.of(new ValorParametroIn(10L, new BigDecimal("700"), null))), 5L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Temperatura máxima fora da faixa").hasMessageContaining("0 a 650 °C");
        }

        @Test
        void exigeOsParametrosObrigatorios() {
            assertThatThrownBy(() -> service.salvarMaquina(null,
                    entrada(List.of(new ValorParametroIn(11L, null, "Elétrico"))), 5L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("obrigatório Temperatura máxima");
        }

        @Test
        void recusaParametroDeOutroTipoDeMaquina() {
            assertThatThrownBy(() -> service.salvarMaquina(null,
                    entrada(List.of(new ValorParametroIn(10L, new BigDecimal("500"), null),
                            new ValorParametroIn(99L, new BigDecimal("1"), null))), 5L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("não é do tipo Forno de tarugos");
        }

        @Test
        void capacidadeSemUnidadeNaoPassa() {
            MaquinaIn in = new MaquinaIn("F-02", "Forno 2", 1L, 1L, new BigDecimal("10"), null, null, null, null, null,
                    null, null, List.of(new ValorParametroIn(10L, BigDecimal.TEN, null)), List.of(), null);
            assertThatThrownBy(() -> service.salvarMaquina(null, in, 5L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("capacidade junto com a unidade");
        }

        @Test
        void parametroComValorEmMaquinaNaoPodeSairDoTipo() {
            when(tipoRepo.findByCodigoIgnoreCase(any())).thenReturn(Optional.empty());
            when(maquinaParametroRepo.existsByParametroId(11L)).thenReturn(true);
            TipoMaquinaIn semCombustivel = new TipoMaquinaIn("FORNO-TAR", "Forno de tarugos", "FORNO", List.of(
                    new ParametroIn(10L, "TEMP_MAX", "Temperatura máxima", "°C", "NUMERO", BigDecimal.ZERO,
                            new BigDecimal("650"), true)));
            assertThatThrownBy(() -> service.salvarTipo(1L, semCombustivel))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Combustível já tem valor");
        }

        @Test
        void tipoRecusaFaixaInvertida() {
            when(tipoRepo.findByCodigoIgnoreCase(any())).thenReturn(Optional.empty());
            TipoMaquinaIn in = new TipoMaquinaIn("PRENSA", "Prensa", "PRENSA_EXTRUSAO", List.of(
                    new ParametroIn(null, "FORCA", "Força", "tf", "NUMERO", new BigDecimal("10"), new BigDecimal("5"), false)));
            assertThatThrownBy(() -> service.salvarTipo(null, in))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("mínimo é maior que o máximo");
        }
    }

    @Nested
    @DisplayName("Status e concorrência")
    class Status {

        private Maquina maquina;

        @BeforeEach
        void maquina() {
            maquina = Maquina.builder().id(7L).codigo("F-01").descricao("Forno 1").tipoMaquina(forno).centroTrabalho(centro)
                    .status("DISPONIVEL").versao(3L).build();
            when(maquinaRepo.findById(7L)).thenReturn(Optional.of(maquina));
        }

        @Test
        void mudarStatusGravaEventoComAnteriorENovo() {
            service.mudarStatus(7L, new MudarStatusIn("EM_MANUTENCAO", 3L, "Troca de resistência"), 5L);

            ArgumentCaptor<MaquinaEvento> evento = ArgumentCaptor.forClass(MaquinaEvento.class);
            verify(eventoRepo).save(evento.capture());
            assertThat(evento.getValue().getStatusAnterior()).isEqualTo("DISPONIVEL");
            assertThat(evento.getValue().getStatusNovo()).isEqualTo("EM_MANUTENCAO");
            assertThat(evento.getValue().getDescricao()).isEqualTo("Troca de resistência");
            assertThat(evento.getValue().getUsuarioId()).isEqualTo(5L);
            assertThat(maquina.getStatus()).isEqualTo("EM_MANUTENCAO");
        }

        @Test
        void versaoDiferenteDaTelaNaoSobrescreve() {
            assertThatThrownBy(() -> service.mudarStatus(7L, new MudarStatusIn("PARADA", 2L, null), 5L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("alterada por outra pessoa");
            verify(eventoRepo, never()).save(any());
            assertThat(maquina.getStatus()).isEqualTo("DISPONIVEL");
        }

        @Test
        void maquinaEmManutencaoNaoEntraEmOperacao() {
            assertThatThrownBy(() -> MaquinaService.validarTransicao("EM_MANUTENCAO", "EM_OPERACAO"))
                    .isInstanceOf(BusinessException.class);
            MaquinaService.validarTransicao("PARADA", "EM_OPERACAO");
        }

        @Test
        void inativaSoVoltaParaDisponivel() {
            assertThatThrownBy(() -> MaquinaService.validarTransicao("INATIVA", "PARADA")).isInstanceOf(BusinessException.class);
            MaquinaService.validarTransicao("INATIVA", "DISPONIVEL");
        }

        @Test
        void mesmoStatusOuStatusDesconhecidoSaoRecusados() {
            assertThatThrownBy(() -> MaquinaService.validarTransicao("PARADA", "PARADA")).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> MaquinaService.validarTransicao("PARADA", "QUEBRADA")).isInstanceOf(BusinessException.class);
        }

        @Test
        void horimetroNaoVolta() {
            maquina.setHorimetro(new BigDecimal("1200.50"));
            assertThatThrownBy(() -> service.registrarHorimetro(7L, new HorimetroIn(new BigDecimal("1100"), 3L), 5L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("1200.5 h");

            service.registrarHorimetro(7L, new HorimetroIn(new BigDecimal("1250"), 3L), 5L);
            assertThat(maquina.getHorimetro()).isEqualByComparingTo("1250");
            verify(eventoRepo).save(any());
        }

        @Test
        void manutencaoComFimAntesDoInicioNaoPassa() {
            var inicio = java.time.LocalDateTime.of(2026, 11, 10, 8, 0);
            assertThatThrownBy(() -> service.registrarManutencao(7L,
                    new ManutencaoIn("CORRETIVA", "Vazamento", inicio, inicio.minusHours(1), null), 5L))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.registrarManutencao(7L,
                    new ManutencaoIn("PREDITIVA", "x", null, null, null), 5L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("Turno que vira a meia-noite soma as horas do dia seguinte")
    void duracaoDoTurno() {
        assertThat(Turno.minutos(LocalTime.of(6, 0), LocalTime.of(14, 0))).isEqualTo(480);
        assertThat(Turno.minutos(LocalTime.of(22, 0), LocalTime.of(6, 0))).isEqualTo(480);
    }
}
