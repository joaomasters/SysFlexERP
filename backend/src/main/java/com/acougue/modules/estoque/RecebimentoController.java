package com.acougue.modules.estoque;

import com.acougue.entity.Modulo;
import com.acougue.entity.RecebimentoMercadoria;
import com.acougue.modules.estoque.dto.RecebimentoDTO;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.ExigirPermissao;
import com.acougue.security.IdentificacaoUsuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/estoque/recebimentos")
@RequiredArgsConstructor
public class RecebimentoController {

    private final RecebimentoService recebimentoService;
    private final IdentificacaoUsuario identificacao;

    @ExigirPermissao(modulo = Modulo.RECEBIMENTO, acao = Acao.VER)
    @GetMapping
    public ResponseEntity<List<RecebimentoMercadoria>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String fornecedor) {

        boolean semFiltro = inicio == null && fim == null && (fornecedor == null || fornecedor.isBlank());

        List<RecebimentoMercadoria> recebimentos;
        if (semFiltro) {
            // Compatibilidade: DesossaPage usa esse mesmo endpoint sem filtros
            // pra popular o combo de "vincular NF de recebimento".
            recebimentos = recebimentoService.listar();
        } else {
            LocalDateTime desde = inicio != null ? inicio.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
            LocalDateTime ate   = fim    != null ? fim.atTime(LocalTime.MAX) : LocalDateTime.now();
            recebimentos = recebimentoService.listar(desde, ate, fornecedor);
        }

        identificacao.preencher(recebimentos,
                RecebimentoMercadoria::getUsuarioId, RecebimentoMercadoria::setUsuarioNome);
        return ResponseEntity.ok(recebimentos);
    }

    @ExigirPermissao(modulo = Modulo.RECEBIMENTO, acao = Acao.VER)
    @GetMapping("/{id}")
    public ResponseEntity<RecebimentoMercadoria> buscar(@PathVariable Long id) {
        RecebimentoMercadoria recebimento = recebimentoService.buscar(id);
        identificacao.preencherItem(recebimento,
                RecebimentoMercadoria::getUsuarioId, RecebimentoMercadoria::setUsuarioNome);
        return ResponseEntity.ok(recebimento);
    }

    @ExigirPermissao(modulo = Modulo.RECEBIMENTO, acao = Acao.CRIAR)
    @PostMapping
    public ResponseEntity<RecebimentoMercadoria> registrar(@RequestBody @Valid RecebimentoDTO dto) {
        // Quem deu entrada é SEMPRE o usuário logado (token).
        return ResponseEntity.ok(recebimentoService.registrar(dto, ContextoUsuario.atual().getUsuarioId()));
    }

    /**
     * Cancela a entrada (até 7 dias) e estorna o estoque. Corpo: { "motivo": "..." }.
     * Permissão de EXCLUIR: desfaz movimentação de estoque, é ação de administrador.
     */
    @ExigirPermissao(modulo = Modulo.RECEBIMENTO, acao = Acao.EXCLUIR)
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<RecebimentoMercadoria> cancelar(@PathVariable Long id, @RequestBody Map<String, String> body) {
        RecebimentoMercadoria rec = recebimentoService.cancelar(id, body.get("motivo"), ContextoUsuario.atual().getUsuarioId());
        identificacao.preencherItem(rec, RecebimentoMercadoria::getUsuarioId, RecebimentoMercadoria::setUsuarioNome);
        return ResponseEntity.ok(rec);
    }

    @ExigirPermissao(modulo = Modulo.RECEBIMENTO, acao = Acao.EDITAR)
    @PutMapping("/{id}/xml")
    public ResponseEntity<RecebimentoMercadoria> uploadXml(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(recebimentoService.uploadXml(id, body.get("xml")));
    }
}