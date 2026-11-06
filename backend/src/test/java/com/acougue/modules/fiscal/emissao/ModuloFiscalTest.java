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
        private final ConfiguracaoFiscalService service = new ConfiguracaoFiscalService(
                repo, mock(ProdutoRepository.class), new CredencialCripto("chave-de-teste-com-tamanho-suficiente-123"), List.of());

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
