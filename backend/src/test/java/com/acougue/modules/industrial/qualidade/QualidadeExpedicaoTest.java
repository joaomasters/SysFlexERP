package com.acougue.modules.industrial.qualidade;

import com.acougue.entity.Cliente;
import com.acougue.entity.NotaFiscalSaida;
import com.acougue.entity.NotaFiscalSaidaItem;
import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.estoque.EstoqueService;
import com.acougue.modules.fiscal.NotaFiscalService;
import com.acougue.modules.industrial.engenharia.FichaTecnicaRepository;
import com.acougue.modules.industrial.execucao.ExecucaoMapper;
import com.acougue.modules.industrial.expedicao.*;
import com.acougue.modules.industrial.expedicao.ExpedicaoDto.*;
import com.acougue.modules.industrial.pcp.OrdemProducao;
import com.acougue.modules.industrial.pcp.OrdemProducaoRepository;
import com.acougue.modules.industrial.qualidade.QualidadeDto.*;
import com.acougue.repository.ClienteRepository;
import com.acougue.repository.NotaFiscalSaidaRepository;
import com.acougue.repository.ProdutoRepository;
import com.acougue.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Qualidade, rastreabilidade e expedição")
class QualidadeExpedicaoTest {

    private final LoteRepository loteRepo = mock(LoteRepository.class);
    private final LoteGenealogiaRepository genealogiaRepo = mock(LoteGenealogiaRepository.class);
    private final LoteMovimentoRepository movimentoRepo = mock(LoteMovimentoRepository.class);
    private final PlanoInspecaoRepository planoRepo = mock(PlanoInspecaoRepository.class);
    private final NaoConformidadeRepository ncRepo = mock(NaoConformidadeRepository.class);
    private final InspecaoRepository inspecaoRepo = mock(InspecaoRepository.class);
    private final ExpedicaoRepository expedicaoRepo = mock(ExpedicaoRepository.class);
    private final OrdemProducaoRepository ordemRepo = mock(OrdemProducaoRepository.class);
    private final ClienteRepository clienteRepo = mock(ClienteRepository.class);
    private final ProdutoRepository produtoRepo = mock(ProdutoRepository.class);
    private final FichaTecnicaRepository fichaRepo = mock(FichaTecnicaRepository.class);
    private final EstoqueService estoque = mock(EstoqueService.class);
    private final NotaFiscalService notaFiscalService = mock(NotaFiscalService.class);
    private final UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);

    private LoteService lotes;
    private InspecaoService inspecoes;
    private NaoConformidadeService ncs;
    private ExpedicaoService expedicoes;

    private Produto perfil;
    private OrdemProducao ordem;
    private PlanoInspecao plano;
    private final AtomicLong ids = new AtomicLong(100);
    private final Map<Long, Lote> banco = new HashMap<>();

    private static BigDecimal n(String v) { return new BigDecimal(v); }

    @BeforeEach
    void setUp() {
        ExecucaoMapper mapper = new ExecucaoMapper(usuarioRepo);
        lotes = new LoteService(loteRepo, genealogiaRepo, movimentoRepo, planoRepo, ncRepo, expedicaoRepo, estoque, mapper);
        ncs = new NaoConformidadeService(ncRepo, loteRepo, ordemRepo, lotes, mapper);
        inspecoes = new InspecaoService(inspecaoRepo, loteRepo, planoRepo, lotes, ncs, mapper);
        expedicoes = new ExpedicaoService(expedicaoRepo, clienteRepo, produtoRepo, loteRepo, lotes, estoque, notaFiscalService, fichaRepo, mapper);

        perfil = Produto.builder().id(1L).nome("Perfil U").unidadeMedida("KG").ativo(true).precoVenda(n("30")).build();
        ordem = OrdemProducao.builder().id(9L).numero("OP000009").produto(perfil).build();
        plano = PlanoInspecao.builder().id(5L).codigo("PI-U").produto(perfil).revisao(2).build();
        plano.getCaracteristicas().add(PlanoInspecao.Caracteristica.builder().plano(plano).codigo("ESPESSURA").nome("Espessura")
                .tipo("MEDIDA").unidade("mm").minimo(n("1.9")).maximo(n("2.1")).build());
        plano.getCaracteristicas().add(PlanoInspecao.Caracteristica.builder().plano(plano).codigo("ASPECTO").nome("Aspecto visual")
                .tipo("ATRIBUTO").build());

        when(loteRepo.saveAndFlush(any())).thenAnswer(inv -> {
            Lote l = inv.getArgument(0);
            if (l.getId() == null) l.setId(ids.getAndIncrement());
            banco.put(l.getId(), l);
            return l;
        });
        when(loteRepo.travar(anyLong())).thenAnswer(inv -> Optional.ofNullable(banco.get(inv.<Long>getArgument(0))));
        when(loteRepo.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(banco.get(inv.<Long>getArgument(0))));
        when(loteRepo.proximoNumero()).thenAnswer(inv -> ids.getAndIncrement());
        when(planoRepo.ativoDoProduto(anyLong())).thenReturn(Optional.empty());
        when(expedicaoRepo.reservadoDoLote(anyLong())).thenReturn(BigDecimal.ZERO);
        when(expedicaoRepo.reservadoDosLotes(any())).thenReturn(List.of());
        when(ncRepo.saveAndFlush(any())).thenAnswer(inv -> {
            NaoConformidade nc = inv.getArgument(0);
            if (nc.getId() == null) nc.setId(ids.getAndIncrement());
            return nc;
        });
        when(ncRepo.proximoNumero()).thenReturn(7L);
        when(inspecaoRepo.findByChave(any())).thenReturn(Optional.empty());
        when(inspecaoRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepo.findAllById(any())).thenReturn(List.of());
        when(fichaRepo.findByProdutoIdIn(any())).thenReturn(List.of());
    }

    private Lote lote(String situacao, String saldo) {
        Lote l = Lote.builder().id(ids.getAndIncrement()).codigo("OP000009-L01").produto(perfil).ordem(ordem).origem("PRODUCAO")
                .quantidadeInicial(n("100")).saldo(n(saldo)).situacao(situacao).versao(0L).createdAt(LocalDateTime.now()).build();
        banco.put(l.getId(), l);
        return l;
    }

    @Nested
    @DisplayName("Lote")
    class LoteTeste {
        @Test
        void nasce_em_quarentena_so_se_o_produto_tem_plano_ativo() {
            assertThat(lotes.registrarProducao(ordem, 50L, "OP000009-L01", n("100"), 3L).getSituacao()).isEqualTo("LIBERADO");
            when(planoRepo.ativoDoProduto(1L)).thenReturn(Optional.of(plano));
            Lote l = lotes.registrarProducao(ordem, 51L, "OP000009-L02", n("40"), 3L);
            assertThat(l.getSituacao()).isEqualTo("QUARENTENA");
            assertThat(l.getSaldo()).isEqualByComparingTo("40");
            verify(movimentoRepo, times(2)).save(argThat(m -> "PRODUCAO".equals(m.getTipo())));
        }

        @Test
        void estorno_de_producao_so_com_lote_intacto() {
            Lote l = lote("LIBERADO", "60");
            l.setApontamentoId(50L);
            when(loteRepo.findByApontamentoId(50L)).thenReturn(Optional.of(l));
            assertThatThrownBy(() -> lotes.estornarProducao(50L, 3L)).hasMessageContaining("não pode ser estornado");
            l.setSaldo(n("100"));
            lotes.estornarProducao(50L, 3L);
            assertThat(l.getSituacao()).isEqualTo("ESTORNADO");
            assertThat(l.getSaldo()).isEqualByComparingTo("0");
        }

        @Test
        void regras_de_situacao() {
            when(planoRepo.ativoDoProduto(1L)).thenReturn(Optional.of(plano));
            Lote q = lote("QUARENTENA", "100");
            assertThatThrownBy(() -> lotes.mudarSituacao(q.getId(), new SituacaoIn("LIBERADO", 0L, null), 3L))
                    .hasMessageContaining("libere o lote pela inspeção");
            assertThatThrownBy(() -> lotes.mudarSituacao(q.getId(), new SituacaoIn("BLOQUEADO", 0L, " "), 3L))
                    .hasMessageContaining("motivo");
            Lote b = lote("BLOQUEADO", "100");
            assertThatThrownBy(() -> lotes.mudarSituacao(b.getId(), new SituacaoIn("LIBERADO", 0L, null), 3L))
                    .hasMessageContaining("não conformidade");
            when(ncRepo.abertasDoLote(b.getId())).thenReturn(1L);
            assertThatThrownBy(() -> lotes.mudarSituacao(b.getId(), new SituacaoIn("QUARENTENA", 0L, "reinspeção"), 3L))
                    .hasMessageContaining("Encerre antes");
        }

        @Test
        void divide_so_o_disponivel_e_os_filhos_herdam_a_situacao() {
            Lote pai = lote("LIBERADO", "100");
            when(expedicaoRepo.reservadoDoLote(pai.getId())).thenReturn(n("30"));
            assertThatThrownBy(() -> lotes.dividir(pai.getId(), new DivisaoIn(List.of(n("50"), n("30"))), 3L))
                    .hasMessageContaining("tem 70 disponível");
            List<LoteOut> filhos = lotes.dividir(pai.getId(), new DivisaoIn(List.of(n("40"), n("30"))), 3L);
            assertThat(filhos).hasSize(2).allSatisfy(f -> {
                assertThat(f.situacao()).isEqualTo("LIBERADO");
                assertThat(f.origem()).isEqualTo("DIVISAO");
                assertThat(f.codigo()).startsWith("LT");
            });
            assertThat(pai.getSaldo()).isEqualByComparingTo("30");
            verify(genealogiaRepo, times(2)).save(argThat(g -> g.getLotePai() == pai));
        }

        @Test
        void une_so_lotes_do_mesmo_produto_e_situacao() {
            Lote a = lote("LIBERADO", "20");
            Lote b = lote("LIBERADO", "30");
            Lote c = lote("QUARENTENA", "10");
            assertThatThrownBy(() -> lotes.unir(new UniaoIn(List.of(new ParteUniao(a.getId(), n("20")), new ParteUniao(c.getId(), n("10")))), 3L))
                    .hasMessageContaining("mesma situação");
            LoteOut u = lotes.unir(new UniaoIn(List.of(new ParteUniao(a.getId(), n("20")), new ParteUniao(b.getId(), n("10")))), 3L);
            assertThat(u.quantidadeInicial()).isEqualByComparingTo("30");
            assertThat(u.origem()).isEqualTo("UNIAO");
            assertThat(a.getSaldo()).isEqualByComparingTo("0");
            assertThat(b.getSaldo()).isEqualByComparingTo("20");
        }
    }

    @Nested
    @DisplayName("Inspeção e não conformidade")
    class Inspecoes {
        @Test
        void inspecao_aprovada_libera_o_lote() {
            when(planoRepo.ativoDoProduto(1L)).thenReturn(Optional.of(plano));
            Lote l = lote("QUARENTENA", "100");
            InspecaoOut out = inspecoes.registrar(new InspecaoIn("i1", l.getId(), 5, null, List.of(
                    new ValorIn("espessura", n("2.0"), null), new ValorIn("ASPECTO", null, true))), 3L);
            assertThat(out.resultado()).isEqualTo("APROVADO");
            assertThat(out.planoRevisao()).isEqualTo(2);
            assertThat(l.getSituacao()).isEqualTo("LIBERADO");
            verify(ncRepo, never()).saveAndFlush(any());
        }

        @Test
        void inspecao_reprovada_bloqueia_e_abre_nc() {
            when(planoRepo.ativoDoProduto(1L)).thenReturn(Optional.of(plano));
            Lote l = lote("QUARENTENA", "100");
            InspecaoOut out = inspecoes.registrar(new InspecaoIn("i2", l.getId(), null, null, List.of(
                    new ValorIn("ESPESSURA", n("2.3"), null), new ValorIn("ASPECTO", null, true))), 3L);
            assertThat(out.resultado()).isEqualTo("REPROVADO");
            assertThat(out.naoConformidadeNumero()).isEqualTo("NC000007");
            assertThat(l.getSituacao()).isEqualTo("BLOQUEADO");
        }

        @Test
        void obrigatoria_faltando_e_lote_fora_da_quarentena_sao_recusados() {
            when(planoRepo.ativoDoProduto(1L)).thenReturn(Optional.of(plano));
            Lote l = lote("QUARENTENA", "100");
            assertThatThrownBy(() -> inspecoes.registrar(new InspecaoIn("i3", l.getId(), null, null,
                    List.of(new ValorIn("ESPESSURA", n("2.0"), null))), 3L)).hasMessageContaining("Informe Aspecto visual");
            Lote liberado = lote("LIBERADO", "100");
            assertThatThrownBy(() -> inspecoes.registrar(new InspecaoIn("i4", liberado.getId(), null, null,
                    List.of(new ValorIn("ESPESSURA", n("2.0"), null))), 3L)).hasMessageContaining("Só lotes em quarentena");
        }

        @Test
        void encerrar_nc_usar_como_esta_libera_e_sucatear_baixa_o_estoque() {
            Lote l = lote("BLOQUEADO", "100");
            NaoConformidade nc = NaoConformidade.builder().id(70L).numero("NC000001").origem("INSPECAO").lote(l).descricao("x").versao(0L).build();
            when(ncRepo.findById(70L)).thenReturn(Optional.of(nc));
            assertThatThrownBy(() -> ncs.encerrar(70L, new EncerrarNcIn(null, "causa", "ação", 0L), 3L))
                    .hasMessageContaining("Escolha a disposição");
            ncs.encerrar(70L, new EncerrarNcIn("USAR_COMO_ESTA", "Risco leve", "Ajuste na guia", 0L), 3L);
            assertThat(l.getSituacao()).isEqualTo("LIBERADO");

            Lote s = lote("BLOQUEADO", "80");
            NaoConformidade nc2 = NaoConformidade.builder().id(71L).numero("NC000002").origem("INSPECAO").lote(s).descricao("x").versao(0L).build();
            when(ncRepo.findById(71L)).thenReturn(Optional.of(nc2));
            ncs.encerrar(71L, new EncerrarNcIn("SUCATEAR", "Trinca", "Troca de matriz", 0L), 3L);
            verify(estoque).saida(perfil, n("80"), "SAIDA_DESCARTE", "NC000002", 3L);
            assertThat(s.getSaldo()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("Expedição")
    class ExpedicaoTeste {
        private Expedicao exp;
        private ExpedicaoItem item;

        @BeforeEach
        void exp() {
            Cliente cliente = Cliente.builder().id(4L).nome("Construtora X").cpfCnpj("12345678000199").ativo(true).build();
            exp = Expedicao.builder().id(30L).numero("EXP000001").cliente(cliente).dataPrevista(LocalDate.now()).versao(0L).build();
            item = ExpedicaoItem.builder().id(31L).expedicao(exp).produto(perfil).quantidade(n("100")).build();
            exp.getItens().add(item);
            when(expedicaoRepo.findById(30L)).thenReturn(Optional.of(exp));
            when(expedicaoRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void reserva_so_lote_liberado_com_saldo_e_sem_passar_do_item() {
            Lote bloqueado = lote("BLOQUEADO", "100");
            assertThatThrownBy(() -> expedicoes.reservar(30L, new ReservaIn(31L, bloqueado.getId(), n("10"))))
                    .hasMessageContaining("bloqueado pela qualidade");
            Lote l = lote("LIBERADO", "100");
            when(expedicaoRepo.reservadoDoLote(l.getId())).thenReturn(n("95"));
            assertThatThrownBy(() -> expedicoes.reservar(30L, new ReservaIn(31L, l.getId(), n("10")))).hasMessageContaining("tem 5 disponível");
            when(expedicaoRepo.reservadoDoLote(l.getId())).thenReturn(BigDecimal.ZERO);
            item.setQuantidade(n("50"));
            assertThatThrownBy(() -> expedicoes.reservar(30L, new ReservaIn(31L, l.getId(), n("60")))).hasMessageContaining("passa da quantidade");
        }

        @Test
        void reserva_automatica_usa_o_lote_mais_antigo_primeiro() {
            Lote velho = lote("LIBERADO", "70");
            Lote novo = lote("LIBERADO", "80");
            when(loteRepo.liberadosFifo(1L)).thenReturn(List.of(velho, novo));
            ExpedicaoOut out = expedicoes.reservarAutomatico(30L);
            assertThat(out.itens().get(0).reservas()).extracting(ReservaOut::loteId, r -> r.quantidade().intValueExact())
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(velho.getId(), 70), org.assertj.core.groups.Tuple.tuple(novo.getId(), 30));
            assertThat(out.itens().get(0).pendente()).isEqualByComparingTo("0");
        }

        @Test
        void conferencia_le_o_codigo_e_so_conclui_sem_pendencias() {
            Lote l = lote("LIBERADO", "100");
            expedicoes.reservar(30L, new ReservaIn(31L, l.getId(), n("100")));
            assertThatThrownBy(() -> expedicoes.conferir(30L, new ConferenciaIn("LT999999"), 3L)).hasMessageContaining("não está reservado");
            assertThatThrownBy(() -> expedicoes.concluirConferencia(30L, new VersaoIn(0L))).hasMessageContaining("não conferido");
            ExpedicaoOut out = expedicoes.conferir(30L, new ConferenciaIn("op000009-l01"), 3L);
            assertThat(out.itens().get(0).reservas().get(0).conferida()).isTrue();
            assertThat(expedicoes.concluirConferencia(30L, new VersaoIn(0L)).status()).isEqualTo("CONFERIDA");
        }

        @Test
        void carregamento_baixa_lote_e_estoque_uma_vez_e_recusa_lote_bloqueado_depois() {
            Lote l = lote("LIBERADO", "100");
            expedicoes.reservar(30L, new ReservaIn(31L, l.getId(), n("100")));
            expedicoes.conferir(30L, new ConferenciaIn(l.getCodigo()), 3L);
            expedicoes.concluirConferencia(30L, new VersaoIn(0L));

            l.setSituacao("BLOQUEADO");
            assertThatThrownBy(() -> expedicoes.carregar(30L, new VersaoIn(0L), 3L)).hasMessageContaining("bloqueado pela qualidade");
            verifyNoInteractions(estoque);

            l.setSituacao("LIBERADO");
            assertThat(expedicoes.carregar(30L, new VersaoIn(0L), 3L).status()).isEqualTo("CARREGADA");
            expedicoes.carregar(30L, new VersaoIn(0L), 3L); // repetido: não baixa de novo
            verify(estoque, times(1)).saida(perfil, n("100"), "SAIDA_EXPEDICAO", "EXP000001", 3L);
            assertThat(l.getSaldo()).isEqualByComparingTo("0");
        }

        @Test
        void nota_fiscal_e_criada_pendente_ligada_a_expedicao_e_uma_vez_so() {
            Lote l = lote("LIBERADO", "100");
            expedicoes.reservar(30L, new ReservaIn(31L, l.getId(), n("100")));
            expedicoes.conferir(30L, new ConferenciaIn(l.getCodigo()), 3L);
            expedicoes.concluirConferencia(30L, new VersaoIn(0L));
            expedicoes.carregar(30L, new VersaoIn(0L), 3L);
            NotaFiscalSaida nf = NotaFiscalSaida.builder().id(500L).status("PENDENTE").build();
            when(notaFiscalService.criar(any(), eq(3L))).thenReturn(nf);

            NotaGerada g = expedicoes.gerarNotaFiscal(30L, 3L);
            assertThat(g.notaFiscalId()).isEqualTo(500L);
            assertThat(g.status()).isEqualTo("PENDENTE");
            assertThat(nf.getExpedicaoId()).isEqualTo(30L);
            assertThat(expedicoes.gerarNotaFiscal(30L, 3L).status()).isEqualTo("JA_GERADA");
            verify(notaFiscalService, times(1)).criar(any(), anyLong());
        }
    }

    @Test
    @DisplayName("NF de saída vinda da expedição não baixa o estoque de novo ao emitir nem devolve ao cancelar")
    void nota_de_expedicao_nao_movimenta_estoque() {
        NotaFiscalSaidaRepository notaRepo = mock(NotaFiscalSaidaRepository.class);
        NotaFiscalService fiscal = new NotaFiscalService(notaRepo, clienteRepo, produtoRepo, estoque);
        NotaFiscalSaida nf = NotaFiscalSaida.builder().id(500L).status("PENDENTE").expedicaoId(30L).build();
        nf.getItens().add(NotaFiscalSaidaItem.builder().nota(nf).produto(perfil).quantidade(n("100")).build());
        when(notaRepo.findById(500L)).thenReturn(Optional.of(nf));
        when(notaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        fiscal.atualizarStatus(500L, "EMITIDA", 3L);
        fiscal.atualizarStatus(500L, "CANCELADA", 3L);

        verifyNoInteractions(estoque);
        assertThat(nf.getUsuarioEmissaoId()).isEqualTo(3L);
        ArgumentCaptor<NotaFiscalSaida> c = ArgumentCaptor.forClass(NotaFiscalSaida.class);
        verify(notaRepo, times(2)).save(c.capture());
        assertThat(c.getValue().getStatus()).isEqualTo("CANCELADA");
    }
}
