package com.acougue.modules.fiscal.emissao;

import com.acougue.entity.ConfiguracaoFiscal;
import com.acougue.entity.DocumentoFiscal;
import com.acougue.exception.BusinessException;
import com.acougue.modules.fiscal.emissao.dto.CertificadoDTO;
import com.acougue.modules.fiscal.emissao.dto.ConfiguracaoFiscalDTO;
import com.acougue.modules.fiscal.emissao.dto.SalvarConfiguracaoFiscalDTO;
import com.acougue.modules.pagamento.CredencialCripto;
import com.acougue.modules.fiscal.parametros.EnquadramentoService;
import com.acougue.repository.ConfiguracaoFiscalRepository;
import com.acougue.repository.DocumentoFiscalRepository;
import com.acougue.repository.ProdutoRepository;
import com.acougue.security.ContextoUsuario;
import com.acougue.security.auditoria.AuditoriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Configuração de NFC-e / NF-e da empresa logada (só Paraná, por enquanto). */
@Service
public class ConfiguracaoFiscalService {

    private final ConfiguracaoFiscalRepository repo;
    private final ProdutoRepository produtoRepo;
    private final CredencialCripto cripto;
    private final List<ProvedorFiscal> provedores;
    private final EnquadramentoService enquadramento;
    private final DocumentoFiscalRepository documentoRepo;
    private final AuditoriaService auditoria;

    public ConfiguracaoFiscalService(ConfiguracaoFiscalRepository repo, ProdutoRepository produtoRepo,
                                     CredencialCripto cripto, List<ProvedorFiscal> provedores,
                                     EnquadramentoService enquadramento, DocumentoFiscalRepository documentoRepo,
                                     AuditoriaService auditoria) {
        this.repo = repo;
        this.produtoRepo = produtoRepo;
        this.cripto = cripto;
        this.provedores = provedores;
        this.enquadramento = enquadramento;
        this.documentoRepo = documentoRepo;
        this.auditoria = auditoria;
    }

    @Transactional
    public ConfiguracaoFiscalDTO buscar() {
        enquadramento.sincronizarConfiguracao(); // vigência futura que começou hoje passa a valer na tela
        return paraDto(carregar());
    }

    @Transactional
    public ConfiguracaoFiscalDTO salvar(SalvarConfiguracaoFiscalDTO dto) {
        enquadramento.sincronizarConfiguracao();
        ConfiguracaoFiscal c = carregar();
        Estado antes = Estado.de(c);
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
        c.setLogradouro(texto(dto.getLogradouro()));
        c.setNumeroEndereco(texto(dto.getNumeroEndereco()));
        c.setComplemento(texto(dto.getComplemento()));
        c.setBairro(texto(dto.getBairro()));
        c.setCodigoMunicipio(digitos(dto.getCodigoMunicipio()));
        c.setMunicipio(texto(dto.getMunicipio()));
        c.setCep(digitos(dto.getCep()));
        c.setTelefone(digitos(dto.getTelefone()));
        // Numeração nunca volta: o próximo número tem de ser maior que o último já usado na série
        exigirNumeroLivre(DocumentoFiscal.MODELO_NFCE, dto.getNfceSerie(), dto.getAmbiente(), dto.getNfceProximoNumero(), "NFC-e");
        exigirNumeroLivre(DocumentoFiscal.MODELO_NFE, dto.getNfeSerie(), dto.getAmbiente(), dto.getNfeProximoNumero(), "NF-e");
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

        c.setPisCofinsCst(texto(dto.getPisCofinsCst()));
        c.setPercentualTributos(dto.getPercentualTributos());
        if (dto.isLimparTokens()) {
            c.setProvedorTokenHomologacaoCripto(null);
            c.setProvedorTokenProducaoCripto(null);
        } else {
            if (dto.getTokenHomologacao() != null && !dto.getTokenHomologacao().isBlank()) {
                exigirCripto();
                c.setProvedorTokenHomologacaoCripto(cripto.cifrar(dto.getTokenHomologacao().trim()));
            }
            if (dto.getTokenProducao() != null && !dto.getTokenProducao().isBlank()) {
                exigirCripto();
                c.setProvedorTokenProducaoCripto(cripto.cifrar(dto.getTokenProducao().trim()));
            }
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
        ConfiguracaoFiscal salva = repo.save(c);
        auditar(antes, Estado.de(salva), null);
        // Trocar o regime na tela abre uma nova vigência a partir de hoje (a anterior é preservada)
        if (dto.getRegimeTributario() != salva.getRegimeTributario()) {
            enquadramento.registrar(dto.getRegimeTributario(), LocalDate.now(), "Alterado na configuração fiscal");
        }
        return paraDto(salva);
    }

    private void exigirNumeroLivre(short modelo, short serie, String ambiente, int proximo, String nome) {
        Integer ultimo = documentoRepo.maiorNumero(modelo, serie, ambiente);
        if (ultimo != null && proximo <= ultimo) {
            throw new BusinessException("O próximo número da " + nome + " (série " + serie + ") precisa ser maior que "
                    + ultimo + ", o último já usado. Numeração fiscal nunca volta.");
        }
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
        Estado antes = Estado.de(c);
        c.setCertificadoCripto(null);
        c.setCertificadoSenhaCripto(null);
        c.setCertificadoTitular(null);
        c.setCertificadoCnpj(null);
        c.setCertificadoValidade(null);
        // Sem certificado não há como emitir: volta ao cupom não fiscal
        c.setEmitirNfce(false);
        c.setEmitirNfe(false);
        c.setUpdatedBy(ContextoUsuario.atual().getUsuarioId());
        ConfiguracaoFiscal salva = repo.save(c);
        auditar(antes, Estado.de(salva), "certificado digital removido");
        return paraDto(salva);
    }

    // ── auditoria ──────────────────────────────────────────────

    /** O que interessa auditar na configuração: ligar/desligar a emissão, ambiente, provedor, numeração e modo. */
    record Estado(boolean emitirNfce, boolean emitirNfe, String ambiente, String provedor, String nfceModo,
                  Short nfceSerie, Integer nfceProximoNumero, Short nfeSerie, Integer nfeProximoNumero) {
        static Estado de(ConfiguracaoFiscal c) {
            return new Estado(Boolean.TRUE.equals(c.getEmitirNfce()), Boolean.TRUE.equals(c.getEmitirNfe()), c.getAmbiente(),
                    c.getProvedor(), c.getNfceModo(), c.getNfceSerie(), c.getNfceProximoNumero(), c.getNfeSerie(),
                    c.getNfeProximoNumero());
        }
    }

    /** Uma linha por emissão ligada/desligada e uma com as demais trocas (quem e quando ficam no próprio log). */
    private void auditar(Estado antes, Estado depois, String motivo) {
        String sufixo = motivo == null ? "" : " (" + motivo + ")";
        if (antes.emitirNfce() != depois.emitirNfce()) {
            auditoria.registrar("FISCAL", depois.emitirNfce() ? "NFCE_LIGADA" : "NFCE_DESLIGADA",
                    "Emissão de NFC-e " + (depois.emitirNfce() ? "ligada" : "desligada") + sufixo
                            + " — ambiente " + depois.ambiente() + ", série " + depois.nfceSerie()
                            + ", modo " + rotuloModo(depois.nfceModo()));
        }
        if (antes.emitirNfe() != depois.emitirNfe()) {
            auditoria.registrar("FISCAL", depois.emitirNfe() ? "NFE_LIGADA" : "NFE_DESLIGADA",
                    "Emissão de NF-e " + (depois.emitirNfe() ? "ligada" : "desligada") + sufixo);
        }
        List<String> trocas = trocas(antes, depois);
        if (!trocas.isEmpty()) {
            auditoria.registrar("FISCAL", "CONFIG_FISCAL", "Configuração fiscal: " + String.join("; ", trocas) + sufixo);
        }
    }

    static List<String> trocas(Estado a, Estado d) {
        List<String> t = new ArrayList<>();
        if (!Objects.equals(a.ambiente(), d.ambiente())) t.add("ambiente " + a.ambiente() + " → " + d.ambiente());
        if (!Objects.equals(a.provedor(), d.provedor())) t.add("provedor " + a.provedor() + " → " + d.provedor());
        if (!Objects.equals(a.nfceModo(), d.nfceModo())) {
            t.add("quando emitir NFC-e: " + rotuloModo(a.nfceModo()) + " → " + rotuloModo(d.nfceModo()));
        }
        if (!Objects.equals(a.nfceSerie(), d.nfceSerie())) t.add("série NFC-e " + a.nfceSerie() + " → " + d.nfceSerie());
        if (!Objects.equals(a.nfceProximoNumero(), d.nfceProximoNumero())) {
            t.add("próximo número NFC-e " + a.nfceProximoNumero() + " → " + d.nfceProximoNumero());
        }
        if (!Objects.equals(a.nfeSerie(), d.nfeSerie())) t.add("série NF-e " + a.nfeSerie() + " → " + d.nfeSerie());
        if (!Objects.equals(a.nfeProximoNumero(), d.nfeProximoNumero())) {
            t.add("próximo número NF-e " + a.nfeProximoNumero() + " → " + d.nfeProximoNumero());
        }
        return t;
    }

    private static String rotuloModo(String modo) {
        if ("TODAS".equals(modo)) return "em todas as vendas";
        if ("SOB_DEMANDA".equals(modo)) return "só quando o cliente pedir";
        return String.valueOf(modo);
    }

    // ── internos ───────────────────────────────────────────────

    private ConfiguracaoFiscal carregar() {
        return repo.findById(ConfiguracaoFiscal.ID_UNICO).orElseGet(() -> repo.save(
                ConfiguracaoFiscal.builder().id(ConfiguracaoFiscal.ID_UNICO).build()));
    }

    private PendenciasFiscais pendencias(ConfiguracaoFiscal c) {
        boolean provedorDisponivel = provedores.stream().anyMatch(p -> p.codigo().equals(c.getProvedor()));
        ProvedorFiscal provedor = provedores.stream().filter(p -> p.codigo().equals(c.getProvedor())).findFirst().orElse(null);
        return PendenciasFiscais.calcular(c, cripto.configurada(), provedorDisponivel, provedor);
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
                c.getProvedorTokenHomologacaoCripto() != null, c.getProvedorTokenProducaoCripto() != null, c.getPisCofinsCst(),
                c.getCertificadoCripto() != null, c.getCertificadoTitular(), c.getCertificadoCnpj(),
                c.getCertificadoValidade(),
                cripto.configurada(),
                provedores.stream().map(x -> new ConfiguracaoFiscalDTO.Provedor(x.codigo(), x.rotulo())).toList(),
                p.nfce(), p.nfe(),
                produtoRepo.contarAtivosSemNcm(), c.getPercentualTributos());
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
