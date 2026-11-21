package com.acougue.modules.industrial.gestao;

import com.acougue.entity.Produto;
import com.acougue.modules.industrial.engenharia.*;
import com.acougue.modules.industrial.estrutura.*;
import com.acougue.modules.industrial.execucao.Apontamento;
import com.acougue.modules.industrial.execucao.ApontamentoRepository;
import com.acougue.modules.industrial.execucao.ParadaRepository;
import com.acougue.modules.industrial.expedicao.ExpedicaoRepository;
import com.acougue.modules.industrial.extrusao.ConsumoLoteRepository;
import com.acougue.modules.industrial.gestao.GestaoDto.*;
import com.acougue.modules.industrial.pcp.*;
import com.acougue.modules.industrial.qualidade.InspecaoRepository;
import com.acougue.modules.industrial.qualidade.LoteRepository;
import com.acougue.modules.industrial.qualidade.NaoConformidadeRepository;
import com.acougue.repository.ProdutoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Custos, MRP e indicadores")
class GestaoTest {

    private static BigDecimal n(String v) { return new BigDecimal(v); }

    @Test
    void custo_por_hora_e_desvio() {
        assertThat(CustoService.porHora(n("90"), n("120"))).isEqualByComparingTo("180");
        assertThat(CustoService.porHora(n("90"), null)).isEqualByComparingTo("0");
        assertThat(CustoService.desvio(n("11"), n("10"))).isEqualByComparingTo("10.0");
        assertThat(CustoService.desvio(n("11"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void minutos_dentro_do_periodo() {
        LocalDateTime de = LocalDateTime.of(2026, 11, 16, 0, 0);
        LocalDateTime ate = de.plusDays(1);
        assertThat(IndicadoresIndustriaisService.minutosDentro(de.minusHours(1), de.plusHours(2), de, ate)).isEqualByComparingTo("120");
        assertThat(IndicadoresIndustriaisService.minutosDentro(de.minusHours(3), de.minusHours(1), de, ate)).isEqualByComparingTo("0");
    }

    @Nested
    @DisplayName("MRP")
    class Mrp {
        private final OrdemProducaoRepository ordemRepo = mock(OrdemProducaoRepository.class);
        private final BomRepository bomRepo = mock(BomRepository.class);
        private final RoteiroRepository roteiroRepo = mock(RoteiroRepository.class);
        private final FichaTecnicaRepository fichaRepo = mock(FichaTecnicaRepository.class);
        private final ProdutoRepository produtoRepo = mock(ProdutoRepository.class);
        private final ConsumoLoteRepository consumoRepo = mock(ConsumoLoteRepository.class);
        private final ApontamentoRepository apontamentoRepo = mock(ApontamentoRepository.class);
        private final MrpService mrp = new MrpService(ordemRepo, bomRepo, roteiroRepo, fichaRepo, produtoRepo, consumoRepo, apontamentoRepo);

        private Produto produto(long id, String nome, String estoque) {
            Produto p = Produto.builder().id(id).nome(nome).codigoInterno("P" + id).unidadeMedida("KG").ativo(true)
                    .estoqueAtual(n(estoque)).estoqueMinimo(BigDecimal.ZERO).build();
            when(produtoRepo.findById(id)).thenReturn(Optional.of(p));
            return p;
        }

        private Bom bom(Produto p, Object... componentes) {
            Bom b = Bom.builder().id(100 + p.getId()).produto(p).versao(1).status("ATIVA").quantidadeBase(BigDecimal.ONE).build();
            for (int i = 0; i < componentes.length; i += 2) {
                b.getItens().add(BomItem.builder().bom(b).sequencia(i).componente((Produto) componentes[i])
                        .quantidade(n((String) componentes[i + 1])).perdaPercentual(BigDecimal.ZERO).build());
            }
            when(bomRepo.ativaDoProduto(p.getId())).thenReturn(Optional.of(b));
            return b;
        }

        @Test
        void arredonda_pelo_lote() {
            FichaTecnica f = FichaTecnica.builder().loteMinimo(n("100")).loteMultiplo(n("50")).build();
            assertThat(MrpService.aplicarLote(n("30"), f)).isEqualByComparingTo("100");
            assertThat(MrpService.aplicarLote(n("120"), f)).isEqualByComparingTo("150");
            assertThat(MrpService.aplicarLote(n("120"), null)).isEqualByComparingTo("120");
        }

        @Test
        void explode_em_niveis_desconta_estoque_e_producao_e_sugere_produzir_ou_comprar() {
            Produto perfil = produto(1, "Perfil", "0");
            Produto semi = produto(2, "Semiacabado", "20");
            Produto tarugo = produto(3, "Tarugo", "30");
            bom(perfil, tarugo, "1.1", semi, "1");
            bom(semi, tarugo, "0.5");
            when(bomRepo.ativaDoProduto(3L)).thenReturn(Optional.empty());
            when(roteiroRepo.ativoDoProduto(2L)).thenReturn(Optional.of(Roteiro.builder().id(9L).build()));
            when(fichaRepo.findByProdutoIdIn(any())).thenReturn(List.of(
                    FichaTecnica.builder().produto(semi).loteMultiplo(n("50")).prazoProducaoDias(2).build()));
            LocalDate daqui10 = LocalDate.now().plusDays(10);
            OrdemProducao planejada = OrdemProducao.builder().id(1L).numero("OP000001").produto(perfil).quantidade(n("100"))
                    .status("PLANEJADA").inicioPrevisto(daqui10).fimPrevisto(daqui10).build();
            OrdemProducao semiEmProducao = OrdemProducao.builder().id(2L).numero("OP000002").produto(semi).quantidade(n("10"))
                    .status("LIBERADA").inicioPrevisto(daqui10).fimPrevisto(daqui10).build();
            when(ordemRepo.listar(null)).thenReturn(List.of(planejada, semiEmProducao));
            when(apontamentoRepo.validosDasOrdens(any())).thenReturn(List.of());
            when(consumoRepo.validosDaOrdem(anyLong())).thenReturn(List.of());

            ResultadoMrp r = mrp.calcular(false);

            SugestaoMrp s = r.sugestoes().stream().filter(x -> x.produtoId() == 2L).findFirst().orElseThrow();
            assertThat(s.tipo()).isEqualTo("PRODUZIR");
            // 100 necessários − 20 em estoque − 10 já em produção = 70 → lote múltiplo de 50 → 100
            assertThat(s.liquida()).isEqualByComparingTo("70");
            assertThat(s.sugerida()).isEqualByComparingTo("100");
            assertThat(s.iniciarEm()).isEqualTo(daqui10.minusDays(2));
            SugestaoMrp t = r.sugestoes().stream().filter(x -> x.produtoId() == 3L).findFirst().orElseThrow();
            assertThat(t.tipo()).isEqualTo("COMPRAR");
            // 110 do perfil + 50 da OP sugerida do semiacabado (100 × 0,5) − 30 em estoque = 130
            assertThat(t.necessidadeBruta()).isEqualByComparingTo("160");
            assertThat(t.liquida()).isEqualByComparingTo("130");
            assertThat(t.nivel()).isGreaterThan(s.nivel());
        }
    }

    @Nested
    @DisplayName("OEE")
    class Oee {
        private final ApontamentoRepository apontamentoRepo = mock(ApontamentoRepository.class);
        private final MaquinaRepository maquinaRepo = mock(MaquinaRepository.class);
        private final OrdemProducaoRepository ordemRepo = mock(OrdemProducaoRepository.class);
        private final IndicadoresIndustriaisService ind = new IndicadoresIndustriaisService(apontamentoRepo, mock(ParadaRepository.class), mock(OpEventoRepository.class),
                ordemRepo, mock(InspecaoRepository.class), mock(NaoConformidadeRepository.class), mock(LoteRepository.class),
                mock(ExpedicaoRepository.class), maquinaRepo, mock(FichaTecnicaRepository.class));
        // 16/11/2026 é segunda-feira
        private final LocalDate dia = LocalDate.of(2026, 11, 16);
        private Maquina prensa;
        private OrdemProducao ordem;
        private OpOperacao op;

        private void cenario(int quantos) {
            Turno t = Turno.builder().horaInicio(LocalTime.of(8, 0)).horaFim(LocalTime.of(16, 0)).diasSemana("1000000").build();
            prensa = Maquina.builder().id(8L).codigo("PR-01").descricao("Prensa").build();
            prensa.getTurnos().add(t);
            when(maquinaRepo.ativasComTurnos()).thenReturn(List.of(prensa));
            Produto p = Produto.builder().id(1L).nome("Perfil").unidadeMedida("KG").build();
            ordem = OrdemProducao.builder().id(1L).produto(p).quantidade(n("100")).build();
            // tempo padrão: 100 min para 100 kg sem preparação = 1 min/kg
            op = OpOperacao.builder().id(10L).ordem(ordem).sequencia(10).tempoPrevistoMin(n("100")).tempoSetupMin(BigDecimal.ZERO).build();
            ordem.getOperacoes().add(op);
            List<Apontamento> aps = new ArrayList<>();
            for (int i = 0; i < quantos; i++) {
                LocalDateTime ini = dia.atTime(8 + i, 0);
                aps.add(Apontamento.builder().id((long) i).ordem(ordem).operacao(op).maquina(prensa).inicio(ini).fim(ini.plusMinutes(60))
                        .status("FINALIZADO").quantidadeBoa(n("45")).quantidadeRefugo(n("5")).build());
            }
            when(apontamentoRepo.finalizadosNoPeriodo(any(), any())).thenReturn(aps);
        }

        @Test
        void calcula_com_dados_confiaveis() {
            cenario(3);
            OeeMaquina o = ind.painel(dia, dia).oee().get(0);
            assertThat(o.confiavel()).isTrue();
            // disponibilidade 180/480; performance 150 min padrão / 180 operando; qualidade 135/150
            assertThat(o.disponibilidade()).isEqualByComparingTo("0.3750");
            assertThat(o.performance()).isEqualByComparingTo("0.8333");
            assertThat(o.qualidade()).isEqualByComparingTo("0.9000");
            assertThat(o.oee()).isEqualByComparingTo("0.2812");
        }

        @Test
        void sem_apontamentos_suficientes_nao_mostra_numero() {
            cenario(2);
            OeeMaquina o = ind.painel(dia, dia).oee().get(0);
            assertThat(o.confiavel()).isFalse();
            assertThat(o.oee()).isNull();
            assertThat(o.motivos()).anyMatch(m -> m.contains("Menos de 3 apontamentos"));
        }

        @Test
        void maquina_sem_turnos_nao_mostra_numero() {
            cenario(3);
            prensa.getTurnos().clear();
            OeeMaquina o = ind.painel(dia, dia).oee().get(0);
            assertThat(o.oee()).isNull();
            assertThat(o.motivos()).anyMatch(m -> m.contains("sem turnos"));
        }
    }
}
