package com.acougue.modules.comprovante;

import com.acougue.entity.ConfiguracaoComprovante;
import com.acougue.entity.Modulo;
import com.acougue.modules.comprovante.dto.ConfiguracaoComprovanteDTO;
import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/comprovante/configuracao")
@RequiredArgsConstructor
public class ComprovanteController {

    private final ComprovanteService service;

    /**
     * Sem @ExigirPermissao de propósito: o PDV de qualquer caixa precisa do
     * cabeçalho para imprimir, e são só dados públicos da loja (nome, endereço,
     * telefone). Continua exigindo usuário logado (SecurityConfig).
     */
    @GetMapping
    public ResponseEntity<ConfiguracaoComprovante> buscar() {
        return ResponseEntity.ok(service.buscar());
    }

    @ExigirPermissao(modulo = Modulo.COMPROVANTE, acao = Acao.EDITAR)
    @PutMapping
    public ResponseEntity<ConfiguracaoComprovante> salvar(@RequestBody @Valid ConfiguracaoComprovanteDTO dto) {
        return ResponseEntity.ok(service.salvar(dto));
    }
}
