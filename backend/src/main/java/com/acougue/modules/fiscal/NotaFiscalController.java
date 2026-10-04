package com.acougue.modules.fiscal;

import com.acougue.entity.Modulo;
import com.acougue.entity.NotaFiscalSaida;
import com.acougue.modules.fiscal.dto.NotaFiscalSaidaDTO;
import com.acougue.security.Acao;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.ExigirPermissao;
import com.acougue.security.IdentificacaoUsuario;
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
@RequestMapping("/fiscal/notas")
@RequiredArgsConstructor
public class NotaFiscalController {

    private final NotaFiscalService notaFiscalService;
    private final IdentificacaoUsuario identificacao;

    @ExigirPermissao(modulo = Modulo.NF_SAIDA, acao = Acao.VER)
    @GetMapping
    public ResponseEntity<List<NotaFiscalSaida>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String cliente) {

        // Mesmos filtros (e mesma regra de compatibilidade) do Recebimento de Mercadoria:
        // sem nenhum filtro, devolve tudo.
        boolean semFiltro = inicio == null && fim == null && (cliente == null || cliente.isBlank());

        List<NotaFiscalSaida> notas;
        if (semFiltro) {
            notas = notaFiscalService.listar();
        } else {
            LocalDateTime desde = inicio != null ? inicio.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
            LocalDateTime ate   = fim    != null ? fim.atTime(LocalTime.MAX) : LocalDateTime.now();
            notas = notaFiscalService.listar(desde, ate, cliente);
        }
        preencherNomes(notas);
        return ResponseEntity.ok(notas);
    }

    @ExigirPermissao(modulo = Modulo.NF_SAIDA, acao = Acao.VER)
    @GetMapping("/{id}")
    public ResponseEntity<NotaFiscalSaida> buscar(@PathVariable Long id) {
        NotaFiscalSaida nota = notaFiscalService.buscar(id);
        preencherNomes(List.of(nota));
        return ResponseEntity.ok(nota);
    }

    @ExigirPermissao(modulo = Modulo.NF_SAIDA, acao = Acao.CRIAR)
    @PostMapping
    public ResponseEntity<NotaFiscalSaida> criar(@RequestBody NotaFiscalSaidaDTO dto) {
        return ResponseEntity.ok(notaFiscalService.criar(dto, ContextoUsuario.atual().getUsuarioId()));
    }

    @ExigirPermissao(modulo = Modulo.NF_SAIDA, acao = Acao.EDITAR)
    @PutMapping("/{id}/status")
    public ResponseEntity<NotaFiscalSaida> status(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(notaFiscalService.atualizarStatus(
                id, body.get("status"), ContextoUsuario.atual().getUsuarioId()));
    }

    @ExigirPermissao(modulo = Modulo.NF_SAIDA, acao = Acao.EDITAR)
    @PutMapping("/{id}/xml")
    public ResponseEntity<NotaFiscalSaida> xml(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(notaFiscalService.uploadXml(
                id, body.get("xml"), ContextoUsuario.atual().getUsuarioId()));
    }

    // Só administradores recebem os nomes (quem lançou / quem efetivou a saída).
    private void preencherNomes(List<NotaFiscalSaida> notas) {
        identificacao.preencher(notas, NotaFiscalSaida::getUsuarioCriacaoId, NotaFiscalSaida::setUsuarioCriacaoNome);
        identificacao.preencher(notas, NotaFiscalSaida::getUsuarioEmissaoId, NotaFiscalSaida::setUsuarioEmissaoNome);
    }
}