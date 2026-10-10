package com.acougue.modules.comissao;

import com.acougue.entity.Usuario;
import com.acougue.entity.Venda;
import com.acougue.exception.BusinessException;
import com.acougue.modules.comissao.dto.ComissaoFuncionarioDTO;
import com.acougue.modules.comissao.dto.VendaComissaoDTO;
import com.acougue.repository.UsuarioRepository;
import com.acougue.repository.VendaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Comissão de funcionários: cada usuário tem um percentual sobre as vendas
 * que fecha no PDV. O valor é gravado na própria venda no fechamento
 * (PdvService#registrarComissao) — a apuração aqui só soma esses snapshots.
 */
@Service
@RequiredArgsConstructor
public class ComissaoService {

    private final UsuarioRepository usuarioRepo;
    private final VendaRepository vendaRepo;

    public List<Usuario> listarFuncionarios() {
        return usuarioRepo.findAll().stream()
                .sorted(Comparator.comparing(Usuario::getAtivo).reversed()
                        .thenComparing(Usuario::getNome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public Usuario definirPercentual(Long usuarioId, BigDecimal percentual) {
        Usuario usuario = usuarioRepo.findById(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + usuarioId));
        usuario.setPercentualComissao(percentual);
        return usuarioRepo.save(usuario);
    }

    /**
     * Uma linha por funcionário que vendeu no período ou que tem percentual
     * configurado (mesmo sem vendas, para a tela mostrar quem está "zerado").
     */
    public List<ComissaoFuncionarioDTO> apurar(LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);

        Map<Long, Object[]> resumoPorOperador = vendaRepo
                .resumirComissoesPorOperador(inicioDoDia(inicio), fimDoDia(fim)).stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), Function.identity()));

        List<ComissaoFuncionarioDTO> linhas = new ArrayList<>();
        for (Usuario u : usuarioRepo.findAll()) {
            Object[] resumo = resumoPorOperador.get(u.getId());
            boolean comissionado = u.getPercentualComissao().compareTo(BigDecimal.ZERO) > 0;
            if (resumo == null && !comissionado) continue;

            linhas.add(ComissaoFuncionarioDTO.builder()
                    .usuarioId(u.getId())
                    .nome(u.getNome())
                    .login(u.getLogin())
                    .ativo(Boolean.TRUE.equals(u.getAtivo()))
                    .percentualAtual(u.getPercentualComissao())
                    .quantidadeVendas(resumo == null ? 0 : ((Number) resumo[1]).longValue())
                    .totalVendido(resumo == null ? BigDecimal.ZERO : paraBigDecimal(resumo[2]))
                    .totalComissao(resumo == null ? BigDecimal.ZERO : paraBigDecimal(resumo[3]))
                    .build());
        }
        linhas.sort(Comparator.comparing(ComissaoFuncionarioDTO::getTotalComissao).reversed()
                .thenComparing(ComissaoFuncionarioDTO::getNome, String.CASE_INSENSITIVE_ORDER));
        return linhas;
    }

    public List<VendaComissaoDTO> detalharVendas(Long usuarioId, LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);
        return vendaRepo.findFechadasDoOperador(usuarioId, inicioDoDia(inicio), fimDoDia(fim)).stream()
                .map(this::paraDTO)
                .toList();
    }

    private VendaComissaoDTO paraDTO(Venda v) {
        return VendaComissaoDTO.builder()
                .vendaId(v.getId())
                .numeroCupom(v.getNumeroCupom())
                .dataVenda(v.getDataVenda())
                .total(v.getTotal())
                .percentualComissao(v.getPercentualComissao())
                .valorComissao(v.getValorComissao() != null ? v.getValorComissao() : BigDecimal.ZERO)
                .build();
    }

    private void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            throw new BusinessException("Informe o período (início e fim).");
        }
        if (inicio.isAfter(fim)) {
            throw new BusinessException("A data inicial não pode ser depois da data final.");
        }
    }

    // COALESCE(SUM(...), 0) pode voltar como BigDecimal ou Integer conforme o dialeto
    private static BigDecimal paraBigDecimal(Object valor) {
        return valor instanceof BigDecimal bd ? bd : new BigDecimal(valor.toString());
    }

    private static LocalDateTime inicioDoDia(LocalDate d) { return d.atStartOfDay(); }

    private static LocalDateTime fimDoDia(LocalDate d) { return d.atTime(LocalTime.MAX); }
}
