package com.acougue.modules.industrial.execucao;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.estoque.EstoqueService;
import com.acougue.modules.industrial.estrutura.*;
import com.acougue.modules.industrial.execucao.ExecucaoDto.*;
import com.acougue.modules.industrial.pcp.*;
import com.acougue.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Execução: apontamentos, paradas e consumo")
class ExecucaoTest {

    private final ApontamentoRepository apontamentoRepo = mock(ApontamentoRepository.class);
    private final ParadaRepository paradaRepo = mock(ParadaRepository.class);
    private final ParadaService paradaService = mock(ParadaService.class);
    private final OpOperacaoRepository operacaoRepo = mock(OpOperacaoRepository.class);
    private final MaquinaRepository maquinaRepo = mock(MaquinaRepository.class);
    private final MaquinaService maquinaService = mock(MaquinaService.class);
    private final OrdemProducaoService ordemService = mock(OrdemProducaoService.class);
    private final OrdemProducaoRepository ordemRepo = mock(OrdemProducaoRepository.class);
    private final ConsumoRepository consumoRepo = mock(ConsumoRepository.class);
    private final EstoqueService estoque = mock(EstoqueService.class);
    private final UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);

    private ApontamentoService apontamentos;
    private ConsumoService consumos;
    private OrdemProducao ordem;
    private Produto perfil;
    private Produto tarugo;
    private OpOperacao extrudar;
    private OpOperacao embalar;
    private Maquina prensa;
    private MotivoParada setup;
    private final AtomicLong ids = new AtomicLong(1);

    private static BigDecimal n(String v) { return new BigDecimal(v); }

    @BeforeEach
    void setUp() {
        ExecucaoMapper mapper = new ExecucaoMapper(usuarioRepo);
        apontamentos = new ApontamentoService(apontamentoRepo, paradaRepo, paradaService, operacaoRepo, maquinaRepo,
                maquinaService, ordemService, estoque, mapper);
        consumos = new ConsumoService(consumoRepo, ordemRepo, apontamentoRepo, estoque, mapper);

        perfil = Produto.builder().id(1L).nome("Perfil U").unidadeMedida("KG").ativo(true).estoqueAtual(BigDecimal.ZERO).build();
        tarugo = Produto.builder().id(2L).nome("Tarugo").unidadeMedida("KG").ativo(true).precoCusto(n("21.5"))
                .estoqueAtual(n("1000")).build();
        Setor setor = Setor.builder().id(1L).nome("Extrusão").unidade(Unidade.builder().id(1L).nome("Fábrica").build()).build();
        CentroTrabalho ctPrensa = CentroTrabalho.builder().id(5L).setor(setor).nome("Prensa").build();
        CentroTrabalho ctEmbalagem = CentroTrabalho.builder().id(6L).setor(setor).nome("Embalagem").build();
        TipoMaquina tipoPrensa = TipoMaquina.builder().id(7L).nome("Prensa").build();
        prensa = Maquina.builder().id(8L).codigo("PR-01").centroTrabalho(ctPrensa).tipoMaquina(tipoPrensa).status("DISPONIVEL").build();

        ordem = OrdemProducao.builder().id(99L).numero("OP000001").produto(perfil).quantidade(n("100")).status("LIBERADA")
                .inicioPrevisto(LocalDate.now()).fimPrevisto(LocalDate.now()).build();
        extrudar = OpOperacao.builder().id(10L).ordem(ordem).sequencia(10).descricao("Extrudar").centroTrabalho(ctPrensa)
                .tipoMaquina(tipoPrensa).tempoSetupMin(BigDecimal.ZERO).tempoPrevistoMin(n("60")).build();
        embalar = OpOperacao.builder().id(20L).ordem(ordem).sequencia(20).descricao("Embalar").centroTrabalho(ctEmbalagem)
                .tempoSetupMin(BigDecimal.ZERO).tempoPrevistoMin(n("30")).build();
        ordem.getOperacoes().addAll(List.of(extrudar, embalar));
        ordem.getMateriais().add(OpMaterial.builder().ordem(ordem).sequencia(10).produto(tarugo).quantidadeLiquida(n("110"))
                .perdaPercentual(n("10")).quantidadePrevista(n("122.222222")).unidade("KG").build());
        setup = MotivoParada.builder().id(3L).codigo("SETUP").descricao("Preparação").categoria("SETUP").planejada(true).build();

        when(operacaoRepo.findById(10L)).thenReturn(Optional.of(extrudar));
        when(operacaoRepo.findById(20L)).thenReturn(Optional.of(embalar));
        when(maquinaRepo.findById(8L)).thenReturn(Optional.of(prensa));
        when(apontamentoRepo.findByChave(any())).thenReturn(Optional.empty());
        when(apontamentoRepo.abertoDoOperador(anyLong())).thenReturn(Optional.empty());
        when(apontamentoRepo.abertoDaMaquina(anyLong())).thenReturn(Optional.empty());
        when(apontamentoRepo.saveAndFlush(any())).thenAnswer(inv -> {
            Apontamento a = inv.getArgument(0);
            if (a.getId() == null) a.setId(ids.getAndIncrement());
            return a;
        });
        when(paradaRepo.findByChave(any())).thenReturn(Optional.empty());
        when(paradaRepo.abertaDaMaquina(anyLong())).thenReturn(Optional.empty());
        when(paradaRepo.abertaDoApontamento(anyLong())).thenReturn(Optional.empty());
        when(paradaRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paradaService.motivoAtivo(3L)).thenReturn(setup);
        when(usuarioRepo.findAllById(any())).thenReturn(List.of());
        when(ordemRepo.findById(99L)).thenReturn(Optional.of(ordem));
        when(consumoRepo.findByChave(any())).thenReturn(Optional.empty());
        when(consumoRepo.saveAndFlush(any())).thenAnswer(inv -> {
            Consumo c = inv.getArgument(0);
            if (c.getId() == null) c.setId(7L);
            return c;
        });
    }

    private Apontamento aberto(OpOperacao op, Maquina m) {
        Apontamento a = Apontamento.builder().id(50L).chave("k-ini").ordem(ordem).operacao(op).maquina(m).operadorId(9L)
                .inicio(LocalDateTime.now().minusMinutes(30)).build();
        when(apontamentoRepo.findById(50L)).thenReturn(Optional.of(a));
        return a;
    }

    private FinalizarIn fim(String boa, String refugo, boolean concluir) {
        return new FinalizarIn("k-fim", n(boa), n(refugo), refugo.equals("0") ? null : "Risco", concluir, null);
    }

    @Nested
    @DisplayName("Início")
    class Inicio {
        @Test
        void primeiro_inicio_poe_a_op_em_producao_e_a_maquina_em_operacao() {
            ApontamentoOut out = apontamentos.iniciar(new IniciarIn("k1", 10L, 8L, null), 9L);
            assertThat(out.status()).isEqualTo("EM_ANDAMENTO");
            assertThat(extrudar.getStatus()).isEqualTo("EM_EXECUCAO");
            verify(ordemService).statusPelaExecucao(ordem, "EM_PRODUCAO", "Início do primeiro apontamento", 9L);
            verify(maquinaService).statusPelaProducao(eq(prensa), eq("EM_OPERACAO"), anyString(), eq(9L));
        }

        @Test
        void mesma_chave_devolve_o_apontamento_ja_gravado() {
            Apontamento existente = aberto(extrudar, prensa);
            when(apontamentoRepo.findByChave("k1")).thenReturn(Optional.of(existente));
            assertThat(apontamentos.iniciar(new IniciarIn("k1", 10L, 8L, null), 9L).id()).isEqualTo(50L);
            verify(apontamentoRepo, never()).saveAndFlush(any());
            verifyNoInteractions(maquinaService);
        }

        @Test
        void recusa_op_pausada_ou_nao_liberada() {
            ordem.setStatus("PAUSADA");
            assertThatThrownBy(() -> apontamentos.iniciar(new IniciarIn("k1", 10L, 8L, null), 9L)).hasMessageContaining("pausada");
            ordem.setStatus("PLANEJADA");
            assertThatThrownBy(() -> apontamentos.iniciar(new IniciarIn("k1", 10L, 8L, null), 9L)).hasMessageContaining("não está liberada");
        }

        @Test
        void um_apontamento_aberto_por_operador() {
            Apontamento outro = aberto(embalar, null);
            when(apontamentoRepo.abertoDoOperador(9L)).thenReturn(Optional.of(outro));
            assertThatThrownBy(() -> apontamentos.iniciar(new IniciarIn("k1", 10L, 8L, null), 9L))
                    .hasMessageContaining("já tem um apontamento aberto");
        }

        @Test
        void maquina_ocupada_parada_ou_em_manutencao_e_recusada() {
            when(paradaRepo.abertaDaMaquina(8L)).thenReturn(Optional.of(Parada.builder().motivo(setup).build()));
            assertThatThrownBy(() -> apontamentos.iniciar(new IniciarIn("k1", 10L, 8L, null), 9L)).hasMessageContaining("está parada");
            when(paradaRepo.abertaDaMaquina(8L)).thenReturn(Optional.empty());
            prensa.setStatus("EM_MANUTENCAO");
            assertThatThrownBy(() -> apontamentos.iniciar(new IniciarIn("k1", 10L, 8L, null), 9L)).hasMessageContaining("em manutenção");
        }

        @Test
        void centro_com_maquinas_exige_escolher_a_maquina() {
            when(maquinaRepo.countByCentroTrabalhoIdAndStatusNot(5L, "INATIVA")).thenReturn(2L);
            assertThatThrownBy(() -> apontamentos.iniciar(new IniciarIn("k1", 10L, null, null), 9L))
                    .hasMessageContaining("Escolha a máquina");
        }
    }

    @Nested
    @DisplayName("Pausa e fim")
    class PausaEFim {
        @Test
        void pausa_abre_parada_e_retomada_soma_o_tempo_parado() {
            Apontamento a = aberto(extrudar, prensa);
            apontamentos.pausar(50L, new PausarIn("p1", 3L, null), 9L, false);
            assertThat(a.getStatus()).isEqualTo("PAUSADO");
            verify(maquinaService).statusPelaProducao(eq(prensa), eq("PARADA"), contains("Preparação"), eq(9L));

            Parada pausa = Parada.builder().apontamento(a).motivo(setup).inicio(LocalDateTime.now().minusMinutes(10)).build();
            when(paradaRepo.abertaDoApontamento(50L)).thenReturn(Optional.of(pausa)).thenReturn(Optional.empty());
            apontamentos.retomar(50L, 9L, false);
            assertThat(a.getStatus()).isEqualTo("EM_ANDAMENTO");
            assertThat(pausa.getFim()).isNotNull();
            assertThat(a.getMinutosParado()).isBetween(n("9.9"), n("10.1"));
        }

        @Test
        void outro_operador_so_com_permissao_de_supervisao() {
            aberto(extrudar, prensa);
            assertThatThrownBy(() -> apontamentos.finalizar(50L, fim("10", "0", false), 77L, false))
                    .hasMessageContaining("outro operador");
            assertThat(apontamentos.finalizar(50L, fim("10", "0", false), 77L, true).status()).isEqualTo("FINALIZADO");
        }

        @Test
        void operacao_intermediaria_nao_mexe_no_estoque() {
            aberto(extrudar, prensa);
            ApontamentoOut out = apontamentos.finalizar(50L, fim("98", "2", false), 9L, false);
            assertThat(out.quantidadeBoa()).isEqualByComparingTo("98");
            assertThat(out.lote()).isNull();
            verifyNoInteractions(estoque);
            verify(maquinaService).statusPelaProducao(eq(prensa), eq("DISPONIVEL"), anyString(), eq(9L));
        }

        @Test
        void refugo_exige_motivo() {
            aberto(extrudar, prensa);
            assertThatThrownBy(() -> apontamentos.finalizar(50L, new FinalizarIn("k-fim", n("10"), n("1"), " ", false, null), 9L, false))
                    .hasMessageContaining("motivo do refugo");
        }

        @Test
        void ultima_operacao_da_entrada_no_estoque_com_lote_da_op_uma_vez_so() {
            Apontamento a = aberto(embalar, null);
            when(apontamentoRepo.lotesDaOrdem(99L)).thenReturn(0L);
            ApontamentoOut out = apontamentos.finalizar(50L, fim("95", "0", false), 9L, false);
            assertThat(out.lote()).isEqualTo("OP000001-L01");
            assertThat(out.entradaEstoque()).isTrue();
            verify(estoque).entrada(perfil, n("95"), null, "ENTRADA_PRODUCAO", "OP000001/AP50", 9L);

            // o tablet reenviou o mesmo "finalizar": nada muda
            assertThat(a.getStatus()).isEqualTo("FINALIZADO");
            apontamentos.finalizar(50L, fim("95", "0", false), 9L, false);
            verify(estoque, times(1)).entrada(any(), any(), any(), any(), any(), any());
            // outra finalização (chave diferente) é recusada
            assertThatThrownBy(() -> apontamentos.finalizar(50L, new FinalizarIn("k-outra", n("1"), n("0"), null, false, null), 9L, false))
                    .hasMessageContaining("já foi finalizado");
        }

        @Test
        void concluir_a_ultima_operacao_pendente_conclui_a_op() {
            ordem.setStatus("EM_PRODUCAO");
            extrudar.setStatus("CONCLUIDA");
            aberto(embalar, null);
            apontamentos.finalizar(50L, fim("100", "0", true), 9L, false);
            assertThat(embalar.getStatus()).isEqualTo("CONCLUIDA");
            verify(ordemService).statusPelaExecucao(ordem, "CONCLUIDA", "Todas as operações concluídas", 9L);
        }
    }

    @Nested
    @DisplayName("Estorno")
    class Estorno {
        @Test
        void estorno_tira_do_estoque_e_reabre_operacao_e_op() {
            ordem.setStatus("CONCLUIDA");
            embalar.setStatus("CONCLUIDA");
            Apontamento a = aberto(embalar, null);
            a.setStatus("FINALIZADO");
            a.setQuantidadeBoa(n("95"));
            a.setEntradaEstoque(true);
            a.setConcluiuOperacao(true);

            ApontamentoOut out = apontamentos.estornar(50L, new EstornoIn("Quantidade errada"), 9L);

            assertThat(out.status()).isEqualTo("ESTORNADO");
            verify(estoque).saida(perfil, n("95"), "SAIDA_ESTORNO_PRODUCAO", "OP000001/AP50", 9L);
            assertThat(embalar.getStatus()).isEqualTo("EM_EXECUCAO");
            verify(ordemService).statusPelaExecucao(eq(ordem), eq("EM_PRODUCAO"), contains("Quantidade errada"), eq(9L));
        }

        @Test
        void so_finalizado_e_estornado_e_nunca_em_op_encerrada() {
            aberto(extrudar, prensa);
            assertThatThrownBy(() -> apontamentos.estornar(50L, new EstornoIn("x"), 9L)).hasMessageContaining("Só apontamentos finalizados");
            Apontamento a = aberto(extrudar, prensa);
            a.setStatus("FINALIZADO");
            ordem.setStatus("ENCERRADA");
            assertThatThrownBy(() -> apontamentos.estornar(50L, new EstornoIn("x"), 9L)).isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("Consumo")
    class ConsumoTeste {
        @Test
        void consumo_da_baixa_no_estoque_com_documento_da_op() {
            ConsumoOut out = consumos.consumir(99L, new ConsumoIn("c1", 2L, n("60"), "CORRIDA-881", null, null), 9L);
            assertThat(out.custoUnitario()).isEqualByComparingTo("21.5");
            assertThat(out.loteMaterial()).isEqualTo("CORRIDA-881");
            verify(estoque).saida(tarugo, n("60"), "SAIDA_CONSUMO_PRODUCAO", "OP000001/CS7", 9L);
        }

        @Test
        void mesma_chave_nao_baixa_duas_vezes() {
            Consumo existente = Consumo.builder().id(7L).chave("c1").ordem(ordem).produto(tarugo).quantidade(n("60")).build();
            when(consumoRepo.findByChave("c1")).thenReturn(Optional.of(existente));
            consumos.consumir(99L, new ConsumoIn("c1", 2L, n("60"), null, null, null), 9L);
            verifyNoInteractions(estoque);
        }

        @Test
        void so_material_da_op_e_so_em_op_liberada_ou_produzindo() {
            assertThatThrownBy(() -> consumos.consumir(99L, new ConsumoIn("c1", 1L, n("1"), null, null, null), 9L))
                    .hasMessageContaining("não faz parte da OP");
            ordem.setStatus("PLANEJADA");
            assertThatThrownBy(() -> consumos.consumir(99L, new ConsumoIn("c2", 2L, n("1"), null, null, null), 9L))
                    .hasMessageContaining("não aceita consumo");
        }

        @Test
        void estorno_devolve_com_o_custo_do_consumo_uma_vez_so() {
            Consumo c = Consumo.builder().id(7L).chave("c1").ordem(ordem).produto(tarugo).quantidade(n("60"))
                    .custoUnitario(n("20")).usuarioId(9L).build();
            when(consumoRepo.findById(7L)).thenReturn(Optional.of(c));
            when(consumoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            assertThat(consumos.estornar(7L, new EstornoIn("Lançado errado"), 9L).estornado()).isTrue();
            consumos.estornar(7L, new EstornoIn("de novo"), 9L);
            verify(estoque, times(1)).entrada(tarugo, n("60"), n("20"), "ENTRADA_ESTORNO_CONSUMO", "OP000001/CS7-EST", 9L);
        }
    }

    @Test
    void minutos_entre_instantes() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 14, 8, 0);
        assertThat(ExecucaoMapper.minutos(t, t.plusMinutes(90).plusSeconds(30))).isEqualByComparingTo("90.50");
        assertThat(ExecucaoMapper.minutos(t, t.minusMinutes(5))).isEqualByComparingTo("0");
    }
}
