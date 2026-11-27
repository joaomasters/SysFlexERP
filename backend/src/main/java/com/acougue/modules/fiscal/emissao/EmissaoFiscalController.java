package com.acougue.modules.fiscal.emissao;

import com.acougue.entity.DocumentoFiscal;
import com.acougue.entity.Modulo;
import com.acougue.modules.fiscal.emissao.dto.CertificadoDTO;
import com.acougue.modules.fiscal.emissao.dto.ConfiguracaoFiscalDTO;
import com.acougue.modules.fiscal.emissao.dto.SalvarConfiguracaoFiscalDTO;
import com.acougue.repository.DocumentoFiscalRepository;
import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/** Configuração da emissão de NFC-e / NF-e e a lista dos documentos emitidos. */
@RestController
@RequestMapping("/fiscal")
@RequiredArgsConstructor
public class EmissaoFiscalController {

    private final ConfiguracaoFiscalService configuracao;
    private final DocumentoFiscalRepository documentos;
    private final XmlMensalService xmlMensal;
    private final FaturamentoMensalService faturamentoMensal;

    @ExigirPermissao(modulo = Modulo.FISCAL, acao = Acao.VER)
    @GetMapping("/configuracao")
    public ResponseEntity<ConfiguracaoFiscalDTO> buscar() {
        return ResponseEntity.ok(configuracao.buscar());
    }

    @ExigirPermissao(modulo = Modulo.FISCAL, acao = Acao.EDITAR)
    @PutMapping("/configuracao")
    public ResponseEntity<ConfiguracaoFiscalDTO> salvar(@RequestBody @Valid SalvarConfiguracaoFiscalDTO dto) {
        return ResponseEntity.ok(configuracao.salvar(dto));
    }

    @ExigirPermissao(modulo = Modulo.FISCAL, acao = Acao.EDITAR)
    @PostMapping("/configuracao/certificado")
    public ResponseEntity<ConfiguracaoFiscalDTO> enviarCertificado(@RequestBody @Valid CertificadoDTO dto) {
        return ResponseEntity.ok(configuracao.salvarCertificado(dto));
    }

    @ExigirPermissao(modulo = Modulo.FISCAL, acao = Acao.EXCLUIR)
    @DeleteMapping("/configuracao/certificado")
    public ResponseEntity<ConfiguracaoFiscalDTO> removerCertificado() {
        return ResponseEntity.ok(configuracao.removerCertificado());
    }

    /** Um mês (mes=2026-11) ou os últimos N dias (90 por padrão); modelo 55/65 e status opcionais. */
    @ExigirPermissao(modulo = Modulo.FISCAL, acao = Acao.VER)
    @GetMapping("/documentos")
    public ResponseEntity<List<DocumentoFiscal>> listar(@RequestParam(required = false) Short modelo,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) YearMonth mes,
                                                        @RequestParam(defaultValue = "90") int dias) {
        if (mes != null) {
            LocalDateTime de = mes.atDay(1).atStartOfDay();
            return ResponseEntity.ok(documentos.buscarPeriodo(modelo, status, de, de.plusMonths(1)));
        }
        LocalDateTime desde = LocalDateTime.now().minusDays(Math.min(Math.max(dias, 1), 366));
        return ResponseEntity.ok(documentos.buscar(modelo, status, desde));
    }

    /** XML autorizado de uma nota. */
    @ExigirPermissao(modulo = Modulo.FISCAL, acao = Acao.VER)
    @GetMapping("/documentos/{id}/xml")
    public ResponseEntity<byte[]> xml(@PathVariable Long id) {
        return arquivo(xmlMensal.xml(id), MediaType.APPLICATION_XML);
    }

    /** Todos os XMLs de produção do mês num .zip, com um resumo.csv (para o contador). */
    @ExigirPermissao(modulo = Modulo.FISCAL, acao = Acao.VER)
    @GetMapping("/documentos/xml-do-mes")
    public ResponseEntity<byte[]> xmlDoMes(@RequestParam YearMonth mes) {
        return arquivo(xmlMensal.zipDoMes(mes), MediaType.parseMediaType("application/zip"));
    }

    /** Extrato de faturamento do mês: vendas fechadas por dia e forma de pagamento, com e sem NFC-e. */
    @ExigirPermissao(modulo = Modulo.FISCAL, acao = Acao.VER)
    @GetMapping("/faturamento-do-mes")
    public ResponseEntity<FaturamentoMensalService.Extrato> faturamentoDoMes(@RequestParam YearMonth mes) {
        return ResponseEntity.ok(faturamentoMensal.extrato(mes));
    }

    private static ResponseEntity<byte[]> arquivo(XmlMensalService.Arquivo a, MediaType tipo) {
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(a.nome()).build().toString())
                .body(a.conteudo());
    }
}
