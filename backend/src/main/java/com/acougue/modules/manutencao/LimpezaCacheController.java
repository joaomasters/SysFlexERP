package com.acougue.modules.manutencao;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Sem @ExigirPermissao: a trava é ser SUPER_ADMIN, conferida no LimpezaCacheService. */
@RestController
@RequestMapping("/manutencao/cache")
@RequiredArgsConstructor
public class LimpezaCacheController {

    private final LimpezaCacheService service;

    @GetMapping
    public ResponseEntity<LimpezaCacheService.StatusCache> status() {
        return ResponseEntity.ok(service.status());
    }

    @PostMapping("/limpar")
    public ResponseEntity<LimpezaCacheService.StatusCache> limpar() {
        return ResponseEntity.ok(service.limpar());
    }
}
