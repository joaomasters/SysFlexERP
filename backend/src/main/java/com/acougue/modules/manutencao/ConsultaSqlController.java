package com.acougue.modules.manutencao;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Sem @ExigirPermissao: a trava é ser SUPER_ADMIN, conferida no ConsultaSqlService. */
@RestController
@RequestMapping("/manutencao/consulta-sql")
@RequiredArgsConstructor
public class ConsultaSqlController {

    private final ConsultaSqlService service;

    public record Pedido(String sql) {}

    @PostMapping
    public ResponseEntity<ConsultaSqlService.Resultado> consultar(@RequestBody Pedido pedido) {
        return ResponseEntity.ok(service.consultar(pedido.sql()));
    }
}
