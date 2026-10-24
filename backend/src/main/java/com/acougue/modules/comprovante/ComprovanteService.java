package com.acougue.modules.comprovante;

import com.acougue.entity.ConfiguracaoComprovante;
import com.acougue.exception.BusinessException;
import com.acougue.modules.comprovante.dto.ConfiguracaoComprovanteDTO;
import com.acougue.repository.ConfiguracaoComprovanteRepository;
import com.acougue.security.ContextoUsuario;
import com.acougue.tenant.Tenant;
import com.acougue.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Configuração do cupom não fiscal da empresa logada. */
@Service
@RequiredArgsConstructor
public class ComprovanteService {

    private final ConfiguracaoComprovanteRepository repo;
    private final TenantRepository tenantRepository;

    /**
     * A migration V26 já cria a linha; se faltar (empresa criada de outro jeito),
     * nasce aqui com o nome da empresa e o padrão de 80 mm.
     */
    @Transactional
    public ConfiguracaoComprovante buscar() {
        return repo.findById(ConfiguracaoComprovante.ID_UNICO).orElseGet(() -> repo.save(
                ConfiguracaoComprovante.builder()
                        .id(ConfiguracaoComprovante.ID_UNICO)
                        .nomeFantasia(nomeDaEmpresaLogada())
                        .build()));
    }

    @Transactional
    public ConfiguracaoComprovante salvar(ConfiguracaoComprovanteDTO dto) {
        if (dto.getLarguraPapelMm() != 58 && dto.getLarguraPapelMm() != 80) {
            throw new BusinessException("Largura do papel deve ser 58 ou 80 mm.");
        }
        ConfiguracaoComprovante c = buscar();
        c.setNomeFantasia(dto.getNomeFantasia().trim());
        c.setCnpj(vazioParaNull(dto.getCnpj()));
        c.setEndereco(vazioParaNull(dto.getEndereco()));
        c.setBairro(vazioParaNull(dto.getBairro()));
        c.setCidade(vazioParaNull(dto.getCidade()));
        c.setTelefone(vazioParaNull(dto.getTelefone()));
        c.setMensagemRodape(vazioParaNull(dto.getMensagemRodape()));
        c.setLarguraPapelMm(dto.getLarguraPapelMm());
        c.setVias(dto.getVias());
        c.setImprimirAutomatico(dto.isImprimirAutomatico());
        c.setMostrarAvisoNaoFiscal(dto.isMostrarAvisoNaoFiscal());
        c.setMostrarOperador(dto.isMostrarOperador());
        c.setMostrarCliente(dto.isMostrarCliente());
        c.setUpdatedBy(ContextoUsuario.atual().getUsuarioId());
        return repo.save(c);
    }

    private String nomeDaEmpresaLogada() {
        String schema = ContextoUsuario.atual().getTenantSchema();
        return tenantRepository.listarTodos().stream()
                .filter(t -> t.schemaName().equals(schema))
                .map(Tenant::nome)
                .findFirst()
                .orElse("Minha Loja");
    }

    private static String vazioParaNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
