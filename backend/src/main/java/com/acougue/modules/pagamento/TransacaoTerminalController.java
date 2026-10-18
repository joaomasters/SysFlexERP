package com.acougue.modules.pagamento;

import com.acougue.entity.Modulo;
import com.acougue.entity.TerminalPagamento;
import com.acougue.modules.pagamento.dto.IniciarTransacaoDTO;
import com.acougue.modules.pagamento.dto.ResultadoLocalDTO;
import com.acougue.modules.pagamento.dto.TransacaoTerminalDTO;
import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Uso das maquininhas pelo PDV — protegido pela permissão do próprio PDV. */
@RestController
@RequestMapping("/pagamento")
@RequiredArgsConstructor
public class TransacaoTerminalController {

    private final TransacaoTerminalService transacaoService;
    private final TerminalPagamentoService terminalService;

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.VER)
    @GetMapping("/terminais/disponiveis")
    public ResponseEntity<List<TerminalPagamento>> terminaisDisponiveis() {
        return ResponseEntity.ok(terminalService.listarAtivos());
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR)
    @PostMapping("/transacoes")
    public ResponseEntity<TransacaoTerminalDTO> iniciar(@RequestBody @Valid IniciarTransacaoDTO dto) {
        return ResponseEntity.ok(transacaoService.iniciar(dto));
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.VER)
    @GetMapping("/transacoes/{id}")
    public ResponseEntity<TransacaoTerminalDTO> consultar(@PathVariable Long id) {
        return ResponseEntity.ok(transacaoService.consultar(id));
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR)
    @PostMapping("/transacoes/{id}/resultado")
    public ResponseEntity<TransacaoTerminalDTO> registrarResultado(@PathVariable Long id,
                                                                   @RequestBody @Valid ResultadoLocalDTO dto) {
        return ResponseEntity.ok(transacaoService.registrarResultadoLocal(id, dto));
    }

    @ExigirPermissao(modulo = Modulo.PDV, acao = Acao.CRIAR)
    @PostMapping("/transacoes/{id}/cancelar")
    public ResponseEntity<TransacaoTerminalDTO> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(transacaoService.cancelar(id));
    }
}
