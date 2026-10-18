package com.acougue.modules.pagamento;

import com.acougue.entity.PagamentoProvedorConfig;
import com.acougue.entity.ProvedorPagamento;
import com.acougue.exception.BusinessException;
import com.acougue.modules.pagamento.dto.ProvedorConfigDTO;
import com.acougue.modules.pagamento.dto.SalvarProvedorConfigDTO;
import com.acougue.repository.PagamentoProvedorConfigRepository;
import com.acougue.security.ContextoUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Credenciais das adquirentes da empresa atual (cada tenant tem as suas). */
@Service
@RequiredArgsConstructor
public class CredenciaisPagamentoService {

    private final PagamentoProvedorConfigRepository configRepo;
    private final CredencialCripto cripto;

    public List<ProvedorConfigDTO> listar() {
        return Arrays.stream(ProvedorPagamento.values())
                .map(p -> paraDTO(buscarOuCriar(p)))
                .toList();
    }

    @Transactional
    public ProvedorConfigDTO salvar(ProvedorPagamento provedor, SalvarProvedorConfigDTO dto) {
        PagamentoProvedorConfig config = buscarOuCriar(provedor);

        config.setAtivo(dto.getAtivo());
        config.setAmbiente(dto.getAmbiente());
        config.setCodigoEstabelecimento(vazioParaNull(dto.getCodigoEstabelecimento()));
        config.setObservacoes(vazioParaNull(dto.getObservacoes()));

        if (dto.isLimparAccessToken()) {
            config.setAccessTokenCripto(null);
        } else if (temTexto(dto.getAccessToken())) {
            config.setAccessTokenCripto(cripto.cifrar(dto.getAccessToken().trim()));
        }
        if (dto.isLimparWebhookSecret()) {
            config.setWebhookSecretCripto(null);
        } else if (temTexto(dto.getWebhookSecret())) {
            config.setWebhookSecretCripto(cripto.cifrar(dto.getWebhookSecret().trim()));
        }

        // Ativo sem credencial é permitido (terminais AGENTE_LOCAL/MANUAL não precisam);
        // a cobrança em nuvem é que barra depois, em credencialObrigatoria().
        config.setUpdatedBy(ContextoUsuario.atual().getUsuarioId());
        return paraDTO(configRepo.save(config));
    }

    /** Credencial em claro para chamar a API da adquirente. Uso interno do backend — nunca devolver na API. */
    public String credencialObrigatoria(ProvedorPagamento provedor) {
        PagamentoProvedorConfig config = buscarOuCriar(provedor);
        if (!config.getAtivo()) {
            throw new BusinessException(provedor.getRotulo() + " está desativado em Maquininhas → Credenciais.");
        }
        String token = cripto.decifrar(config.getAccessTokenCripto());
        if (token == null) {
            throw new BusinessException("Credencial da " + provedor.getRotulo()
                    + " não cadastrada. Configure em Maquininhas → Credenciais.");
        }
        return token;
    }

    public boolean provedorAtivo(ProvedorPagamento provedor) {
        return configRepo.findById(provedor).map(PagamentoProvedorConfig::getAtivo).orElse(false);
    }

    /**
     * Token do Mercado Pago para o PIX. Opcional: sem credencial cadastrada na
     * tela, o PixService cai no MP_ACCESS_TOKEN do ambiente (comportamento antigo).
     */
    public Optional<String> tokenMercadoPago() {
        return configRepo.findById(ProvedorPagamento.MERCADO_PAGO)
                .filter(c -> c.getAccessTokenCripto() != null && cripto.configurada())
                .map(c -> cripto.decifrar(c.getAccessTokenCripto()));
    }

    public Optional<String> webhookSecret(ProvedorPagamento provedor) {
        return configRepo.findById(provedor)
                .filter(c -> c.getWebhookSecretCripto() != null && cripto.configurada())
                .map(c -> cripto.decifrar(c.getWebhookSecretCripto()));
    }

    private PagamentoProvedorConfig buscarOuCriar(ProvedorPagamento provedor) {
        // A migration já cria as 3 linhas; isto cobre um provedor novo no enum sem migration.
        return configRepo.findById(provedor)
                .orElseGet(() -> PagamentoProvedorConfig.builder().provedor(provedor).build());
    }

    private ProvedorConfigDTO paraDTO(PagamentoProvedorConfig c) {
        return ProvedorConfigDTO.builder()
                .provedor(c.getProvedor())
                .rotulo(c.getProvedor().getRotulo())
                .ativo(Boolean.TRUE.equals(c.getAtivo()))
                .ambiente(c.getAmbiente())
                .codigoEstabelecimento(c.getCodigoEstabelecimento())
                .observacoes(c.getObservacoes())
                .credencialConfigurada(c.getAccessTokenCripto() != null)
                .webhookSecretConfigurado(c.getWebhookSecretCripto() != null)
                .suportaApiNuvem(c.getProvedor().suportaApiNuvem())
                .criptoConfigurada(cripto.configurada())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private static boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }

    private static String vazioParaNull(String s) {
        return temTexto(s) ? s.trim() : null;
    }
}
