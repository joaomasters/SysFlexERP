package com.acougue.modules.industrial.estrutura;

import com.acougue.entity.Usuario;
import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.ModuloIndustrialInterceptor;
import com.acougue.modules.industrial.estrutura.EstruturaDto.*;
import com.acougue.repository.UsuarioRepository;
import com.acougue.tenant.TenantContext;
import com.acougue.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Estrutura industrial: cadastros, alocação e habilitação por empresa")
class EstruturaIndustrialTest {

    private final UnidadeRepository unidadeRepo = mock(UnidadeRepository.class);
    private final SetorRepository setorRepo = mock(SetorRepository.class);
    private final CentroTrabalhoRepository centroRepo = mock(CentroTrabalhoRepository.class);
    private final TurnoRepository turnoRepo = mock(TurnoRepository.class);
    private final MaquinaRepository maquinaRepo = mock(MaquinaRepository.class);
    private final AlocacaoOperadorRepository alocacaoRepo = mock(AlocacaoOperadorRepository.class);
    private final UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);
    private final FerramentaRepository ferramentaRepo = mock(FerramentaRepository.class);
    private final TipoMaquinaRepository tipoRepo = mock(TipoMaquinaRepository.class);

    private EstruturaService estrutura;
    private AlocacaoService alocacoes;
    private FerramentaService ferramentas;
    private CentroTrabalho centro;
    private Turno turno;

    @BeforeEach
    void setUp() {
        estrutura = new EstruturaService(unidadeRepo, setorRepo, centroRepo, turnoRepo, maquinaRepo);
        alocacoes = new AlocacaoService(alocacaoRepo, centroRepo, turnoRepo, usuarioRepo);
        ferramentas = new FerramentaService(ferramentaRepo, tipoRepo);
        Unidade unidade = Unidade.builder().id(1L).codigo("FAB1").nome("Fábrica 1").build();
        Setor setor = Setor.builder().id(2L).unidade(unidade).codigo("EXT").nome("Extrusão").build();
        centro = CentroTrabalho.builder().id(3L).setor(setor).codigo("CT1").nome("Prensa 1").build();
        turno = Turno.builder().id(4L).codigo("T1").nome("1º turno").horaInicio(LocalTime.of(6, 0))
                .horaFim(LocalTime.of(14, 0)).build();
        when(unidadeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(turnoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(alocacaoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ferramentaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(centroRepo.findById(3L)).thenReturn(Optional.of(centro));
        when(turnoRepo.findById(4L)).thenReturn(Optional.of(turno));
        when(usuarioRepo.findById(9L)).thenReturn(Optional.of(Usuario.builder().id(9L).nome("Carlos").ativo(true).build()));
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    void codigoDuplicadoDeUnidadeERecusadoSemDiferenciarMaiusculas() {
        when(unidadeRepo.findByCodigoIgnoreCase("FAB1"))
                .thenReturn(Optional.of(Unidade.builder().id(1L).codigo("FAB1").nome("Fábrica 1").build()));
        assertThatThrownBy(() -> estrutura.salvarUnidade(null, new UnidadeIn(" fab1 ", "Outra", null, null), 1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("FAB1");
        // A própria unidade pode ser salva com o mesmo código
        when(unidadeRepo.findById(1L)).thenReturn(Optional.of(Unidade.builder().id(1L).codigo("FAB1").nome("x").build()));
        assertThat(estrutura.salvarUnidade(1L, new UnidadeIn("fab1", "Fábrica Rolândia", null, "pr"), 1L).uf())
                .isEqualTo("PR");
    }

    @Test
    void centroComMaquinasAtivasNaoPodeSerInativado() {
        when(maquinaRepo.countByCentroTrabalhoIdAndStatusNot(3L, "INATIVA")).thenReturn(2L);
        assertThatThrownBy(() -> estrutura.ativarCentro(3L, false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("máquinas ativas");
        assertThat(centro.getAtivo()).isTrue();
    }

    @Test
    void turnoPrecisaDeHorariosDiferentesEDeAoMenosUmDia() {
        when(turnoRepo.findByCodigoIgnoreCase(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> estrutura.salvarTurno(null,
                new TurnoIn("T9", "Igual", LocalTime.of(8, 0), LocalTime.of(8, 0), "1111100")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> estrutura.salvarTurno(null,
                new TurnoIn("T9", "Nenhum dia", LocalTime.of(8, 0), LocalTime.of(17, 0), "0000000")))
                .isInstanceOf(BusinessException.class);
        TurnoOut noturno = estrutura.salvarTurno(null,
                new TurnoIn("t3", "3º turno", LocalTime.of(22, 0), LocalTime.of(6, 0), null));
        assertThat(noturno.minutos()).isEqualTo(480);
        assertThat(noturno.diasSemana()).isEqualTo("1111100");
    }

    @Test
    void periodosQueSeEncontramSobrepoem() {
        LocalDate d1 = LocalDate.of(2026, 1, 1), d10 = LocalDate.of(2026, 1, 10), d11 = LocalDate.of(2026, 1, 11);
        assertThat(AlocacaoService.sobrepoe(d1, d10, d10, null)).isTrue();   // mesmo dia
        assertThat(AlocacaoService.sobrepoe(d1, d10, d11, null)).isFalse();  // começa no dia seguinte
        assertThat(AlocacaoService.sobrepoe(d1, null, d11, d11)).isTrue();   // sem fim
    }

    @Test
    void operadorNaoFicaEmDoisCentrosNoMesmoTurnoNoMesmoPeriodo() {
        when(alocacaoRepo.findByUsuarioIdAndTurnoId(9L, 4L)).thenReturn(List.of(AlocacaoOperador.builder().id(1L)
                .usuarioId(9L).centroTrabalho(centro).turno(turno).vigenteDe(LocalDate.of(2026, 1, 1)).build()));
        assertThatThrownBy(() -> alocacoes.criar(new AlocacaoIn(9L, 3L, 4L, null, LocalDate.of(2026, 6, 1), null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Carlos já está alocado");
    }

    @Test
    void alocacaoNovaSemConflitoEGravada() {
        when(alocacaoRepo.findByUsuarioIdAndTurnoId(9L, 4L)).thenReturn(List.of(AlocacaoOperador.builder().id(1L)
                .usuarioId(9L).centroTrabalho(centro).turno(turno).vigenteDe(LocalDate.of(2026, 1, 1))
                .vigenteAte(LocalDate.of(2026, 5, 31)).build()));
        AlocacaoOut out = alocacoes.criar(new AlocacaoIn(9L, 3L, 4L, " Operador de prensa ", LocalDate.of(2026, 6, 1), null));
        assertThat(out.usuarioNome()).isEqualTo("Carlos");
        assertThat(out.funcao()).isEqualTo("Operador de prensa");
    }

    @Test
    void ferramentaDescartadaNaoVoltaECiclosNaoDiminuem() {
        Ferramenta matriz = Ferramenta.builder().id(1L).codigo("MZ-100").revisao("B").descricao("Matriz perfil U")
                .tipo("MATRIZ").vidaUtilCiclos(8000).ciclosUtilizados(2000).status("DISPONIVEL").build();
        when(ferramentaRepo.findById(1L)).thenReturn(Optional.of(matriz));
        when(ferramentaRepo.buscarPorCodigoERevisao(any(), any())).thenReturn(Optional.empty());

        assertThat(FerramentaService.paraDto(matriz).percentualVidaUsada()).isEqualByComparingTo("25.0");
        assertThatThrownBy(() -> ferramentas.salvar(1L, new FerramentaIn("MZ-100", "Matriz perfil U", "MATRIZ", "B", null,
                8000, 1500, null, null))).isInstanceOf(BusinessException.class).hasMessageContaining("não podem diminuir");

        ferramentas.mudarStatus(1L, "DESCARTADA");
        assertThatThrownBy(() -> ferramentas.mudarStatus(1L, "DISPONIVEL")).isInstanceOf(BusinessException.class);
    }

    @Test
    void empresaSemModuloIndustrialNaoAlcancaAsRotas() {
        TenantRepository tenants = mock(TenantRepository.class);
        ModuloIndustrialInterceptor interceptor = new ModuloIndustrialInterceptor(tenants);
        TenantContext.set("tenant_acougue");
        when(tenants.moduloIndustrialHabilitado("tenant_acougue")).thenReturn(false);
        assertThatThrownBy(() -> interceptor.preHandle(null, null, null)).isInstanceOf(AccessDeniedException.class);

        TenantContext.set("tenant_fabrica");
        when(tenants.moduloIndustrialHabilitado("tenant_fabrica")).thenReturn(true);
        assertThat(interceptor.preHandle(null, null, null)).isTrue();
    }

    @Test
    void custoHoraNegativoEBarradoNaValidacaoDoDto() {
        var validador = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validador.validate(new CentroIn(2L, "CT", "Centro", new BigDecimal("-1")))).hasSize(1);
        assertThat(validador.validate(new CentroIn(2L, "CT", "Centro", BigDecimal.TEN))).isEmpty();
    }
}
