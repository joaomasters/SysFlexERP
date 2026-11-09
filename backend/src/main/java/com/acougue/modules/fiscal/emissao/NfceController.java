package com.acougue.modules.fiscal.emissao;

import com.acougue.entity.Modulo;
import com.acougue.modules.fiscal.emissao.dto.EmitirNfceDTO;
import com.acougue.modules.fiscal.emissao.dto.NfceDTO;
import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** NFC-e do PDV: quem vende emite (permissão do PDV, não a do módulo Fiscal, que é dos administradores). */
@RestController
@RequestMapping("/fiscal/nfce")
@RequiredArgsConstructor
public class NfceController {

    private final NfceService nfce;

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.VER)
    @GetMapping("/situacao")
    public ResponseEntity<NfceService.Situacao> situacao() {
        return ResponseEntity.ok(nfce.situacao());
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR)
    @PostMapping("/venda/{vendaId}")
    public ResponseEntity<NfceDTO> emitir(@PathVariable Long vendaId, @RequestBody(required = false) @Valid EmitirNfceDTO dto) {
        return ResponseEntity.ok(nfce.emitirParaVenda(vendaId, dto == null ? null : dto.getDocumento()));
    }

    /** Para reimprimir o DANFE; 204 quando a venda não tem NFC-e. */
    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.VER)
    @GetMapping("/venda/{vendaId}")
    public ResponseEntity<NfceDTO> daVenda(@PathVariable Long vendaId) {
        return nfce.daVenda(vendaId).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }
}
