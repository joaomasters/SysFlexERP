package com.acougue.modules.comissao;

import com.acougue.entity.Modulo;
import com.acougue.entity.Usuario;
import com.acougue.modules.comissao.dto.ComissaoFuncionarioDTO;
import com.acougue.modules.comissao.dto.DefinirPercentualDTO;
import com.acougue.modules.comissao.dto.VendaComissaoDTO;
import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/comissoes")
@RequiredArgsConstructor
public class ComissaoController {

    private final ComissaoService comissaoService;

    @ExigirPermissao(modulo = Modulo.COMISSOES, acao = Acao.VER)
    @GetMapping("/funcionarios")
    public ResponseEntity<List<Usuario>> listarFuncionarios() {
        return ResponseEntity.ok(comissaoService.listarFuncionarios());
    }

    @ExigirPermissao(modulo = Modulo.COMISSOES, acao = Acao.EDITAR)
    @PutMapping("/funcionarios/{id}")
    public ResponseEntity<Usuario> definirPercentual(@PathVariable Long id,
                                                     @RequestBody @Valid DefinirPercentualDTO dto) {
        return ResponseEntity.ok(comissaoService.definirPercentual(id, dto.getPercentual()));
    }

    @ExigirPermissao(modulo = Modulo.COMISSOES, acao = Acao.VER)
    @GetMapping("/apuracao")
    public ResponseEntity<List<ComissaoFuncionarioDTO>> apurar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(comissaoService.apurar(inicio, fim));
    }

    @ExigirPermissao(modulo = Modulo.COMISSOES, acao = Acao.VER)
    @GetMapping("/apuracao/{usuarioId}/vendas")
    public ResponseEntity<List<VendaComissaoDTO>> detalharVendas(
            @PathVariable Long usuarioId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(comissaoService.detalharVendas(usuarioId, inicio, fim));
    }
}
