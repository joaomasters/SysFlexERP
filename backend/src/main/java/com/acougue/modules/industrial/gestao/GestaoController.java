package com.acougue.modules.industrial.gestao;

import com.acougue.entity.Modulo;
import com.acougue.modules.industrial.gestao.GestaoDto.*;
import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Custos por OP, MRP e indicadores (módulo IND_CUSTOS). Só consultas: nada é gravado aqui.
 * Criar uma OP a partir de uma sugestão do MRP usa a rota normal de OPs (permissão do PCP).
 */
@RestController
@RequestMapping("/industrial/gestao")
@RequiredArgsConstructor
public class GestaoController {

    private final CustoService custos;
    private final MrpService mrp;
    private final IndicadoresIndustriaisService indicadores;

    @ExigirPermissao(modulo = Modulo.IND_CUSTOS, acao = Acao.VER)
    @GetMapping("/custos")
    public List<CustoResumo> custos(@RequestParam(required = false) String status) { return custos.listar(status); }

    @ExigirPermissao(modulo = Modulo.IND_CUSTOS, acao = Acao.VER)
    @GetMapping("/custos/{ordemId}")
    public CustoOP custo(@PathVariable Long ordemId) { return custos.custo(ordemId); }

    @ExigirPermissao(modulo = Modulo.IND_CUSTOS, acao = Acao.VER)
    @GetMapping("/mrp")
    public ResultadoMrp mrp(@RequestParam(defaultValue = "false") boolean incluirEstoqueMinimo) { return mrp.calcular(incluirEstoqueMinimo); }

    @ExigirPermissao(modulo = Modulo.IND_CUSTOS, acao = Acao.VER)
    @GetMapping("/indicadores")
    public Painel indicadores(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return indicadores.painel(de, ate);
    }
}
