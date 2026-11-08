package com.acougue.modules.fiscal.emissao;

import com.acougue.entity.ConfiguracaoFiscal;
import com.acougue.entity.DocumentoFiscal;
import com.acougue.entity.ItensVenda;
import com.acougue.entity.PagamentoVenda;
import com.acougue.entity.Produto;
import com.acougue.entity.Venda;
import com.acougue.exception.BusinessException;
import com.acougue.modules.fiscal.emissao.dto.NfceDTO;
import com.acougue.modules.pagamento.CredencialCripto;
import com.acougue.repository.ConfiguracaoFiscalRepository;
import com.acougue.repository.DocumentoFiscalRepository;
import com.acougue.repository.ItensVendaRepository;
import com.acougue.repository.PagamentoVendaRepository;
import com.acougue.repository.VendaRepository;
import com.acougue.security.ContextoUsuario;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * NFC-e de uma venda já fechada no PDV ("CPF na nota?"). O cupom não fiscal continua sendo o
 * padrão: aqui só se chega com a emissão ligada na configuração fiscal da empresa.
 */
@Service
public class NfceService {

    private static final Logger log = LoggerFactory.getLogger(NfceService.class);

    /** Forma de pagamento do PDV → código tPag da SEFAZ. */
    static final Map<String, String> TPAG = Map.of(
            "DINHEIRO", "01", "CHEQUE", "02", "CREDITO", "03", "DEBITO", "04",
            "FIADO", "05",       // crédito loja
            "VOUCHER", "10",     // vale-alimentação (açougue/mercearia)
            "PIX", "17",         // pagamento instantâneo dinâmico
            "CONVENIO", "99");

    private final ConfiguracaoFiscalRepository configRepo;
    private final DocumentoFiscalRepository documentoRepo;
    private final VendaRepository vendaRepo;
    private final ItensVendaRepository itensRepo;
    private final PagamentoVendaRepository pagamentoRepo;
    private final CredencialCripto cripto;
    private final List<ProvedorFiscal> provedores;

    public NfceService(ConfiguracaoFiscalRepository configRepo, DocumentoFiscalRepository documentoRepo,
                       VendaRepository vendaRepo, ItensVendaRepository itensRepo,
                       PagamentoVendaRepository pagamentoRepo, CredencialCripto cripto,
                       List<ProvedorFiscal> provedores) {
        this.configRepo = configRepo;
        this.documentoRepo = documentoRepo;
        this.vendaRepo = vendaRepo;
        this.itensRepo = itensRepo;
        this.pagamentoRepo = pagamentoRepo;
        this.cripto = cripto;
        this.provedores = provedores;
    }

    /** O que o PDV precisa saber para perguntar "CPF na nota?". */
    public record Situacao(boolean emitirNfce, String modo, String ambiente) {}

    @Transactional(readOnly = true)
    public Situacao situacao() {
        return configRepo.findById(ConfiguracaoFiscal.ID_UNICO)
                .map(c -> new Situacao(c.getEmitirNfce(), c.getNfceModo(), c.getAmbiente()))
                .orElse(new Situacao(false, "SOB_DEMANDA", "HOMOLOGACAO"));
    }

    @Transactional(readOnly = true)
    public Optional<NfceDTO> daVenda(Long vendaId) {
        ConfiguracaoFiscal c = configRepo.findById(ConfiguracaoFiscal.ID_UNICO).orElse(null);
        return documentoRepo.findFirstByVendaIdAndModeloOrderByIdDesc(vendaId, DocumentoFiscal.MODELO_NFCE)
                .map(d -> paraDto(d, c));
    }

    /**
     * Emite a NFC-e da venda. Uma venda tem no máximo uma NFC-e autorizada: pedir de novo devolve
     * a mesma. Recusa da SEFAZ ou falha de comunicação ficam gravadas (status REJEITADA/ERRO) e a
     * venda continua fechada; dá para tentar de novo.
     */
    @Transactional
    public NfceDTO emitirParaVenda(Long vendaId, String documento) {
        ConfiguracaoFiscal c = configRepo.travar()
                .orElseThrow(() -> new BusinessException("A empresa ainda não tem configuração fiscal."));
        if (!Boolean.TRUE.equals(c.getEmitirNfce())) {
            throw new BusinessException("A emissão de NFC-e está desligada. Ligue em Fiscal → Emissão de Notas.");
        }
        ProvedorFiscal provedor = provedores.stream().filter(p -> p.codigo().equals(c.getProvedor())).findFirst()
                .orElseThrow(() -> new BusinessException("O provedor fiscal \"" + c.getProvedor() + "\" não está disponível."));
        if (provedor.simulado() && "PRODUCAO".equals(c.getAmbiente())) {
            throw new BusinessException("O provedor simulado não emite em produção. Escolha um provedor de verdade.");
        }
        if (c.getRegimeTributario() == 3) {
            throw new BusinessException("A NFC-e do regime normal (CST) ainda não está disponível: só Simples Nacional e MEI.");
        }

        Venda venda = vendaRepo.findById(vendaId)
                .orElseThrow(() -> new EntityNotFoundException("Venda não encontrada: " + vendaId));
        if (!"FECHADA".equals(venda.getStatus())) {
            throw new BusinessException("Só dá para emitir NFC-e de venda finalizada.");
        }
        Optional<DocumentoFiscal> jaAutorizada = documentoRepo.findFirstByVendaIdAndModeloAndStatus(
                vendaId, DocumentoFiscal.MODELO_NFCE, "AUTORIZADA");
        if (jaAutorizada.isPresent()) {
            return paraDto(jaAutorizada.get(), c);
        }

        String destinatario = destinatario(documento);
        List<NfceEmissao.Item> itens = itens(venda);
        List<NfceEmissao.Pagamento> pagamentos = pagamentoRepo.findByVendaId(vendaId).stream()
                .map(p -> new NfceEmissao.Pagamento(TPAG.getOrDefault(p.getFormaPagamento(), "99"), p.getValor()))
                .toList();

        // Número reservado sob a trava da configuração: nunca se repete, mesmo se a SEFAZ recusar
        int numero = c.getNfceProximoNumero();
        c.setNfceProximoNumero(numero + 1);
        configRepo.save(c);

        LocalDateTime agora = LocalDateTime.now();
        DocumentoFiscal doc = documentoRepo.save(DocumentoFiscal.builder()
                .modelo(DocumentoFiscal.MODELO_NFCE).serie(c.getNfceSerie()).numero(numero)
                .ambiente(c.getAmbiente()).status("PENDENTE").valorTotal(venda.getTotal())
                .vendaId(vendaId).destinatarioDocumento(destinatario).provedor(provedor.codigo())
                .usuarioId(ContextoUsuario.atual().getUsuarioId())
                .build());

        NfceEmissao nota = new NfceEmissao(c.getAmbiente(), c.getUf(), emitente(c), c.getNfceSerie(), numero, agora,
                destinatario, itens, pagamentos, venda.getTroco(), venda.getTotal(), vendaId);
        RetornoEmissao r;
        try {
            r = provedor.emitirNfce(nota, credencial(c));
        } catch (RuntimeException e) {
            log.warn("Falha ao emitir a NFC-e {} da venda {}: {}", numero, vendaId, e.getMessage());
            r = RetornoEmissao.erro("Sem resposta do provedor fiscal: " + e.getMessage());
        }

        doc.setStatus(r.status());
        doc.setChave(r.chave());
        doc.setProtocolo(r.protocolo());
        doc.setMensagem(r.mensagem());
        doc.setUrlQrcode(r.urlQrcode());
        doc.setUrlConsulta(r.urlConsulta());
        doc.setXml(r.xml());
        doc.setIdExterno(r.idExterno());
        if (r.autorizada()) {
            doc.setEmitidaEm(r.autorizadaEm() != null ? r.autorizadaEm() : agora);
            venda.setNfceChave(r.chave());
        }
        venda.setNfceStatus(r.status());
        vendaRepo.save(venda);
        return paraDto(documentoRepo.save(doc), c);
    }

    // ── internos ───────────────────────────────────────────────

    /** CPF ou CNPJ válido, só os dígitos; vazio = consumidor não identificado. */
    static String destinatario(String documento) {
        String d = DocumentosFiscais.limpar(documento);
        if (d == null) return null;
        if (d.length() == 11 && DocumentosFiscais.cpfValido(d)) return d;
        if (d.length() == 14 && DocumentosFiscais.cnpjValido(d)) return d;
        throw new BusinessException("CPF ou CNPJ inválido: confira os números.");
    }

    /**
     * Itens com os dados fiscais do cadastro. O desconto da venda (no total) é rateado pelos itens,
     * na proporção do valor, e o último leva a sobra dos centavos: a nota fecha no total da venda.
     */
    private List<NfceEmissao.Item> itens(Venda venda) {
        List<ItensVenda> itensVenda = itensRepo.findByVendaId(venda.getId());
        if (itensVenda.isEmpty()) throw new BusinessException("A venda não tem itens.");

        List<String> semDados = itensVenda.stream().map(ItensVenda::getProduto)
                .filter(p -> p.getNcm() == null || p.getCfop() == null || p.getCsosn() == null)
                .map(Produto::getNome).distinct().toList();
        if (!semDados.isEmpty()) {
            throw new BusinessException("Complete os dados fiscais (NCM, CFOP e CSOSN) no cadastro de: "
                    + String.join(", ", semDados) + ".");
        }

        BigDecimal brutoTotal = itensVenda.stream().map(NfceService::bruto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal descontoVenda = venda.getDesconto() == null ? BigDecimal.ZERO : venda.getDesconto();
        BigDecimal rateado = BigDecimal.ZERO;
        List<NfceEmissao.Item> itens = new ArrayList<>();
        for (int i = 0; i < itensVenda.size(); i++) {
            ItensVenda iv = itensVenda.get(i);
            Produto p = iv.getProduto();
            BigDecimal bruto = bruto(iv);
            BigDecimal parte = i == itensVenda.size() - 1
                    ? descontoVenda.subtract(rateado)
                    : brutoTotal.signum() == 0 ? BigDecimal.ZERO
                    : descontoVenda.multiply(bruto).divide(brutoTotal, 2, RoundingMode.HALF_UP);
            rateado = rateado.add(parte);
            BigDecimal descontoItem = iv.getDescontoItem() == null ? BigDecimal.ZERO : iv.getDescontoItem();
            itens.add(new NfceEmissao.Item(p.getCodigoInterno(), p.getEan13(), p.getNome(), p.getNcm(), p.getCest(),
                    p.getCfop(), p.getOrigem(), p.getCsosn(), p.getUnidadeMedida(), iv.getQuantidade(),
                    iv.getPrecoUnitario(), bruto, descontoItem.add(parte)));
        }
        return itens;
    }

    /** Valor cheio do item, antes do desconto dado nele. */
    private static BigDecimal bruto(ItensVenda iv) {
        BigDecimal desconto = iv.getDescontoItem() == null ? BigDecimal.ZERO : iv.getDescontoItem();
        return iv.getTotalItem().add(desconto);
    }

    private static NfceEmissao.Emitente emitente(ConfiguracaoFiscal c) {
        return new NfceEmissao.Emitente(c.getCnpj(), c.getInscricaoEstadual(), c.getRazaoSocial(), c.getNomeFantasia(),
                c.getRegimeTributario(), c.getLogradouro(), c.getNumeroEndereco(), c.getComplemento(), c.getBairro(),
                c.getCodigoMunicipio(), c.getMunicipio(), c.getCep(), c.getTelefone());
    }

    private CredencialFiscal credencial(ConfiguracaoFiscal c) {
        return new CredencialFiscal(
                Base64.getDecoder().decode(cripto.decifrar(c.getCertificadoCripto())),
                cripto.decifrar(c.getCertificadoSenhaCripto()),
                c.getCscId(), cripto.decifrar(c.getCscTokenCripto()));
    }

    private static NfceDTO paraDto(DocumentoFiscal d, ConfiguracaoFiscal c) {
        NfceDTO.Emitente emitente = c == null ? null : new NfceDTO.Emitente(
                c.getRazaoSocial(), c.getNomeFantasia(), c.getCnpj(), c.getInscricaoEstadual(),
                String.join(", ", naoVazios(c.getLogradouro(), c.getNumeroEndereco(), c.getComplemento(), c.getBairro())),
                String.join(" - ", naoVazios(c.getMunicipio(), c.getUf())));
        return new NfceDTO(d.getId(), d.getVendaId(), d.getStatus(), d.getMensagem(), d.getAmbiente(),
                d.getSerie(), d.getNumero(), d.getChave(), d.getProtocolo(), d.getEmitidaEm(),
                d.getDestinatarioDocumento(), d.getUrlConsulta(),
                d.getUrlQrcode() == null ? null : QrCodeSvg.gerar(d.getUrlQrcode()),
                emitente);
    }

    private static List<String> naoVazios(String... partes) {
        List<String> r = new ArrayList<>();
        for (String p : partes) if (p != null && !p.isBlank()) r.add(p.trim());
        return r;
    }
}
