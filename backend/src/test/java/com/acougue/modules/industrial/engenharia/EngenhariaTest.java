package com.acougue.modules.industrial.engenharia;

import com.acougue.entity.Produto;
import com.acougue.exception.BusinessException;
import com.acougue.modules.industrial.engenharia.EngenhariaDto.*;
import com.acougue.modules.industrial.estrutura.*;
import com.acougue.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@DisplayName("Engenharia industrial: BOM, roteiro e ficha técnica")
class EngenhariaTest {

    private final BomRepository bomRepo = mock(BomRepository.class);
    private final RoteiroRepository roteiroRepo = mock(RoteiroRepository.class);
    private final ProdutoRepository produtoRepo = mock(ProdutoRepository.class);
    private final FichaTecnicaRepository fichaRepo = mock(FichaTecnicaRepository.class);
    private final CentroTrabalhoRepository centroRepo = mock(CentroTrabalhoRepository.class);
    private final TipoMaquinaRepository tipoRepo = mock(TipoMaquinaRepository.class);

    private BomService boms;
    private RoteiroService roteiros;
    private FichaTecnicaService fichas;
    private Produto perfil;
    private Produto tarugo;
    private Produto embalagem;
    private CentroTrabalho prensa;

    private static Produto produto(long id, String nome, String unidade) {
        return Produto.builder().id(id).nome(nome).codigoInterno("P" + id).unidadeMedida(unidade).ativo(true)
                .estoqueAtual(BigDecimal.ZERO).build();
    }

    private static BigDecimal n(String v) { return new BigDecimal(v); }

    @BeforeEach
    void setUp() {
        boms = new BomService(bomRepo, produtoRepo);
        roteiros = new RoteiroService(roteiroRepo, produtoRepo, centroRepo, tipoRepo);
        fichas = new FichaTecnicaService(fichaRepo, produtoRepo);
        perfil = produto(1, "Perfil U 40x20", "KG");
        tarugo = produto(2, "Tarugo 6063", "KG");
        embalagem = produto(3, "Filme stretch", "UN");
        for (Produto p : List.of(perfil, tarugo, embalagem)) when(produtoRepo.findById(p.getId())).thenReturn(Optional.of(p));
        when(bomRepo.ativaDoProduto(anyLong())).thenReturn(Optional.empty());
        when(bomRepo.findFirstByProdutoIdAndStatus(anyLong(), any())).thenReturn(Optional.empty());
        when(bomRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(bomRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roteiroRepo.ativoDoProduto(anyLong())).thenReturn(Optional.empty());
        when(roteiroRepo.findFirstByProdutoIdAndStatus(anyLong(), any())).thenReturn(Optional.empty());
        when(roteiroRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roteiroRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        Setor setor = Setor.builder().id(1L).nome("Extrusão").unidade(Unidade.builder().id(1L).nome("Fábrica").build()).build();
        prensa = CentroTrabalho.builder().id(5L).setor(setor).codigo("CT-P1").nome("Prensa 1").build();
        when(centroRepo.findById(5L)).thenReturn(Optional.of(prensa));
    }

    private Bom bomAtiva(Produto p, Produto componente, String qtd) {
        Bom b = Bom.builder().id(100L + p.getId()).produto(p).versao(1).status("ATIVA").quantidadeBase(BigDecimal.ONE)
                .vigenciaInicio(LocalDate.now().minusMonths(1)).build();
        b.getItens().add(BomItem.builder().bom(b).sequencia(10).componente(componente).quantidade(n(qtd)).build());
        return b;
    }

    @Nested
    @DisplayName("Cálculo de necessidade")
    class Necessidade {
        @Test
        void proporcional_a_base_e_com_perda_sobre_o_que_entra() {
            // 1,1 kg de tarugo por 1 kg de perfil, 10% de perda: 100 kg de perfil pedem 110 / 0,9 = 122,222222 kg
            assertThat(BomService.necessidade(n("1.1"), n("10"), BigDecimal.ONE, n("100"))).isEqualByComparingTo("122.222222");
            assertThat(BomService.necessidade(n("2"), BigDecimal.ZERO, n("50"), n("100"))).isEqualByComparingTo("4");
            assertThat(BomService.liquida(n("2"), n("50"), n("100"))).isEqualByComparingTo("4");
        }
    }

    @Nested
    @DisplayName("Estrutura (BOM)")
    class Estrutura {
        @Test
        void cria_rascunho_com_a_proxima_versao_e_sequencia_automatica() {
            when(bomRepo.ultimaVersao(1L)).thenReturn(2);
            BomOut b = boms.criar(new BomIn(1L, BigDecimal.ONE, null, List.of(
                    new BomItemIn(2L, n("1.1"), n("8"), null), new BomItemIn(3L, n("0.01"), null, null))), 9L);
            assertThat(b.versao()).isEqualTo(3);
            assertThat(b.status()).isEqualTo("RASCUNHO");
            assertThat(b.itens()).extracting(BomItemOut::sequencia).containsExactly(10, 20);
            assertThat(b.itens().get(1).perdaPercentual()).isEqualByComparingTo("0");
        }

        @Test
        void produto_nao_pode_ser_componente_de_si_mesmo() {
            assertThatThrownBy(() -> boms.criar(new BomIn(1L, BigDecimal.ONE, null, List.of(new BomItemIn(1L, n("1"), null, null))), 9L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("si mesmo");
        }

        @Test
        void recusa_ciclo_indireto() {
            // o tarugo (ativo) já usa o perfil; o perfil não pode passar a usar o tarugo
            when(bomRepo.ativaDoProduto(2L)).thenReturn(Optional.of(bomAtiva(tarugo, perfil, "1")));
            assertThatThrownBy(() -> boms.criar(new BomIn(1L, BigDecimal.ONE, null, List.of(new BomItemIn(2L, n("1"), null, null))), 9L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Ciclo");
        }

        @Test
        void recusa_componente_repetido() {
            assertThatThrownBy(() -> boms.criar(new BomIn(1L, BigDecimal.ONE, null, List.of(
                    new BomItemIn(2L, n("1"), null, null), new BomItemIn(2L, n("2"), null, null))), 9L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("mais de uma vez");
        }

        @Test
        void recusa_segundo_rascunho_do_mesmo_produto() {
            when(bomRepo.findFirstByProdutoIdAndStatus(1L, "RASCUNHO"))
                    .thenReturn(Optional.of(Bom.builder().versao(4).produto(perfil).build()));
            assertThatThrownBy(() -> boms.criar(new BomIn(1L, BigDecimal.ONE, null, List.of()), 9L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("versão 4 em rascunho");
        }

        @Test
        void versao_ativada_nao_e_editada() {
            Bom ativa = bomAtiva(perfil, tarugo, "1");
            when(bomRepo.findById(ativa.getId())).thenReturn(Optional.of(ativa));
            assertThatThrownBy(() -> boms.editar(ativa.getId(), new BomIn(1L, BigDecimal.ONE, null, List.of())))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("nova versão");
            assertThatThrownBy(() -> boms.excluir(ativa.getId())).isInstanceOf(BusinessException.class);
        }

        @Test
        void ativar_torna_a_anterior_obsoleta_com_vigencia_fechada_na_vespera() {
            Bom anterior = bomAtiva(perfil, tarugo, "1");
            Bom nova = Bom.builder().id(500L).produto(perfil).versao(2).quantidadeBase(BigDecimal.ONE).build();
            nova.getItens().add(BomItem.builder().bom(nova).sequencia(10).componente(tarugo).quantidade(n("1.05")).build());
            when(bomRepo.findById(500L)).thenReturn(Optional.of(nova));
            when(bomRepo.ativaDoProduto(1L)).thenReturn(Optional.of(anterior));

            BomOut out = boms.ativar(500L, 9L);

            assertThat(out.status()).isEqualTo("ATIVA");
            assertThat(out.vigenciaInicio()).isEqualTo(LocalDate.now());
            assertThat(anterior.getStatus()).isEqualTo("OBSOLETA");
            assertThat(anterior.getVigenciaFim()).isEqualTo(LocalDate.now().minusDays(1));
            // a anterior é gravada antes da nova (o índice "uma ativa por produto" não é adiável)
            var ordem = inOrder(bomRepo);
            ordem.verify(bomRepo).saveAndFlush(anterior);
            ordem.verify(bomRepo).saveAndFlush(nova);
        }

        @Test
        void nao_ativa_estrutura_vazia() {
            Bom vazia = Bom.builder().id(501L).produto(perfil).versao(1).quantidadeBase(BigDecimal.ONE).build();
            when(bomRepo.findById(501L)).thenReturn(Optional.of(vazia));
            assertThatThrownBy(() -> boms.ativar(501L, 9L)).hasMessageContaining("ao menos um componente");
        }

        @Test
        void nova_versao_copia_os_itens_para_um_rascunho() {
            Bom ativa = bomAtiva(perfil, tarugo, "1.1");
            when(bomRepo.findById(ativa.getId())).thenReturn(Optional.of(ativa));
            when(bomRepo.ultimaVersao(1L)).thenReturn(1);
            BomOut copia = boms.novaVersao(ativa.getId(), 9L);
            assertThat(copia.versao()).isEqualTo(2);
            assertThat(copia.status()).isEqualTo("RASCUNHO");
            assertThat(copia.itens()).singleElement().satisfies(i -> assertThat(i.quantidade()).isEqualByComparingTo("1.1"));
        }

        @Test
        void explosao_desce_pelos_semiacabados() {
            // perfil (base 1) usa 1,1 tarugo; tarugo é fabricado: 1 tarugo usa 0,5 de embalagem (só para o teste)
            Bom bPerfil = bomAtiva(perfil, tarugo, "1.1");
            when(bomRepo.findById(bPerfil.getId())).thenReturn(Optional.of(bPerfil));
            when(bomRepo.ativaDoProduto(2L)).thenReturn(Optional.of(bomAtiva(tarugo, embalagem, "0.5")));

            List<ExplosaoLinha> linhas = boms.explosao(bPerfil.getId(), n("100"));

            assertThat(linhas).hasSize(2);
            assertThat(linhas.get(0).nivel()).isEqualTo(1);
            assertThat(linhas.get(0).quantidade()).isEqualByComparingTo("110");
            assertThat(linhas.get(1).nivel()).isEqualTo(2);
            assertThat(linhas.get(1).quantidade()).isEqualByComparingTo("55");
        }
    }

    @Nested
    @DisplayName("Roteiro")
    class RoteiroTeste {
        private OperacaoIn op(int seq) {
            return new OperacaoIn(seq, "Extrudar", 5L, null, n("30"), n("0.2"), null);
        }

        @Test
        void ordena_por_sequencia_e_recusa_repetida() {
            RoteiroOut r = roteiros.criar(new RoteiroIn(1L, null, List.of(op(20), op(10))), 9L);
            assertThat(r.operacoes()).extracting(OperacaoOut::sequencia).containsExactly(10, 20);
            assertThatThrownBy(() -> roteiros.criar(new RoteiroIn(1L, null, List.of(op(10), op(10))), 9L))
                    .hasMessageContaining("repetida");
        }

        @Test
        void recusa_centro_inativo() {
            prensa.setAtivo(false);
            assertThatThrownBy(() -> roteiros.criar(new RoteiroIn(1L, null, List.of(op(10))), 9L))
                    .hasMessageContaining("inativo");
        }

        @Test
        void ativar_obsoleta_o_anterior() {
            Roteiro anterior = Roteiro.builder().id(1L).produto(perfil).versao(1).status("ATIVO")
                    .vigenciaInicio(LocalDate.now().minusDays(10)).build();
            Roteiro novo = Roteiro.builder().id(2L).produto(perfil).versao(2).build();
            novo.getOperacoes().add(RoteiroOperacao.builder().roteiro(novo).sequencia(10).descricao("Extrudar")
                    .centroTrabalho(prensa).build());
            when(roteiroRepo.findById(2L)).thenReturn(Optional.of(novo));
            when(roteiroRepo.ativoDoProduto(1L)).thenReturn(Optional.of(anterior));
            assertThat(roteiros.ativar(2L, 9L).status()).isEqualTo("ATIVO");
            assertThat(anterior.getStatus()).isEqualTo("OBSOLETO");
        }
    }

    @Nested
    @DisplayName("Ficha técnica")
    class Ficha {
        private FichaIn ficha(String minimo, String multiplo) {
            return new FichaIn("ACABADO", "DES-1", "A", null, n("0.85"), n("6000"),
                    minimo == null ? null : n(minimo), multiplo == null ? null : n(multiplo), 3, true, null);
        }

        @Test
        void cria_a_ficha_do_produto() {
            when(fichaRepo.findByProdutoId(1L)).thenReturn(Optional.empty());
            when(fichaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            FichaOut f = fichas.salvar(1L, ficha("500", "100"));
            assertThat(f.tipoItem()).isEqualTo("ACABADO");
            assertThat(f.produtoNome()).isEqualTo("Perfil U 40x20");
        }

        @Test
        void lote_minimo_precisa_ser_multiplo_do_lote_multiplo() {
            when(fichaRepo.findByProdutoId(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> fichas.salvar(1L, ficha("250", "100"))).hasMessageContaining("múltiplo");
        }

        @Test
        void recusa_tipo_desconhecido() {
            assertThatThrownBy(() -> fichas.salvar(1L, new FichaIn("SUCATA", null, null, null, null, null, null, null, null, null, null)))
                    .hasMessageContaining("Tipo de item");
        }
    }

    @Test
    void produto_inativo_nao_entra_na_engenharia() {
        tarugo.setAtivo(false);
        assertThatThrownBy(() -> boms.criar(new BomIn(1L, BigDecimal.ONE, null, List.of(new BomItemIn(2L, n("1"), null, null))), 9L))
                .hasMessageContaining("inativo");
    }
}
