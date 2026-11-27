package com.acougue.modules.fiscal.emissao;

import com.acougue.entity.ConfiguracaoFiscal;
import com.acougue.exception.BusinessException;
import com.acougue.modules.fiscal.emissao.dto.CertificadoDTO;
import com.acougue.modules.fiscal.emissao.dto.ConfiguracaoFiscalDTO;
import com.acougue.modules.fiscal.emissao.dto.SalvarConfiguracaoFiscalDTO;
import com.acougue.modules.pagamento.CredencialCripto;
import com.acougue.repository.ConfiguracaoFiscalRepository;
import com.acougue.repository.ProdutoRepository;
import com.acougue.security.UsuarioAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Módulo fiscal (NFC-e / NF-e)")
class ModuloFiscalTest {

    private static byte[] pfxTeste() throws Exception {
        try (InputStream in = ModuloFiscalTest.class.getResourceAsStream("/fiscal/a1-teste.pfx")) {
            return in.readAllBytes();
        }
    }

    /** Emitente do Paraná completo e válido, com certificado e CSC. */
    private static ConfiguracaoFiscal completa() {
        return ConfiguracaoFiscal.builder().id((short) 1)
                .razaoSocial("Loja Teste Ltda").cnpj("11222333000181").inscricaoEstadual("1234567850")
                .logradouro("Rua XV de Novembro").numeroEndereco("100").bairro("Centro").municipio("Rolândia")
                .codigoMunicipio("4122404").cep("86600000")
                .certificadoCripto("v1:x").certificadoCnpj("11222333000181").certificadoValidade(LocalDateTime.now().plusYears(1))
                .cscId("000001").cscTokenCripto("v1:y")
                .build();
    }

    @Nested
    @DisplayName("Documentos do emitente")
    class Documentos {

        @Test
        @DisplayName("CNPJ numérico e alfanumérico (jul/2026), com dígitos verificadores")
        void cnpj() {
            assertThat(DocumentosFiscais.cnpjValido("11222333000181")).isTrue();
            assertThat(DocumentosFiscais.cnpjValido("12ABC34501DE35")).isTrue(); // exemplo da Receita
            assertThat(DocumentosFiscais.cnpjValido("11222333000182")).isFalse();
            assertThat(DocumentosFiscais.cnpjValido("11111111111111")).isFalse();
            assertThat(DocumentosFiscais.limpar("12.abc.345/01de-35")).isEqualTo("12ABC34501DE35");
        }

        @Test
        @DisplayName("Inscrição Estadual do Paraná")
        void inscricaoPr() {
            assertThat(DocumentosFiscais.inscricaoEstadualPrValida("1234567850")).isTrue(); // exemplo do Sintegra
            assertThat(DocumentosFiscais.inscricaoEstadualPrValida("1234567851")).isFalse();
            assertThat(DocumentosFiscais.inscricaoEstadualPrValida("123456785")).isFalse();
        }
    }

    @Nested
    @DisplayName("Certificado A1")
    class Certificado {

        @Test
        @DisplayName("lê titular, CNPJ e validade do .pfx")
        void le() throws Exception {
            CertificadoDigital c = CertificadoDigital.ler(pfxTeste(), "senha123");
            assertThat(c.titular()).isEqualTo("LOJA TESTE LTDA:11222333000181");
            assertThat(c.cnpj()).isEqualTo("11222333000181");
            assertThat(c.vencido()).isFalse();
        }

        @Test
        @DisplayName("senha errada vira mensagem clara")
        void senhaErrada() throws Exception {
            byte[] pfx = pfxTeste();
            assertThatThrownBy(() -> CertificadoDigital.ler(pfx, "errada"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("senha");
        }
    }

    @Nested
    @DisplayName("Pendências")
    class Pendencias {

        @Test
        @DisplayName("provedor de API (Focus): não cobra certificado nem CSC aqui, cobra token do ambiente e CST de PIS/COFINS")
        void provedorDeApi() {
            ProvedorFiscal api = new ProvedorFiscal() {
                public String codigo() { return "FOCUS_NFE"; }
                public String rotulo() { return "Focus"; }
                public boolean usaCertificadoLocal() { return false; }
                public boolean exigeToken() { return true; }
                public RetornoEmissao emitirNfce(NfceEmissao n, CredencialFiscal c) { return null; }
            };
            var c = ModuloFiscalTest.completa();
            c.setCertificadoCripto(null);
            c.setCscId(null);
            c.setCscTokenCripto(null);
            assertThat(PendenciasFiscais.calcular(c, true, true, api).nfce())
                    .containsExactly("Informe o token de homologação do provedor fiscal.",
                            "Informe o CST de PIS/COFINS dos itens (confirme com a contabilidade).");
            c.setProvedorTokenHomologacaoCripto("v1:x");
            c.setPisCofinsCst("49");
            assertThat(PendenciasFiscais.calcular(c, true, true, api).nfce()).isEmpty();
            c.setAmbiente("PRODUCAO");
            assertThat(PendenciasFiscais.calcular(c, true, true, api).nfce()).containsExactly("Informe o token de produção do provedor fiscal.");
        }

        @Test
        @DisplayName("tudo certo e com provedor: nenhuma pendência")
        void completa() {
            PendenciasFiscais p = PendenciasFiscais.calcular(ModuloFiscalTest.completa(), true, true);
            assertThat(p.nfce()).isEmpty();
            assertThat(p.nfe()).isEmpty();
        }

        @Test
        @DisplayName("sem provedor da SEFAZ nada pode ser ligado; CSC só pesa na NFC-e")
        void provedorECsc() {
            ConfiguracaoFiscal c = ModuloFiscalTest.completa();
            c.setCscTokenCripto(null);
            PendenciasFiscais p = PendenciasFiscais.calcular(c, true, false);
            assertThat(p.nfe()).singleElement().asString().contains("provedor");
            assertThat(p.nfce()).hasSize(2).anyMatch(m -> m.contains("CSC"));
        }

        @Test
        @DisplayName("fora do Paraná, certificado de outro CNPJ ou vencido aparecem como pendência")
        void regrasPr() {
            ConfiguracaoFiscal c = ModuloFiscalTest.completa();
            c.setCep("01310100");                  // São Paulo
            c.setCodigoMunicipio("3550308");
            c.setCertificadoCnpj("99888777000166");
            c.setCertificadoValidade(LocalDateTime.now().minusDays(1));
            assertThat(PendenciasFiscais.calcular(c, true, true).nfe())
                    .anyMatch(m -> m.contains("CEP do Paraná"))
                    .anyMatch(m -> m.contains("começa com 41"))
                    .anyMatch(m -> m.contains("outro CNPJ"))
                    .anyMatch(m -> m.contains("venceu"));
        }
    }

    @Nested
    @DisplayName("Serviço")
    class Servico {

        private final ConfiguracaoFiscalRepository repo = mock(ConfiguracaoFiscalRepository.class);
        private final com.acougue.modules.fiscal.parametros.EnquadramentoService enquadramento =
                mock(com.acougue.modules.fiscal.parametros.EnquadramentoService.class);
        private final com.acougue.repository.DocumentoFiscalRepository documentoRepo = mock(com.acougue.repository.DocumentoFiscalRepository.class);
        private final com.acougue.security.auditoria.AuditoriaService auditoria = mock(com.acougue.security.auditoria.AuditoriaService.class);
        private final ConfiguracaoFiscalService service = new ConfiguracaoFiscalService(
                repo, mock(ProdutoRepository.class), new CredencialCripto("chave-de-teste-com-tamanho-suficiente-123"), List.of(),
                enquadramento, documentoRepo, auditoria);

        Servico() {
            when(repo.findById((short) 1)).thenReturn(Optional.of(ModuloFiscalTest.completa()));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            UsuarioAutenticado u = new UsuarioAutenticado(1L, "dono", "Dono", "SUPER_ADMIN", true, Map.of(), "tenant_a");
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u, null));
        }

        @AfterEach
        void limpar() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("não liga a NFC-e com pendência: o PDV segue no cupom não fiscal")
        void naoLigaComPendencia() {
            SalvarConfiguracaoFiscalDTO dto = new SalvarConfiguracaoFiscalDTO();
            dto.setAmbiente("HOMOLOGACAO");
            dto.setProvedor("NENHUM");
            dto.setEmitirNfce(true);
            assertThatThrownBy(() -> service.salvar(dto))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("provedor");
        }

        @Test
        @DisplayName("numeração nunca volta: próximo número menor ou igual ao último usado é recusado")
        void numeracaoNaoVolta() {
            when(documentoRepo.maiorNumero((short) 65, (short) 1, "HOMOLOGACAO")).thenReturn(40);
            SalvarConfiguracaoFiscalDTO dto = new SalvarConfiguracaoFiscalDTO();
            dto.setAmbiente("HOMOLOGACAO");
            dto.setProvedor("NENHUM");
            dto.setNfceProximoNumero(40);
            assertThatThrownBy(() -> service.salvar(dto)).hasMessageContaining("maior que 40");
            dto.setNfceProximoNumero(41);
            service.salvar(dto);
        }

        @Test
        @DisplayName("trocar o regime na tela abre vigência nova a partir de hoje, sem reescrever o passado")
        void regimeAbreVigencia() {
            SalvarConfiguracaoFiscalDTO dto = new SalvarConfiguracaoFiscalDTO();
            dto.setAmbiente("HOMOLOGACAO");
            dto.setProvedor("NENHUM");
            dto.setRegimeTributario((short) 3);
            service.salvar(dto);
            verify(enquadramento).registrar(eq((short) 3), eq(java.time.LocalDate.now()), any());
        }

        @Test
        @DisplayName("auditoria: troca de série e de \"quando emitir\" vira uma linha; sem mudança, nada")
        void auditaTrocas() {
            SalvarConfiguracaoFiscalDTO dto = new SalvarConfiguracaoFiscalDTO();
            dto.setAmbiente("HOMOLOGACAO");
            dto.setProvedor("NENHUM");
            dto.setNfceSerie((short) 4);
            dto.setNfceModo("TODAS");
            service.salvar(dto);
            verify(auditoria).registrar(eq("FISCAL"), eq("CONFIG_FISCAL"),
                    argThat(d -> d.contains("série NFC-e 1 → 4") && d.contains("→ em todas as vendas")));
            verify(auditoria, never()).registrar(eq("FISCAL"), eq("NFCE_LIGADA"), any());

            clearInvocations(auditoria);
            service.salvar(dto);   // mesma configuração de novo
            verify(auditoria, never()).registrar(any(), any(), any());
        }

        @Test
        @DisplayName("auditoria: desligar a NFC-e (aqui, ao remover o certificado) registra quem, quando e por quê")
        void auditaDesligar() {
            ConfiguracaoFiscal ligada = ModuloFiscalTest.completa();
            ligada.setEmitirNfce(true);
            when(repo.findById((short) 1)).thenReturn(Optional.of(ligada));
            service.removerCertificado();
            verify(auditoria).registrar(eq("FISCAL"), eq("NFCE_DESLIGADA"),
                    argThat(d -> d.contains("desligada") && d.contains("certificado digital removido")));
        }

        @Test
        @DisplayName("trocas(): ambiente, provedor, numeração e modo")
        void trocas() {
            var a = new ConfiguracaoFiscalService.Estado(true, false, "HOMOLOGACAO", "FOCUS_NFE", "SOB_DEMANDA", (short) 1, 10, (short) 1, 1);
            var d = new ConfiguracaoFiscalService.Estado(true, false, "PRODUCAO", "FOCUS_NFE", "SOB_DEMANDA", (short) 1, 12, (short) 1, 1);
            assertThat(ConfiguracaoFiscalService.trocas(a, d))
                    .containsExactly("ambiente HOMOLOGACAO → PRODUCAO", "próximo número NFC-e 10 → 12");
        }

        @Test
        @DisplayName("guarda o certificado cifrado e nunca o devolve")
        void certificado() throws Exception {
            CertificadoDTO dto = new CertificadoDTO();
            dto.setArquivoBase64(Base64.getEncoder().encodeToString(pfxTeste()));
            dto.setSenha("senha123");

            ConfiguracaoFiscalDTO r = service.salvarCertificado(dto);

            assertThat(r.certificadoConfigurado()).isTrue();
            assertThat(r.certificadoCnpj()).isEqualTo("11222333000181");
            assertThat(r.toString()).doesNotContain("senha123");
            verify(repo).save(argThat(c -> c.getCertificadoCripto().startsWith("v1:")
                    && c.getCertificadoSenhaCripto().startsWith("v1:")));
        }
    }
}
