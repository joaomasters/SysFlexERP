package com.acougue.modules.industrial.pcp;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.engenharia.*;
import com.acougue.modules.industrial.estrutura.*;
import com.acougue.modules.industrial.pcp.PcpDto.*;
import com.acougue.repository.ClienteRepository;
import com.acougue.repository.ProdutoRepository;
import com.acougue.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@DisplayName("Ordens de produção: transições, liberação e carga")
class OrdemProducaoTest {

    private final OrdemProducaoRepository ordemRepo = mock(OrdemProducaoRepository.class);
    private final OpEventoRepository eventoRepo = mock(OpEventoRepository.class);
    private final ProdutoRepository produtoRepo = mock(ProdutoRepository.class);
    private final ClienteRepository clienteRepo = mock(ClienteRepository.class);
    private final FichaTecnicaRepository fichaRepo = mock(FichaTecnicaRepository.class);
    private final BomRepository bomRepo = mock(BomRepository.class);
    private final RoteiroRepository roteiroRepo = mock(RoteiroRepository.class);
    private final MaquinaRepository maquinaRepo = mock(MaquinaRepository.class);
    private final UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);

    private OrdemProducaoService service;
    private Produto perfil;
    private Produto tarugo;
    private CentroTrabalho prensa;
    private TipoMaquina tipoPrensa;
    private Bom bom;
    private Roteiro roteiro;

    private static BigDecimal n(String v) { return new BigDecimal(v); }

    @BeforeEach
    void setUp() {
        service = new OrdemProducaoService(ordemRepo, eventoRepo, produtoRepo, clienteRepo, fichaRepo, bomRepo, roteiroRepo,
                maquinaRepo, usuarioRepo);
        perfil = Produto.builder().id(1L).nome("Perfil U").codigoInterno("P1").unidadeMedida("KG").ativo(true).build();
        tarugo = Produto.builder().id(2L).nome("Tarugo").codigoInterno("P2").unidadeMedida("KG").ativo(true)
                .estoqueAtual(n("50")).build();
        Setor setor = Setor.builder().id(1L).nome("Extrusão").unidade(Unidade.builder().id(1L).nome("Fábrica").build()).build();
        prensa = CentroTrabalho.builder().id(5L).setor(setor).codigo("CT-P1").nome("Prensa 1").build();
        tipoPrensa = TipoMaquina.builder().id(7L).nome("Prensa").categoria("PRENSA_EXTRUSAO").build();

        bom = Bom.builder().id(10L).produto(perfil).versao(3).status("ATIVA").quantidadeBase(BigDecimal.ONE).build();
        bom.getItens().add(BomItem.builder().bom(bom).sequencia(10).componente(tarugo).quantidade(n("1.1"))
                .perdaPercentual(n("10")).build());
        roteiro = Roteiro.builder().id(20L).produto(perfil).versao(2).status("ATIVO").build();
        roteiro.getOperacoes().add(RoteiroOperacao.builder().roteiro(roteiro).sequencia(10).descricao("Extrudar")
                .centroTrabalho(prensa).tipoMaquina(tipoPrensa).tempoSetupMin(n("45")).tempoPorUnidadeMin(n("0.3")).build());

        when(produtoRepo.findById(1L)).thenReturn(Optional.of(perfil));
        when(fichaRepo.findByProdutoId(anyLong())).thenReturn(Optional.empty());
        when(bomRepo.ativaDoProduto(1L)).thenReturn(Optional.of(bom));
        when(roteiroRepo.ativoDoProduto(1L)).thenReturn(Optional.of(roteiro));
        when(ordemRepo.proximoNumero()).thenReturn(42L);
        when(ordemRepo.saveAndFlush(any())).thenAnswer(inv -> {
            OrdemProducao o = inv.getArgument(0);
            if (o.getId() == null) o.setId(99L);
            return o;
        });
        when(eventoRepo.findByOrdemIdOrderByCreatedAtDescIdDesc(anyLong())).thenReturn(List.of());
        when(usuarioRepo.findAllById(any())).thenReturn(List.of());
    }

    private OrdemIn entrada(String quantidade) {
        return new OrdemIn(1L, n(quantidade), null, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20), null, "PED-7", null, null);
    }

    private OrdemProducao planejada() {
        OrdemProducao o = OrdemProducao.builder().id(99L).numero("OP000042").produto(perfil).quantidade(n("1000"))
                .inicioPrevisto(LocalDate.of(2026, 11, 16)).fimPrevisto(LocalDate.of(2026, 11, 20)).versao(0L).build();
        when(ordemRepo.findById(99L)).thenReturn(Optional.of(o));
        return o;
    }

    @Nested
    @DisplayName("Criação")
    class Criacao {
        @Test
        void numera_e_registra_o_evento_inicial_com_previa_da_engenharia() {
            OrdemOut out = service.criar(entrada("1000"), 9L);
            assertThat(out.ordem().numero()).isEqualTo("OP000042");
            assertThat(out.ordem().status()).isEqualTo("PLANEJADA");
            assertThat(out.previa()).isTrue();
            assertThat(out.pendenciasLiberacao()).isEmpty();
            // 1000 kg × 1,1 / 0,9 = 1222,222222 kg de tarugo; há 50 em estoque
            MaterialOut m = out.materiais().get(0);
            assertThat(m.quantidadePrevista()).isEqualByComparingTo("1222.222222");
            assertThat(m.falta()).isEqualByComparingTo("1172.222222");
            assertThat(out.operacoes().get(0).tempoPrevistoMin()).isEqualByComparingTo("345");
            verify(eventoRepo).save(argThat(e -> e.getStatusAnterior() == null && "PLANEJADA".equals(e.getStatusNovo())));
        }

        @Test
        void respeita_lote_minimo_e_multiplo_da_ficha() {
            when(fichaRepo.findByProdutoId(1L)).thenReturn(Optional.of(FichaTecnica.builder()
                    .loteMinimo(n("500")).loteMultiplo(n("100")).build()));
            assertThatThrownBy(() -> service.criar(entrada("400"), 9L)).hasMessageContaining("lote mínimo");
            assertThatThrownBy(() -> service.criar(entrada("550"), 9L)).hasMessageContaining("múltipla de 100");
            assertThat(service.criar(entrada("600"), 9L).ordem().quantidade()).isEqualByComparingTo("600");
        }

        @Test
        void fim_nao_pode_ser_antes_do_inicio() {
            OrdemIn in = new OrdemIn(1L, n("10"), null, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 16), null, null, null, null);
            assertThatThrownBy(() -> service.criar(in, 9L)).hasMessageContaining("fim previsto");
        }
    }

    @Nested
    @DisplayName("Transições")
    class Transicoes {
        @Test
        void mapa_de_transicoes() {
            assertThat(OrdemProducaoService.TRANSICOES.get("PLANEJADA")).containsExactlyInAnyOrder("LIBERADA", "CANCELADA");
            assertThat(OrdemProducaoService.TRANSICOES.get("ENCERRADA")).isEmpty();
            assertThat(OrdemProducaoService.TRANSICOES.get("CANCELADA")).isEmpty();
            assertThatThrownBy(() -> OrdemProducaoService.validarTransicao("PLANEJADA", "EM_PRODUCAO"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("planejada não pode passar para em produção");
            assertThatThrownBy(() -> OrdemProducaoService.validarTransicao("EM_PRODUCAO", "CANCELADA"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void liberacao_congela_bom_e_roteiro_na_op() {
            OrdemProducao o = planejada();
            OrdemOut out = service.mudarStatus(99L, new StatusIn("LIBERADA", 0L, null), 9L);

            assertThat(out.ordem().status()).isEqualTo("LIBERADA");
            assertThat(out.previa()).isFalse();
            assertThat(out.bomVersao()).isEqualTo(3);
            assertThat(out.roteiroVersao()).isEqualTo(2);
            assertThat(o.getMateriais()).singleElement().satisfies(m -> {
                assertThat(m.getQuantidadeLiquida()).isEqualByComparingTo("1100");
                assertThat(m.getQuantidadePrevista()).isEqualByComparingTo("1222.222222");
                assertThat(m.getUnidade()).isEqualTo("KG");
            });
            assertThat(o.getOperacoes()).singleElement().satisfies(op ->
                    assertThat(op.getTempoPrevistoMin()).isEqualByComparingTo("345"));

            // mudar a engenharia depois não altera a OP liberada
            bom.getItens().get(0).setQuantidade(n("5"));
            assertThat(service.buscar(99L).materiais().get(0).quantidadePrevista()).isEqualByComparingTo("1222.222222");
        }

        @Test
        void sem_bom_ativa_nao_libera() {
            planejada();
            when(bomRepo.ativaDoProduto(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.mudarStatus(99L, new StatusIn("LIBERADA", 0L, null), 9L))
                    .hasMessageContaining("estrutura (BOM) ativa");
        }

        @Test
        void cancelar_e_pausar_exigem_motivo() {
            planejada();
            assertThatThrownBy(() -> service.mudarStatus(99L, new StatusIn("CANCELADA", 0L, " "), 9L))
                    .hasMessageContaining("motivo");
            assertThat(service.mudarStatus(99L, new StatusIn("CANCELADA", 0L, "Cliente desistiu"), 9L).ordem().status())
                    .isEqualTo("CANCELADA");
        }

        @Test
        void versao_desatualizada_e_recusada() {
            OrdemProducao o = planejada();
            o.setVersao(3L);
            assertThatThrownBy(() -> service.mudarStatus(99L, new StatusIn("LIBERADA", 2L, null), 9L))
                    .hasMessageContaining("alterada por outra pessoa");
        }

        @Test
        void voltar_para_planejada_descarta_o_congelamento() {
            OrdemProducao o = planejada();
            service.mudarStatus(99L, new StatusIn("LIBERADA", 0L, null), 9L);
            service.mudarStatus(99L, new StatusIn("PLANEJADA", 0L, "Trocar a estrutura"), 9L);
            assertThat(o.getMateriais()).isEmpty();
            assertThat(o.getOperacoes()).isEmpty();
            assertThat(o.getBom()).isNull();
        }

        @Test
        void nao_volta_para_planejada_com_operacao_iniciada() {
            OrdemProducao o = planejada();
            service.mudarStatus(99L, new StatusIn("LIBERADA", 0L, null), 9L);
            o.getOperacoes().get(0).setStatus("EM_EXECUCAO");
            assertThatThrownBy(() -> service.mudarStatus(99L, new StatusIn("PLANEJADA", 0L, "x"), 9L))
                    .hasMessageContaining("operação iniciada");
        }
    }

    @Nested
    @DisplayName("Máquina da operação")
    class MaquinaDaOperacao {
        private OrdemProducao liberada() {
            OrdemProducao o = planejada();
            service.mudarStatus(99L, new StatusIn("LIBERADA", 0L, null), 9L);
            o.getOperacoes().get(0).setId(300L);
            return o;
        }

        @Test
        void aceita_maquina_do_centro_e_do_tipo_pedido() {
            OrdemProducao o = liberada();
            Maquina m = Maquina.builder().id(8L).codigo("PR-01").descricao("Prensa 1800 t").centroTrabalho(prensa)
                    .tipoMaquina(tipoPrensa).build();
            when(maquinaRepo.findById(8L)).thenReturn(Optional.of(m));
            OrdemOut out = service.definirMaquina(99L, 300L, new MaquinaOperacaoIn(8L, 0L));
            assertThat(out.operacoes().get(0).maquinaCodigo()).isEqualTo("PR-01");
            assertThat(o.getOperacoes().get(0).getMaquina()).isSameAs(m);
        }

        @Test
        void recusa_maquina_de_outro_centro_ou_tipo() {
            liberada();
            CentroTrabalho outro = CentroTrabalho.builder().id(6L).nome("Serra").build();
            when(maquinaRepo.findById(8L)).thenReturn(Optional.of(Maquina.builder().id(8L).codigo("SR-01")
                    .centroTrabalho(outro).tipoMaquina(tipoPrensa).build()));
            assertThatThrownBy(() -> service.definirMaquina(99L, 300L, new MaquinaOperacaoIn(8L, 0L)))
                    .hasMessageContaining("não pertence ao centro");
            when(maquinaRepo.findById(9L)).thenReturn(Optional.of(Maquina.builder().id(9L).codigo("FN-01")
                    .centroTrabalho(prensa).tipoMaquina(TipoMaquina.builder().id(70L).nome("Forno").build()).build()));
            assertThatThrownBy(() -> service.definirMaquina(99L, 300L, new MaquinaOperacaoIn(9L, 0L)))
                    .hasMessageContaining("do tipo Prensa");
        }
    }

    @Nested
    @DisplayName("Carga × capacidade")
    class Carga {
        @Test
        void capacidade_soma_os_turnos_dos_dias_marcados() {
            Turno comercial = Turno.builder().horaInicio(LocalTime.of(6, 0)).horaFim(LocalTime.of(14, 0)).diasSemana("1111100").build();
            Turno noite = Turno.builder().horaInicio(LocalTime.of(22, 0)).horaFim(LocalTime.of(6, 0)).diasSemana("1000000").build();
            // 16/11/2026 é segunda; até domingo 22/11: 5 dias × 480 + 1 segunda × 480 da noite
            assertThat(CargaService.capacidadeMinutos(Set.of(comercial, noite), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22)))
                    .isEqualTo(5 * 480 + 480);
            noite.setAtivo(false);
            assertThat(CargaService.capacidadeMinutos(Set.of(noite), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 16))).isZero();
        }

        @Test
        void rateia_o_tempo_pelos_dias_dentro_do_periodo() {
            // OP de 10 dias, 1000 min; período pega 3 desses dias
            assertThat(CargaService.rateio(n("1000"), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10),
                    LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 30))).isEqualByComparingTo("300");
            assertThat(CargaService.rateio(n("1000"), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10),
                    LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5))).isEqualByComparingTo("0");
        }
    }
}
