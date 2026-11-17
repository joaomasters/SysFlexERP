package com.acougue.modules.industrial.extrusao;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.engenharia.FichaTecnica;
import com.acougue.modules.industrial.engenharia.FichaTecnicaRepository;
import com.acougue.modules.industrial.estrutura.*;
import com.acougue.modules.industrial.execucao.Apontamento;
import com.acougue.modules.industrial.execucao.ApontamentoRepository;
import com.acougue.modules.industrial.execucao.Consumo;
import com.acougue.modules.industrial.execucao.ExecucaoMapper;
import com.acougue.modules.industrial.extrusao.ExtrusaoDto.*;
import com.acougue.modules.industrial.pcp.OpOperacao;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import com.acougue.modules.industrial.pcp.OrdemProducaoRepository;
import com.acougue.repository.FornecedorRepository;
import com.acougue.repository.ProdutoRepository;
import com.acougue.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Extrusão: ligas, lotes de matéria-prima, receitas, medições e rendimento")
class ExtrusaoTest {

    private final LigaRepository ligaRepo = mock(LigaRepository.class);
    private final LoteMpRepository loteRepo = mock(LoteMpRepository.class);
    private final ConsumoLoteRepository consumoLoteRepo = mock(ConsumoLoteRepository.class);
    private final ProdutoRepository produtoRepo = mock(ProdutoRepository.class);
    private final FornecedorRepository fornecedorRepo = mock(FornecedorRepository.class);
    private final FichaTecnicaRepository fichaRepo = mock(FichaTecnicaRepository.class);
    private final ReceitaRepository receitaRepo = mock(ReceitaRepository.class);
    private final TipoMaquinaRepository tipoRepo = mock(TipoMaquinaRepository.class);
    private final MedicaoRepository medicaoRepo = mock(MedicaoRepository.class);
    private final OrdemProducaoRepository ordemRepo = mock(OrdemProducaoRepository.class);
    private final ApontamentoRepository apontamentoRepo = mock(ApontamentoRepository.class);
    private final MaquinaRepository maquinaRepo = mock(MaquinaRepository.class);
    private final PerdaRepository perdaRepo = mock(PerdaRepository.class);
    private final UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);

    private LigaService ligas;
    private LoteMpService lotes;
    private ReceitaService receitas;
    private MedicaoService medicoes;
    private PerdaService perdas;

    private Liga l6063;
    private Produto tarugo;
    private Produto perfil;
    private TipoMaquina forno;
    private Receita aquecimento;
    private OrdemProducao ordem;

    private static BigDecimal n(String v) { return new BigDecimal(v); }

    @BeforeEach
    void setUp() {
        ExecucaoMapper mapper = new ExecucaoMapper(usuarioRepo);
        ligas = new LigaService(ligaRepo);
        lotes = new LoteMpService(loteRepo, consumoLoteRepo, ligas, produtoRepo, fornecedorRepo, fichaRepo);
        receitas = new ReceitaService(receitaRepo, ligas, tipoRepo, produtoRepo, fichaRepo);
        medicoes = new MedicaoService(medicaoRepo, receitas, ordemRepo, apontamentoRepo, maquinaRepo, mapper);
        perdas = new PerdaService(perdaRepo, ordemRepo, apontamentoRepo, consumoLoteRepo, loteRepo, fichaRepo, medicoes, mapper);

        // Faixas cadastradas pelo teste (o sistema não traz nenhuma)
        l6063 = Liga.builder().id(1L).codigo("6063").descricao("Liga 6063").build();
        l6063.getElementos().add(LigaElemento.builder().liga(l6063).elemento("Mg").minimo(n("0.45")).maximo(n("0.90")).build());
        l6063.getElementos().add(LigaElemento.builder().liga(l6063).elemento("Si").minimo(n("0.20")).maximo(n("0.60")).build());
        l6063.getElementos().add(LigaElemento.builder().liga(l6063).elemento("Fe").maximo(n("0.35")).build());
        tarugo = Produto.builder().id(2L).nome("Tarugo 6063").unidadeMedida("KG").ativo(true).build();
        perfil = Produto.builder().id(1L).nome("Perfil U").unidadeMedida("KG").ativo(true).build();
        forno = TipoMaquina.builder().id(7L).nome("Forno de tarugos").build();

        aquecimento = Receita.builder().id(30L).codigo("AQ-6063").etapa("AQUECIMENTO").liga(l6063).tipoMaquina(forno).revisao(2).build();
        aquecimento.getParametros().add(ReceitaParametro.builder().receita(aquecimento).codigo("TEMP_TARUGO").nome("Temperatura do tarugo")
                .unidade("°C").alvo(n("480")).minimo(n("460")).maximo(n("500")).build());
        aquecimento.getParametros().add(ReceitaParametro.builder().receita(aquecimento).codigo("TEMPO").nome("Tempo no forno")
                .unidade("min").obrigatorio(false).build());

        ordem = OrdemProducao.builder().id(99L).numero("OP000001").produto(perfil).quantidade(n("100")).status("EM_PRODUCAO")
                .inicioPrevisto(LocalDate.now()).fimPrevisto(LocalDate.now()).build();

        when(ligaRepo.findById(1L)).thenReturn(Optional.of(l6063));
        when(produtoRepo.findById(2L)).thenReturn(Optional.of(tarugo));
        when(fichaRepo.findByProdutoId(anyLong())).thenReturn(Optional.empty());
        when(loteRepo.doProdutoComNumero(anyLong(), any())).thenReturn(Optional.empty());
        when(loteRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(consumoLoteRepo.consumidoDoLote(anyLong())).thenReturn(BigDecimal.ZERO);
        when(receitaRepo.findById(30L)).thenReturn(Optional.of(aquecimento));
        when(ordemRepo.findById(99L)).thenReturn(Optional.of(ordem));
        when(medicaoRepo.findByChave(any())).thenReturn(Optional.empty());
        when(medicaoRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepo.findAllById(any())).thenReturn(List.of());
    }

    private LoteMp lote(String status, String mg, String si, String fe) {
        LoteMp l = LoteMp.builder().id(40L).numero("C-881").produto(tarugo).liga(l6063).quantidade(n("1000"))
                .dataRecebimento(LocalDate.now()).status(status).versao(0L).build();
        if (mg != null) l.getComposicao().add(LoteMpComposicao.builder().lote(l).elemento("Mg").percentual(n(mg)).build());
        if (si != null) l.getComposicao().add(LoteMpComposicao.builder().lote(l).elemento("Si").percentual(n(si)).build());
        if (fe != null) l.getComposicao().add(LoteMpComposicao.builder().lote(l).elemento("Fe").percentual(n(fe)).build());
        when(loteRepo.findById(40L)).thenReturn(Optional.of(l));
        when(loteRepo.travar(40L)).thenReturn(Optional.of(l));
        return l;
    }

    @Nested
    @DisplayName("Liga e composição")
    class LigaComposicao {
        @Test
        void desvios_por_elemento() {
            assertThat(LigaService.desvios(l6063, Map.of("Mg", n("0.6"), "Si", n("0.4"), "Fe", n("0.2")))).isEmpty();
            assertThat(LigaService.desvios(l6063, Map.of("Mg", n("0.95"), "Si", n("0.1"), "Fe", n("0.2"))))
                    .containsExactly("Mg 0,95% acima do máximo 0,9%", "Si 0,1% abaixo do mínimo 0,2%");
            assertThat(LigaService.desvios(l6063, Map.of("Mg", n("0.6"), "Si", n("0.4")))).containsExactly("Fe sem valor no certificado");
        }

        @Test
        void recusa_elemento_repetido_e_faixa_invertida() {
            when(ligaRepo.findByCodigoIgnoreCase(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> ligas.salvar(null, new LigaIn("6061", "Liga 6061", null, null, null, List.of(
                    new ElementoIn("Mg", n("0.8"), n("1.2")), new ElementoIn("Mg", null, null))))).hasMessageContaining("mais de uma vez");
            assertThatThrownBy(() -> ligas.salvar(null, new LigaIn("6061", "Liga 6061", null, null, null, List.of(
                    new ElementoIn("Cu", n("0.4"), n("0.15")))))).hasMessageContaining("mínimo é maior");
        }
    }

    @Nested
    @DisplayName("Lote de matéria-prima")
    class Lote {
        @Test
        void so_libera_com_composicao_dentro_da_liga() {
            lote("QUARENTENA", "0.95", "0.4", "0.2");
            assertThatThrownBy(() -> lotes.mudarStatus(40L, new StatusLoteIn("LIBERADO", 0L, null), 9L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Mg 0,95% acima do máximo");
            LoteMp ok = lote("QUARENTENA", "0.6", "0.4", "0.2");
            assertThat(lotes.mudarStatus(40L, new StatusLoteIn("LIBERADO", 0L, null), 9L).status()).isEqualTo("LIBERADO");
            assertThat(ok.getStatusPorId()).isEqualTo(9L);
        }

        @Test
        void bloquear_exige_motivo_e_bloqueado_so_volta_para_quarentena() {
            lote("QUARENTENA", "0.6", "0.4", "0.2");
            assertThatThrownBy(() -> lotes.mudarStatus(40L, new StatusLoteIn("BLOQUEADO", 0L, " "), 9L)).hasMessageContaining("motivo");
            lote("BLOQUEADO", "0.6", "0.4", "0.2");
            assertThatThrownBy(() -> lotes.mudarStatus(40L, new StatusLoteIn("LIBERADO", 0L, null), 9L))
                    .hasMessageContaining("não pode passar");
        }

        @Test
        void lote_liberado_nao_e_editado_e_produto_de_outra_liga_e_recusado() {
            lote("LIBERADO", "0.6", "0.4", "0.2");
            LoteMpIn in = new LoteMpIn("C-881", 2L, 1L, null, null, LocalDate.now(), n("1000"), null, null, null, List.of(), 0L);
            assertThatThrownBy(() -> lotes.salvar(40L, in, 9L)).hasMessageContaining("Só lotes em quarentena");
            Liga outra = Liga.builder().id(5L).codigo("6061").build();
            when(fichaRepo.findByProdutoId(2L)).thenReturn(Optional.of(FichaTecnica.builder().liga(outra).build()));
            assertThatThrownBy(() -> lotes.salvar(null, in, 9L)).hasMessageContaining("é da liga 6061");
        }

        @Test
        void consumo_so_de_lote_liberado_do_mesmo_material_e_com_saldo() {
            lote("QUARENTENA", "0.6", "0.4", "0.2");
            assertThatThrownBy(() -> lotes.exigirConsumivel(40L, 2L, n("10"))).hasMessageContaining("quarentena");
            lote("LIBERADO", "0.6", "0.4", "0.2");
            assertThatThrownBy(() -> lotes.exigirConsumivel(40L, 1L, n("10"))).hasMessageContaining("outro material");
            when(consumoLoteRepo.consumidoDoLote(40L)).thenReturn(n("995"));
            assertThatThrownBy(() -> lotes.exigirConsumivel(40L, 2L, n("10"))).hasMessageContaining("tem só 5");
            assertThat(lotes.exigirConsumivel(40L, 2L, n("5")).getId()).isEqualTo(40L);
        }
    }

    @Nested
    @DisplayName("Receita")
    class ReceitaTeste {
        @Test
        void alvo_fora_da_faixa_e_recusado_e_edicao_sobe_a_revisao() {
            when(receitaRepo.findByCodigoIgnoreCase(any())).thenReturn(Optional.empty());
            when(tipoRepo.findById(7L)).thenReturn(Optional.of(forno));
            when(receitaRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            assertThatThrownBy(() -> receitas.salvar(null, new ReceitaIn("AQ", "Aquecimento", "AQUECIMENTO", 1L, 7L, null, null,
                    List.of(new ParametroIn("T", "Temperatura", "°C", n("520"), n("460"), n("500"), true))), 9L))
                    .hasMessageContaining("alvo está fora da faixa");
            ReceitaOut out = receitas.salvar(30L, new ReceitaIn("AQ-6063", "Aquecimento", "AQUECIMENTO", 1L, 7L, null, null,
                    List.of(new ParametroIn("temp_tarugo", "Temperatura do tarugo", "°C", n("485"), n("465"), n("505"), true))), 9L);
            assertThat(out.revisao()).isEqualTo(3);
            assertThat(out.parametros()).singleElement().satisfies(p -> assertThat(p.codigo()).isEqualTo("TEMP_TARUGO"));
        }
    }

    @Nested
    @DisplayName("Medição")
    class MedicaoTeste {
        private MedicaoIn medicao(String chave, String temp, String observacao) {
            return new MedicaoIn(chave, 99L, null, null, 30L, null, observacao, List.of(new ValorIn("temp_tarugo", n(temp))));
        }

        @Test
        void dentro_da_faixa_grava_com_a_faixa_e_revisao_da_epoca() {
            MedicaoOut out = medicoes.registrar(medicao("m1", "480", null), 9L);
            assertThat(out.foraEspecificacao()).isFalse();
            assertThat(out.receitaRevisao()).isEqualTo(2);
            assertThat(out.origem()).isEqualTo("MANUAL");
            assertThat(out.valores()).singleElement().satisfies(v -> {
                assertThat(v.minimo()).isEqualByComparingTo("460");
                assertThat(v.maximo()).isEqualByComparingTo("500");
                assertThat(v.dentroFaixa()).isTrue();
            });
        }

        @Test
        void fora_da_faixa_exige_a_acao_tomada_e_fica_marcada() {
            assertThatThrownBy(() -> medicoes.registrar(medicao("m2", "515", null), 9L)).hasMessageContaining("ação tomada");
            MedicaoOut out = medicoes.registrar(medicao("m3", "515", "Tarugo voltou ao forno"), 9L);
            assertThat(out.foraEspecificacao()).isTrue();
            assertThat(out.valores().get(0).dentroFaixa()).isFalse();
        }

        @Test
        void parametro_obrigatorio_faltando_ou_desconhecido_e_recusado() {
            assertThatThrownBy(() -> medicoes.registrar(new MedicaoIn("m4", 99L, null, null, 30L, null, null,
                    List.of(new ValorIn("TEMPO", n("90")))), 9L)).hasMessageContaining("Informe Temperatura do tarugo");
            assertThatThrownBy(() -> medicoes.registrar(new MedicaoIn("m5", 99L, null, null, 30L, null, null,
                    List.of(new ValorIn("TEMP_TARUGO", n("480")), new ValorIn("PRESSAO", n("200")))), 9L))
                    .hasMessageContaining("não faz parte da receita");
        }

        @Test
        void maquina_de_outro_tipo_e_recusada() {
            Maquina prensa = Maquina.builder().id(8L).codigo("PR-01").tipoMaquina(TipoMaquina.builder().id(70L).nome("Prensa").build()).build();
            when(maquinaRepo.findById(8L)).thenReturn(Optional.of(prensa));
            assertThatThrownBy(() -> medicoes.registrar(new MedicaoIn("m6", 99L, null, 8L, 30L, null, null,
                    List.of(new ValorIn("TEMP_TARUGO", n("480")))), 9L)).hasMessageContaining("é de outro tipo");
        }

        @Test
        void mesma_chave_devolve_a_medicao_gravada() {
            Medicao gravada = Medicao.builder().id(5L).chave("m1").ordem(ordem).receita(aquecimento).receitaRevisao(2)
                    .etapa("AQUECIMENTO").foraEspecificacao(false).build();
            when(medicaoRepo.findByChave("m1")).thenReturn(Optional.of(gravada));
            assertThat(medicoes.registrar(medicao("m1", "480", null), 9L).id()).isEqualTo(5L);
            verify(medicaoRepo, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("Rendimento metálico")
    class RendimentoTeste {
        @Test
        void converte_em_kg_pela_unidade_e_pela_ficha() {
            FichaTecnica f = FichaTecnica.builder().pesoLinearKgM(n("0.85")).pesoUnitarioKg(n("5.1")).build();
            assertThat(PerdaService.emKg(perfil, n("10"), null)).isEqualByComparingTo("10");
            assertThat(PerdaService.emKg(Produto.builder().unidadeMedida("M").build(), n("100"), f)).isEqualByComparingTo("85");
            assertThat(PerdaService.emKg(Produto.builder().unidadeMedida("UN").build(), n("10"), f)).isEqualByComparingTo("51");
            assertThat(PerdaService.emKg(Produto.builder().unidadeMedida("UN").build(), n("10"), null)).isNull();
        }

        @Test
        void balanco_entrada_produzido_perdas_e_nao_apurado() {
            OpOperacao extrudar = OpOperacao.builder().id(10L).ordem(ordem).sequencia(10).build();
            ordem.getOperacoes().add(extrudar);
            when(fichaRepo.findByProdutoIdIn(any())).thenReturn(List.of());
            when(apontamentoRepo.validosDasOrdens(List.of(99L))).thenReturn(List.of(
                    Apontamento.builder().operacao(extrudar).quantidadeBoa(n("880")).build()));
            Consumo c = Consumo.builder().produto(tarugo).quantidade(n("1000")).loteMpId(40L).build();
            Perda topo = Perda.builder().tipo("TOPO_TARUGO").quantidadeKg(n("60")).estornada(false).build();
            Perda estornada = Perda.builder().tipo("RETALHO").quantidadeKg(n("500")).estornada(true).build();

            Rendimento r = perdas.rendimento(ordem, List.of(c), List.of(topo, estornada));

            assertThat(r.entradaKg()).isEqualByComparingTo("1000");
            assertThat(r.produzidoKg()).isEqualByComparingTo("880");
            assertThat(r.perdasKg()).isEqualByComparingTo("60");
            assertThat(r.naoApuradoKg()).isEqualByComparingTo("60");
            assertThat(r.rendimentoPercentual()).isEqualByComparingTo("88.00");
        }
    }
}
