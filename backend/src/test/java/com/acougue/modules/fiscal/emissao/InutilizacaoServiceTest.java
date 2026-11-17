package com.acougue.modules.fiscal.emissao;

import com.acougue.entity.ConfiguracaoFiscal;
import com.acougue.entity.DocumentoFiscal;
import com.acougue.modules.pagamento.CredencialCripto;
import com.acougue.repository.ConfiguracaoFiscalRepository;
import com.acougue.repository.DocumentoFiscalRepository;
import com.acougue.security.UsuarioAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionOperations;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Inutilização de numeração")
class InutilizacaoServiceTest {

    private static final String JUSTIFICATIVA = "Falha na transmissao do PDV";

    private final ConfiguracaoFiscalRepository configRepo = mock(ConfiguracaoFiscalRepository.class);
    private final DocumentoFiscalRepository documentoRepo = mock(DocumentoFiscalRepository.class);
    private final InutilizacaoRepository repo = mock(InutilizacaoRepository.class);
    private final CredencialCripto cripto = mock(CredencialCripto.class);
    private final ProvedorFiscal provedor = mock(ProvedorFiscal.class);
    private final InutilizacaoService service = new InutilizacaoService(configRepo, documentoRepo, repo, cripto,
            List.of(provedor), TransactionOperations.withoutTransaction());
    private final ConfiguracaoFiscal config = ConfiguracaoFiscal.builder().id((short) 1).ambiente("HOMOLOGACAO").uf("PR")
            .cnpj("11222333000181").provedor("FOCUS").nfceSerie((short) 1).nfceProximoNumero(11)
            .nfeSerie((short) 1).nfeProximoNumero(1).certificadoCripto("c").certificadoSenhaCripto("s").build();
    private final Map<Integer, DocumentoFiscal> docs = new HashMap<>();
    private Inutilizacao salva;

    @BeforeEach
    void setUp() {
        when(configRepo.findById((short) 1)).thenReturn(Optional.of(config));
        when(configRepo.travar()).thenReturn(Optional.of(config));
        when(provedor.codigo()).thenReturn("FOCUS");
        when(provedor.rotulo()).thenReturn("Focus");
        when(cripto.decifrar("c")).thenReturn("AAEC");
        when(cripto.decifrar("s")).thenReturn("senha");
        // 1-3 autorizadas, 4 rejeitada, 5 erro, 6 sem documento, 7 desconhecido, 8-10 autorizadas
        for (int n : new int[]{1, 2, 3, 8, 9, 10}) doc(n, "AUTORIZADA");
        doc(4, "REJEITADA");
        doc(5, "ERRO");
        doc(7, "DESCONHECIDO");
        when(documentoRepo.menorNumero((short) 65, (short) 1, "HOMOLOGACAO")).thenReturn(1);
        when(documentoRepo.findByModeloAndSerieAndAmbienteAndNumeroBetweenOrderByNumero(eq((short) 65), eq((short) 1),
                eq("HOMOLOGACAO"), anyInt(), anyInt())).thenAnswer(inv -> {
                    int de = inv.getArgument(3);
                    int ate = inv.getArgument(4);
                    return docs.values().stream().filter(d -> d.getNumero() >= de && d.getNumero() <= ate)
                            .sorted(Comparator.comparing(DocumentoFiscal::getNumero)).toList();
                });
        when(repo.findByModeloAndSerieAndAmbienteAndStatusIn(anyShort(), anyShort(), anyString(), anyList())).thenReturn(List.of());
        when(repo.save(any())).thenAnswer(inv -> {
            Inutilizacao i = inv.getArgument(0);
            if (i.getId() == null) i.setId(1L);
            salva = i;
            return i;
        });
        when(repo.findById(1L)).thenAnswer(inv -> Optional.ofNullable(salva));
        UsuarioAutenticado u = new UsuarioAutenticado(1L, "dono", "Dono", "SUPER_ADMIN", true, Map.of());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u, null));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private void doc(int numero, String status) {
        docs.put(numero, DocumentoFiscal.builder().id((long) numero).modelo((short) 65).serie((short) 1).numero(numero)
                .ambiente("HOMOLOGACAO").status(status).build());
    }

    @Test
    @DisplayName("lacunas: rejeitada, não transmitida e pulada podem; resultado desconhecido não")
    void lacunas() {
        List<InutilizacaoService.Lacuna> l = service.lacunas((short) 65);
        assertThat(l).extracting(InutilizacaoService.Lacuna::numeroInicial, InutilizacaoService.Lacuna::numeroFinal,
                        InutilizacaoService.Lacuna::motivo, InutilizacaoService.Lacuna::inutilizavel)
                .containsExactly(tuple(4, 4, "Rejeitada", true), tuple(5, 5, "Não transmitida", true),
                        tuple(6, 6, "Sem documento", true), tuple(7, 7, "Resultado desconhecido", false));
    }

    @Test
    @DisplayName("homologada: grava protocolo e marca os documentos da faixa como INUTILIZADA")
    void homologa() {
        when(provedor.inutilizar(any(), any())).thenAnswer(inv -> {
            assertThat(salva.getStatus()).isEqualTo("DESCONHECIDO"); // gravado antes de transmitir
            PedidoInutilizacao p = inv.getArgument(0);
            assertThat(p.numeroInicial()).isEqualTo(4);
            assertThat(p.cnpj()).isEqualTo("11222333000181");
            return Optional.of(new PedidoInutilizacao.Retorno("HOMOLOGADA", "141260000000077", "Homologado"));
        });

        Inutilizacao r = service.inutilizar(new InutilizacaoService.Pedido((short) 65, 4, 6, JUSTIFICATIVA));

        assertThat(r.getStatus()).isEqualTo("HOMOLOGADA");
        assertThat(docs.get(4).getStatus()).isEqualTo("INUTILIZADA");
        assertThat(docs.get(5).getStatus()).isEqualTo("INUTILIZADA");
    }

    @Test
    @DisplayName("recusa número autorizado, desconhecido, futuro e justificativa curta")
    void recusas() {
        assertThatThrownBy(() -> service.inutilizar(new InutilizacaoService.Pedido((short) 65, 3, 4, JUSTIFICATIVA)))
                .hasMessageContaining("número 3");
        assertThatThrownBy(() -> service.inutilizar(new InutilizacaoService.Pedido((short) 65, 7, 7, JUSTIFICATIVA)))
                .hasMessageContaining("consulte a situação");
        assertThatThrownBy(() -> service.inutilizar(new InutilizacaoService.Pedido((short) 65, 11, 12, JUSTIFICATIVA)))
                .hasMessageContaining("próximo número");
        assertThatThrownBy(() -> service.inutilizar(new InutilizacaoService.Pedido((short) 65, 4, 4, "curta")))
                .hasMessageContaining("15 a 255");
        verify(provedor, never()).inutilizar(any(), any());
    }

    @Test
    @DisplayName("sem resposta: pedido fica DESCONHECIDO e documentos não mudam")
    void semResposta() {
        when(provedor.inutilizar(any(), any())).thenThrow(new RuntimeException("timeout"));
        Inutilizacao r = service.inutilizar(new InutilizacaoService.Pedido((short) 65, 4, 4, JUSTIFICATIVA));
        assertThat(r.getStatus()).isEqualTo("DESCONHECIDO");
        assertThat(r.getMensagem()).contains("portal da SEFAZ");
        assertThat(docs.get(4).getStatus()).isEqualTo("REJEITADA");
    }

    @Test
    @DisplayName("provedor que não inutiliza: o pedido gravado é desfeito")
    void provedorSemSuporte() {
        when(provedor.inutilizar(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.inutilizar(new InutilizacaoService.Pedido((short) 65, 4, 4, JUSTIFICATIVA)))
                .hasMessageContaining("não inutiliza");
        verify(repo).deleteById(1L);
    }
}
