package com.acougue.modules.fiscal.emissao;

import com.acougue.entity.ConfiguracaoFiscal;
import com.acougue.entity.DocumentoFiscal;
import com.acougue.entity.ItensVenda;
import com.acougue.entity.PagamentoVenda;
import com.acougue.entity.Produto;
import com.acougue.entity.Venda;
import com.acougue.exception.BusinessException;
import com.acougue.modules.fiscal.emissao.dto.NfceDTO;
import com.acougue.modules.pagamento.CredencialCripto;
import com.acougue.repository.ConfiguracaoFiscalRepository;
import com.acougue.repository.DocumentoFiscalRepository;
import com.acougue.repository.ItensVendaRepository;
import com.acougue.repository.PagamentoVendaRepository;
import com.acougue.repository.VendaRepository;
import com.acougue.security.UsuarioAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("NFC-e da venda (CPF na nota)")
class NfceServiceTest {

    private static final String CPF = "52998224725";

    private final ConfiguracaoFiscalRepository configRepo = mock(ConfiguracaoFiscalRepository.class);
    private final DocumentoFiscalRepository documentoRepo = mock(DocumentoFiscalRepository.class);
    private final VendaRepository vendaRepo = mock(VendaRepository.class);
    private final ItensVendaRepository itensRepo = mock(ItensVendaRepository.class);
    private final PagamentoVendaRepository pagamentoRepo = mock(PagamentoVendaRepository.class);
    private final CredencialCripto cripto = mock(CredencialCripto.class);
    private final ProvedorFiscal provedor = mock(ProvedorFiscal.class);

    private NfceService service;
    private ConfiguracaoFiscal config;
    private Venda venda;

    @BeforeEach
    void setUp() {
        service = new NfceService(configRepo, documentoRepo, vendaRepo, itensRepo, pagamentoRepo, cripto, List.of(provedor));
        config = ConfiguracaoFiscal.builder().id((short) 1).emitirNfce(true).provedor("FOCUS").ambiente("HOMOLOGACAO")
                .razaoSocial("Loja Teste Ltda").cnpj("11222333000181").inscricaoEstadual("1234567850")
                .logradouro("Rua XV").numeroEndereco("100").bairro("Centro").municipio("Rolândia")
                .nfceSerie((short) 2).nfceProximoNumero(41)
                .certificadoCripto("c").certificadoSenhaCripto("s").cscId("000001").cscTokenCripto("t")
                .build();
        when(configRepo.travar()).thenReturn(Optional.of(config));
        when(configRepo.findById((short) 1)).thenReturn(Optional.of(config));
        when(provedor.codigo()).thenReturn("FOCUS");
        when(cripto.decifrar("c")).thenReturn("AAEC");
        when(cripto.decifrar("s")).thenReturn("senha");
        when(cripto.decifrar("t")).thenReturn("CSC");

        // Duas carnes (R$ 60 + R$ 40) com R$ 10 de desconto no total: a venda fecha em R$ 90
        venda = Venda.builder().id(7L).status("FECHADA").subtotal(new BigDecimal("100.00"))
                .desconto(new BigDecimal("10.00")).total(new BigDecimal("90.00")).troco(BigDecimal.ZERO).build();
        when(vendaRepo.findById(7L)).thenReturn(Optional.of(venda));
        when(itensRepo.findByVendaId(7L)).thenReturn(List.of(item("Picanha", "60.00"), item("Alcatra", "40.00")));
        when(pagamentoRepo.findByVendaId(7L)).thenReturn(List.of(
                PagamentoVenda.builder().formaPagamento("DEBITO").valor(new BigDecimal("50.00")).build(),
                PagamentoVenda.builder().formaPagamento("VOUCHER").valor(new BigDecimal("40.00")).build()));
        when(documentoRepo.save(any())).thenAnswer(inv -> {
            DocumentoFiscal d = inv.getArgument(0);
            if (d.getId() == null) d.setId(99L);
            return d;
        });

        UsuarioAutenticado u = new UsuarioAutenticado(9L, "caixa", "Caixa", "CAIXA", false, Map.of());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u, null));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private static ItensVenda item(String nome, String total) {
        Produto p = Produto.builder().nome(nome).codigoInterno(nome.toUpperCase()).unidadeMedida("KG")
                .ncm("02013000").cfop("5102").csosn("102").origem("0").build();
        return ItensVenda.builder().produto(p).quantidade(BigDecimal.ONE).precoUnitario(new BigDecimal(total))
                .descontoItem(BigDecimal.ZERO).totalItem(new BigDecimal(total)).build();
    }

    private void autorizar() {
        when(provedor.emitirNfce(any(), any())).thenReturn(new RetornoEmissao("AUTORIZADA",
                "41261111222333000181650020000000411123456780", "141260000000001", "Autorizado",
                "http://www.fazenda.pr.gov.br/nfce/qrcode?p=x", "http://www.fazenda.pr.gov.br/nfce/consulta",
                "<xml/>", "ext-1", LocalDateTime.now()));
    }

    @Test
    @DisplayName("emite com o CPF do consumidor, o próximo número da série e grava a chave na venda")
    void emiteComCpf() {
        autorizar();

        NfceDTO r = service.emitirParaVenda(7L, "529.982.247-25");

        assertThat(r.status()).isEqualTo("AUTORIZADA");
        assertThat(r.numero()).isEqualTo(41);
        assertThat(r.serie()).isEqualTo((short) 2);
        assertThat(r.destinatarioDocumento()).isEqualTo(CPF);
        assertThat(r.qrCodeSvg()).startsWith("<svg");
        assertThat(config.getNfceProximoNumero()).isEqualTo(42);
        assertThat(venda.getNfceChave()).isEqualTo("41261111222333000181650020000000411123456780");
        assertThat(venda.getNfceStatus()).isEqualTo("AUTORIZADA");

        ArgumentCaptor<CredencialFiscal> cred = ArgumentCaptor.forClass(CredencialFiscal.class);
        ArgumentCaptor<NfceEmissao> nota = ArgumentCaptor.forClass(NfceEmissao.class);
        verify(provedor).emitirNfce(nota.capture(), cred.capture());
        assertThat(cred.getValue().certificadoSenha()).isEqualTo("senha");
        assertThat(cred.getValue().cscToken()).isEqualTo("CSC");
        assertThat(nota.getValue().destinatarioDocumento()).isEqualTo(CPF);
        assertThat(nota.getValue().pagamentos()).extracting(NfceEmissao.Pagamento::formaPagamento).containsExactly("04", "10");
    }

    @Test
    @DisplayName("o desconto da venda é rateado pelos itens e a nota fecha no total")
    void rateiaDesconto() {
        autorizar();

        service.emitirParaVenda(7L, null);

        ArgumentCaptor<NfceEmissao> nota = ArgumentCaptor.forClass(NfceEmissao.class);
        verify(provedor).emitirNfce(nota.capture(), any());
        List<NfceEmissao.Item> itens = nota.getValue().itens();
        assertThat(itens).extracting(NfceEmissao.Item::desconto)
                .usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("6.00"), new BigDecimal("4.00"));
        BigDecimal fechado = itens.stream().map(i -> i.valorBruto().subtract(i.desconto())).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(fechado).isEqualByComparingTo(nota.getValue().total());
        assertThat(nota.getValue().destinatarioDocumento()).isNull();
    }

    @Test
    @DisplayName("CPF errado é recusado antes de gastar número de nota")
    void cpfInvalido() {
        assertThatThrownBy(() -> service.emitirParaVenda(7L, "529.982.247-24"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("CPF ou CNPJ inválido");
        assertThat(config.getNfceProximoNumero()).isEqualTo(41);
        verify(provedor, never()).emitirNfce(any(), any());
    }

    @Test
    @DisplayName("produto sem NCM, CFOP ou CSOSN: diz qual completar, sem gastar número")
    void produtoSemDadosFiscais() {
        ItensVenda semNcm = item("Linguiça", "10.00");
        semNcm.getProduto().setNcm(null);
        when(itensRepo.findByVendaId(7L)).thenReturn(List.of(item("Picanha", "60.00"), semNcm));

        assertThatThrownBy(() -> service.emitirParaVenda(7L, CPF))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Linguiça");
        assertThat(config.getNfceProximoNumero()).isEqualTo(41);
    }

    @Test
    @DisplayName("venda que já tem NFC-e autorizada devolve a mesma, sem emitir de novo")
    void naoEmiteDuasVezes() {
        DocumentoFiscal existente = DocumentoFiscal.builder().id(5L).modelo((short) 65).serie((short) 2).numero(40)
                .ambiente("HOMOLOGACAO").status("AUTORIZADA").vendaId(7L).build();
        when(documentoRepo.findFirstByVendaIdAndModeloAndStatus(7L, (short) 65, "AUTORIZADA")).thenReturn(Optional.of(existente));

        NfceDTO r = service.emitirParaVenda(7L, CPF);

        assertThat(r.numero()).isEqualTo(40);
        verify(provedor, never()).emitirNfce(any(), any());
        assertThat(config.getNfceProximoNumero()).isEqualTo(41);
    }

    @Test
    @DisplayName("falha de comunicação vira nota com status ERRO, e a venda segue fechada")
    void falhaDoProvedor() {
        when(provedor.emitirNfce(any(), any())).thenThrow(new RuntimeException("timeout"));

        NfceDTO r = service.emitirParaVenda(7L, CPF);

        assertThat(r.status()).isEqualTo("ERRO");
        assertThat(r.mensagem()).contains("timeout");
        assertThat(venda.getStatus()).isEqualTo("FECHADA");
        assertThat(venda.getNfceStatus()).isEqualTo("ERRO");
    }

    @Test
    @DisplayName("emissão desligada ou venda ainda aberta: recusa")
    void recusas() {
        venda.setStatus("ABERTA");
        assertThatThrownBy(() -> service.emitirParaVenda(7L, CPF)).hasMessageContaining("finalizada");

        config.setEmitirNfce(false);
        assertThatThrownBy(() -> service.emitirParaVenda(7L, CPF)).hasMessageContaining("desligada");
    }

    @Test
    @DisplayName("provedor simulado nunca emite em produção")
    void simuladoNaoEmiteEmProducao() {
        when(provedor.simulado()).thenReturn(true);
        config.setAmbiente("PRODUCAO");

        assertThatThrownBy(() -> service.emitirParaVenda(7L, CPF)).hasMessageContaining("simulado");
        verify(provedor, never()).emitirNfce(any(), any());
    }

    @Test
    @DisplayName("provedor simulado: chave de 44 dígitos válida e QR Code no formato do Paraná")
    void provedorSimulado() {
        ProvedorFiscalSimulado simulado = new ProvedorFiscalSimulado();
        NfceEmissao nota = new NfceEmissao("HOMOLOGACAO", "PR",
                new NfceEmissao.Emitente("11222333000181", null, null, null, (short) 1, null, null, null, null, null, null, null, null),
                (short) 2, 41, LocalDateTime.of(2026, 11, 8, 10, 0), null, List.of(), List.of(), BigDecimal.ZERO,
                BigDecimal.TEN, 7L);

        RetornoEmissao r = simulado.emitirNfce(nota, new CredencialFiscal(new byte[0], "", "000001", "CSC"));

        assertThat(r.autorizada()).isTrue();
        assertThat(ChaveAcesso.valida(r.chave())).isTrue();
        assertThat(r.chave()).startsWith("412611" + "11222333000181" + "65" + "002" + "000000041" + "1");
        assertThat(r.urlQrcode()).matches("http://www\\.fazenda\\.pr\\.gov\\.br/nfce/qrcode\\?p=" + r.chave() + "\\|2\\|2\\|1\\|[0-9A-F]{40}");

        NfceEmissao producao = new NfceEmissao("PRODUCAO", "PR", nota.emitente(), (short) 2, 41, nota.dataEmissao(),
                null, List.of(), List.of(), BigDecimal.ZERO, BigDecimal.TEN, 7L);
        assertThat(simulado.emitirNfce(producao, null).status()).isEqualTo("ERRO");
    }

    @Test
    @DisplayName("chave de acesso: dígito verificador módulo 11 e CPF do consumidor")
    void chaveECpf() {
        String chave = ChaveAcesso.gerar("41", LocalDateTime.of(2026, 11, 8, 0, 0), "11222333000181", 65, 2, 41, 1, 10000001);
        assertThat(chave).hasSize(44);
        assertThat(ChaveAcesso.valida(chave)).isTrue();
        assertThat(ChaveAcesso.valida(chave.substring(0, 43) + ((chave.charAt(43) - '0' + 1) % 10))).isFalse();

        assertThat(DocumentosFiscais.cpfValido(CPF)).isTrue();
        assertThat(DocumentosFiscais.cpfValido("52998224724")).isFalse();
        assertThat(DocumentosFiscais.cpfValido("11111111111")).isFalse();
        assertThat(NfceService.destinatario(" ")).isNull();
        assertThat(NfceService.destinatario("11.222.333/0001-81")).isEqualTo("11222333000181");
    }
}
