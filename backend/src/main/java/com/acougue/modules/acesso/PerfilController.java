package com.acougue.modules.acesso;

import com.acougue.entity.Modulo;
import com.acougue.entity.Perfil;
import com.acougue.modules.acesso.dto.AtualizarPermissoesDTO;
import com.acougue.modules.acesso.dto.CriarPerfilDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/perfis")
@RequiredArgsConstructor
public class PerfilController {

    private final PerfilService perfilService;

    @GetMapping("/modulos")
    // Aberto a quem está logado: só os nomes fixos dos módulos (filtro da Auditoria). Perfis e permissões exigem acesso.
    public ResponseEntity<List<Map<String, String>>> listarModulos() {
        List<Map<String, String>> modulos = Arrays.stream(Modulo.values())
                .map(m -> Map.of("nome", m.name(), "rotulo", m.getRotulo()))
                .toList();
        return ResponseEntity.ok(modulos);
    }

    @GetMapping
    public ResponseEntity<List<Perfil>> listar() {
        return ResponseEntity.ok(perfilService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Perfil> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(perfilService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<Perfil> criar(@RequestBody @Valid CriarPerfilDTO dto) {
        return ResponseEntity.ok(perfilService.criar(dto));
    }

    @PutMapping("/{id}/permissoes")
    public ResponseEntity<Perfil> atualizarPermissoes(
            @PathVariable Long id, @RequestBody AtualizarPermissoesDTO dto) {
        return ResponseEntity.ok(perfilService.atualizarPermissoes(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        perfilService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}