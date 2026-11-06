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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** Configuração da emissão de NFC-e / NF-e e a lista dos documentos emitidos. */
@RestController
@RequestMapping("/fiscal")
@RequiredArgsConstructor
public class EmissaoFiscalController {

    private final ConfiguracaoFiscalService configuracao;
    private final DocumentoFiscalRepository documentos;

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

    /** Últimos 90 dias por padrão; modelo 55/65 e status opcionais. */
    @ExigirPermissao(modulo = Modulo.FISCAL, acao = Acao.VER)
    @GetMapping("/documentos")
    public ResponseEntity<List<DocumentoFiscal>> listar(@RequestParam(required = false) Short modelo,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "90") int dias) {
        LocalDateTime desde = LocalDateTime.now().minusDays(Math.min(Math.max(dias, 1), 366));
        return ResponseEntity.ok(documentos.buscar(modelo, status, desde));
    }
}
