package com.acougue.modules.assistente;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Sem @ExigirPermissao próprio: cada ferramenta exige o módulo dos dados que consulta. */
@RestController
@RequestMapping("/assistente")
public class AssistenteController {

    private final AssistenteService assistente;

    public AssistenteController(AssistenteService assistente) {
        this.assistente = assistente;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status() {
        return ResponseEntity.ok(Map.of("habilitado", assistente.habilitado()));
    }

    public record PerguntaDTO(String pergunta, List<AssistenteService.Mensagem> historico) {}

    @PostMapping("/perguntar")
    public ResponseEntity<AssistenteService.Resposta> perguntar(@RequestBody PerguntaDTO dto) {
        return ResponseEntity.ok(assistente.perguntar(dto.pergunta(), dto.historico()));
    }
}
