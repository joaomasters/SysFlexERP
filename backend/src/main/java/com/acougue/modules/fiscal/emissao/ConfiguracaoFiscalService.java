package com.acougue.modules.fiscal.emissao;

import com.acougue.entity.ConfiguracaoFiscal;
import com.acougue.exception.BusinessException;
import com.acougue.modules.fiscal.emissao.dto.CertificadoDTO;
import com.acougue.modules.fiscal.emissao.dto.ConfiguracaoFiscalDTO;
import com.acougue.modules.fiscal.emissao.dto.SalvarConfiguracaoFiscalDTO;
import com.acougue.modules.pagamento.CredencialCripto;
import com.acougue.repository.ConfiguracaoFiscalRepository;
import com.acougue.repository.ProdutoRepository;
import com.acougue.security.ContextoUsuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;

/** Configuração de NFC-e / NF-e da empresa logada (só Paraná, por enquanto). */
@Service
public class ConfiguracaoFiscalService {

    private final ConfiguracaoFiscalRepository repo;
    private final ProdutoRepository produtoRepo;
    private final CredencialCripto cripto;
    private final List<ProvedorFiscal> provedores;

    public ConfiguracaoFiscalService(ConfiguracaoFiscalRepository repo, ProdutoRepository produtoRepo,
                                     CredencialCripto cripto, List<ProvedorFiscal> provedores) {
        this.repo = repo;
        this.produtoRepo = produtoRepo;
        this.cripto = cripto;
        this.provedores = provedores;
    }

    @Transactional
    public ConfiguracaoFiscalDTO buscar() {
        return paraDto(carregar());
    }

    @Transactional
    public ConfiguracaoFiscalDTO salvar(SalvarConfiguracaoFiscalDTO dto) {
        ConfiguracaoFiscal c = carregar();
        if (!"NENHUM".equals(dto.getProvedor()) && provedores.stream().noneMatch(p -> p.codigo().equals(dto.getProvedor()))) {
            throw new BusinessException("Provedor fiscal desconhecido: " + dto.getProvedor());
        }
        if ("PRODUCAO".equals(dto.getAmbiente())
                && provedores.stream().anyMatch(p -> p.codigo().equals(dto.getProvedor()) && p.simulado())) {
            throw new BusinessException("O provedor simulado é só para testes: em produção, escolha um provedor de verdade.");
        }
        c.setAmbiente(dto.getAmbiente());
        c.setProvedor(dto.getProvedor());
        c.setRazaoSocial(texto(dto.getRazaoSocial()));
        c.setNomeFantasia(texto(dto.getNomeFantasia()));
        c.setCnpj(DocumentosFiscais.limpar(dto.getCnpj()));
        c.setInscricaoEstadual(DocumentosFiscais.limpar(dto.getInscricaoEstadual()));
        c.setRegimeTributario(dto.getRegimeTributario());
        c.setLogradouro(texto(dto.getLogradouro()));
        c.setNumeroEndereco(texto(dto.getNumeroEndereco()));
        c.setComplemento(texto(dto.getComplemento()));
        c.setBairro(texto(dto.getBairro()));
        c.setCodigoMunicipio(digitos(dto.getCodigoMunicipio()));
        c.setMunicipio(texto(dto.getMunicipio()));
        c.setCep(digitos(dto.getCep()));
        c.setTelefone(digitos(dto.getTelefone()));
        c.setNfceSerie(dto.getNfceSerie());
        c.setNfceProximoNumero(dto.getNfceProximoNumero());
        c.setNfeSerie(dto.getNfeSerie());
        c.setNfeProximoNumero(dto.getNfeProximoNumero());
        if (dto.getNfceModo() != null) c.setNfceModo(dto.getNfceModo());
        c.setCscId(texto(dto.getCscId()));
        if (dto.isLimparCsc()) {
            c.setCscId(null);
            c.setCscTokenCripto(null);
        } else if (dto.getCscToken() != null && !dto.getCscToken().isBlank()) {
            exigirCripto();
            c.setCscTokenCripto(cripto.cifrar(dto.getCscToken().trim()));
        }

        // Ligar a emissão só com tudo em ordem; desligar é sempre permitido (volta ao cupom não fiscal)
        PendenciasFiscais pendencias = pendencias(c);
        if (dto.isEmitirNfce() && !pendencias.nfce().isEmpty()) {
            throw new BusinessException("Para emitir NFC-e, resolva antes: " + String.join(" ", pendencias.nfce()));
        }
        if (dto.isEmitirNfe() && !pendencias.nfe().isEmpty()) {
            throw new BusinessException("Para emitir NF-e, resolva antes: " + String.join(" ", pendencias.nfe()));
        }
        c.setEmitirNfce(dto.isEmitirNfce());
        c.setEmitirNfe(dto.isEmitirNfe());
        c.setUpdatedBy(ContextoUsuario.atual().getUsuarioId());
        return paraDto(repo.save(c));
    }

    /** Guarda o A1 cifrado depois de conferir que abre com a senha; devolve titular e validade. */
    @Transactional
    public ConfiguracaoFiscalDTO salvarCertificado(CertificadoDTO dto) {
        exigirCripto();
        byte[] pfx;
        try {
            pfx = Base64.getDecoder().decode(dto.getArquivoBase64().replaceFirst("^data:[^,]*,", ""));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Arquivo do certificado ilegível.");
        }
        CertificadoDigital cert = CertificadoDigital.ler(pfx, dto.getSenha());
        if (cert.vencido()) {
            throw new BusinessException("Este certificado venceu em " + cert.validade().toLocalDate() + ". Envie o renovado.");
        }
        ConfiguracaoFiscal c = carregar();
        c.setCertificadoCripto(cripto.cifrar(Base64.getEncoder().encodeToString(pfx)));
        c.setCertificadoSenhaCripto(cripto.cifrar(dto.getSenha()));
        c.setCertificadoTitular(cert.titular());
        c.setCertificadoCnpj(cert.cnpj());
        c.setCertificadoValidade(cert.validade());
        c.setUpdatedBy(ContextoUsuario.atual().getUsuarioId());
        return paraDto(repo.save(c));
    }

    @Transactional
    public ConfiguracaoFiscalDTO removerCertificado() {
        ConfiguracaoFiscal c = carregar();
        c.setCertificadoCripto(null);
        c.setCertificadoSenhaCripto(null);
        c.setCertificadoTitular(null);
        c.setCertificadoCnpj(null);
        c.setCertificadoValidade(null);
        // Sem certificado não há como emitir: volta ao cupom não fiscal
        c.setEmitirNfce(false);
        c.setEmitirNfe(false);
        c.setUpdatedBy(ContextoUsuario.atual().getUsuarioId());
        return paraDto(repo.save(c));
    }

    // ── internos ───────────────────────────────────────────────

    private ConfiguracaoFiscal carregar() {
        return repo.findById(ConfiguracaoFiscal.ID_UNICO).orElseGet(() -> repo.save(
                ConfiguracaoFiscal.builder().id(ConfiguracaoFiscal.ID_UNICO).build()));
    }

    private PendenciasFiscais pendencias(ConfiguracaoFiscal c) {
        boolean provedorDisponivel = provedores.stream().anyMatch(p -> p.codigo().equals(c.getProvedor()));
        return PendenciasFiscais.calcular(c, cripto.configurada(), provedorDisponivel);
    }

    private ConfiguracaoFiscalDTO paraDto(ConfiguracaoFiscal c) {
        PendenciasFiscais p = pendencias(c);
        return new ConfiguracaoFiscalDTO(
                c.getEmitirNfce(), c.getEmitirNfe(), c.getAmbiente(), c.getUf(), c.getProvedor(),
                c.getRazaoSocial(), c.getNomeFantasia(), c.getCnpj(), c.getInscricaoEstadual(), c.getRegimeTributario(),
                c.getLogradouro(), c.getNumeroEndereco(), c.getComplemento(), c.getBairro(),
                c.getCodigoMunicipio(), c.getMunicipio(), c.getCep(), c.getTelefone(),
                c.getNfceSerie(), c.getNfceProximoNumero(), c.getNfeSerie(), c.getNfeProximoNumero(), c.getNfceModo(),
                c.getCscId(), c.getCscTokenCripto() != null,
                c.getCertificadoCripto() != null, c.getCertificadoTitular(), c.getCertificadoCnpj(),
                c.getCertificadoValidade(),
                cripto.configurada(),
                provedores.stream().map(x -> new ConfiguracaoFiscalDTO.Provedor(x.codigo(), x.rotulo())).toList(),
                p.nfce(), p.nfe(),
                produtoRepo.contarAtivosSemNcm());
    }

    private void exigirCripto() {
        if (!cripto.configurada()) {
            throw new BusinessException("O servidor está sem PAGAMENTO_CRIPTO_CHAVE: não é possível guardar segredos.");
        }
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String digitos(String s) {
        if (s == null) return null;
        String d = s.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }
}
