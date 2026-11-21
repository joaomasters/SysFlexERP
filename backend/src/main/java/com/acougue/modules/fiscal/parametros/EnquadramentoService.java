package com.acougue.modules.fiscal.parametros;

import com.acougue.entity.ConfiguracaoFiscal;
import com.acougue.exception.BusinessException;
import com.acougue.repository.ConfiguracaoFiscalRepository;
import com.acougue.security.ContextoUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Regime tributário por vigência. Uma vigência nunca é editada nem apagada: mudar de regime
 * encerra a vigência aberta na véspera e abre outra. Assim a apuração de um mês passado usa o
 * regime que valia naquele mês, e não o de hoje.
 */
@Service
@RequiredArgsConstructor
public class EnquadramentoService {

    public static final Map<Short, String> REGIMES = Map.of(
            (short) 1, "Simples Nacional",
            (short) 2, "Simples Nacional - excesso de sublimite",
            (short) 3, "Regime normal (Lucro Presumido ou Real)",
            (short) 4, "MEI");

    private final EnquadramentoRepository repo;
    private final ConfiguracaoFiscalRepository configRepo;

    @Transactional(readOnly = true)
    public List<Enquadramento> listar() {
        return repo.findAllByOrderByInicioDesc();
    }

    /** Regime que valia no dia. Sem vigência cadastrada para o dia, recusa: nunca supõe um regime. */
    @Transactional(readOnly = true)
    public short regimeEm(LocalDate dia) {
        return repo.vigenteEm(dia).map(Enquadramento::getRegimeTributario)
                .orElseThrow(() -> new BusinessException("Não há regime tributário cadastrado para " + dia
                        + ". Cadastre a vigência em Fiscal → Emissão de Notas → Configuração."));
    }

    /**
     * Abre uma vigência a partir de {@code inicio}. A vigência aberta termina na véspera. Não dá
     * para começar antes ou no mesmo dia da vigência atual (isso reescreveria o passado).
     */
    @Transactional
    public Enquadramento registrar(short regime, LocalDate inicio, String observacao) {
        if (!REGIMES.containsKey(regime)) throw new BusinessException("Regime tributário inválido: " + regime);
        if (inicio == null) throw new BusinessException("Informe o início da vigência.");
        configRepo.travar(); // serializa mudanças de regime da empresa
        Enquadramento atual = repo.findFirstByOrderByInicioDesc().orElse(null);
        if (atual != null) {
            if (!inicio.isAfter(atual.getInicio())) {
                throw new BusinessException("A nova vigência precisa começar depois de " + atual.getInicio()
                        + ", início da vigência atual. Vigências passadas não são alteradas.");
            }
            if (atual.getRegimeTributario() == regime) {
                throw new BusinessException("A empresa já está em " + REGIMES.get(regime) + " desde " + atual.getInicio() + ".");
            }
            atual.setFim(inicio.minusDays(1));
            repo.save(atual);
        }
        Enquadramento novo = repo.save(Enquadramento.builder().regimeTributario(regime).inicio(inicio)
                .observacao(observacao == null || observacao.isBlank() ? null : observacao.trim())
                .usuarioId(ContextoUsuario.atual().getUsuarioId()).build());
        sincronizarConfiguracao();
        return novo;
    }

    /** Mantém configuracao_fiscal.regime_tributario igual ao regime vigente hoje (é o que a emissão lê). */
    @Transactional
    public void sincronizarConfiguracao() {
        repo.vigenteEm(LocalDate.now()).ifPresent(e -> configRepo.findById(ConfiguracaoFiscal.ID_UNICO).ifPresent(c -> {
            if (!e.getRegimeTributario().equals(c.getRegimeTributario())) {
                c.setRegimeTributario(e.getRegimeTributario());
                configRepo.save(c);
            }
        }));
    }
}
