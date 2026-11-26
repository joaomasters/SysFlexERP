package com.acougue.modules.fiscal.emissao;

import com.acougue.entity.DocumentoFiscal;
import com.acougue.exception.BusinessException;
import com.acougue.repository.DocumentoFiscalRepository;
import com.acougue.security.auditoria.AuditoriaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("XMLs do mês para o contador")
class XmlMensalServiceTest {

    private final DocumentoFiscalRepository repo = mock(DocumentoFiscalRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final XmlMensalService service = new XmlMensalService(repo, auditoria);

    private static DocumentoFiscal nota(int numero, String chave, String status, String valor) {
        return DocumentoFiscal.builder().id((long) numero).modelo(DocumentoFiscal.MODELO_NFCE).serie((short) 4).numero(numero)
                .ambiente("PRODUCAO").status(status).chave(chave).valorTotal(new BigDecimal(valor))
                .emitidaEm(LocalDateTime.of(2026, 11, 27, 10, numero)).xml("<nfeProc>" + numero + "</nfeProc>").build();
    }

    @Test
    @DisplayName("zip com um XML por nota (nome = chave) e o resumo; o mês vai de 1º a 1º")
    void zip() throws Exception {
        String chave = "41261128803350000105650040000000011000000017";
        when(repo.comXmlNoPeriodo(any(), any())).thenReturn(List.of(
                nota(1, chave, "AUTORIZADA", "10.50"), nota(2, null, "CANCELADA", "5.00")));

        XmlMensalService.Arquivo a = service.zipDoMes(YearMonth.of(2026, 11));

        verify(repo).comXmlNoPeriodo(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 12, 1, 0, 0));
        assertThat(a.nome()).isEqualTo("XML-notas-2026-11.zip");
        Map<String, String> arquivos = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(a.conteudo()), StandardCharsets.UTF_8)) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) arquivos.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertThat(arquivos).containsKeys(chave + "-nfce.xml", "serie4-numero2-nfce.xml", "resumo.csv");
        assertThat(arquivos.get(chave + "-nfce.xml")).isEqualTo("<nfeProc>1</nfeProc>");
        assertThat(arquivos.get("resumo.csv"))
                .contains("65;4;1;=\"" + chave + "\";27/11/2026 10:01:00;10,50;AUTORIZADA")
                .contains("65;4;2;;27/11/2026 10:02:00;5,00;CANCELADA");
        verify(auditoria).registrar(eq("FISCAL"), eq("BAIXAR_XML"), contains("autorizadas R$ 10.50"));
    }

    @Test
    @DisplayName("mês sem nota avisa em vez de baixar um zip vazio")
    void vazio() {
        when(repo.comXmlNoPeriodo(any(), any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.zipDoMes(YearMonth.of(2026, 10)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("10/2026");
    }

    @Test
    @DisplayName("nota sem XML (rejeitada, erro) não baixa")
    void semXml() {
        DocumentoFiscal d = nota(3, null, "REJEITADA", "1.00");
        d.setXml(null);
        when(repo.findById(3L)).thenReturn(java.util.Optional.of(d));
        assertThatThrownBy(() -> service.xml(3L)).isInstanceOf(BusinessException.class);
        assertThat(d.temXml()).isFalse();
    }
}
