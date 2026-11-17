package com.acougue.modules.fiscal.parametros;

import com.acougue.entity.Modulo;
import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Regime tributário por vigência: histórico e abertura de nova vigência. */
@RestController
@RequestMapping("/fiscal/enquadramentos")
@RequiredArgsConstructor
public class EnquadramentoController {

    private final EnquadramentoService service;

    public record NovaVigencia(short regimeTributario, LocalDate inicio, String observacao) {}

    @ExigirPermissao(modulo = Modulo.FISCAL, acao = Acao.VER)
    @GetMapping
    public ResponseEntity<List<Enquadramento>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @ExigirPermissao(modulo = Modulo.FISCAL, acao = Acao.EDITAR)
    @PostMapping
    public ResponseEntity<Enquadramento> registrar(@RequestBody NovaVigencia v) {
        return ResponseEntity.ok(service.registrar(v.regimeTributario(), v.inicio(), v.observacao()));
    }
}
