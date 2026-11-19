package com.acougue.modules.fiscal.emissao;

import com.acougue.entity.ConfiguracaoFiscal;
import com.acougue.entity.DocumentoFiscal;
import com.acougue.entity.ItensVenda;
import com.acougue.entity.PagamentoVenda;
import com.acougue.entity.Produto;
import com.acougue.entity.Venda;
import com.acougue.exception.BusinessException;
import com.acougue.modules.fiscal.emissao.dto.NfceDTO;
import com.acougue.modules.fiscal.parametros.EnquadramentoService;
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
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private final TransactionOperations tx;
    private final EnquadramentoService enquadramento;

    public NfceService(ConfiguracaoFiscalRepository configRepo, DocumentoFiscalRepository documentoRepo,
                       VendaRepository vendaRepo, ItensVendaRepository itensRepo,
                       PagamentoVendaRepository pagamentoRepo, CredencialCripto cripto,
                       List<ProvedorFiscal> provedores, TransactionOperations tx, EnquadramentoService enquadramento) {
        this.configRepo = configRepo;
        this.documentoRepo = documentoRepo;
        this.vendaRepo = vendaRepo;
        this.itensRepo = itensRepo;
        this.pagamentoRepo = pagamentoRepo;
        this.cripto = cripto;
        this.provedores = provedores;
        this.tx = tx;
        this.enquadramento = enquadramento;
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
     * Emite a NFC-e da venda, em três passos para que nenhuma nota transmitida se perca:
     * 1. (transação) confere tudo, reserva o número sob a trava da configuração, gera a chave
     *    de acesso e grava o documento PENDENTE — e confirma no banco ANTES de transmitir;
     * 2. (sem transação) transmite pelo provedor;
     * 3. (transação) grava o resultado no documento e na venda.
     * Se o servidor cair entre 1 e 3, o documento fica PENDENTE com a chave: bloqueia nova emissão
     * da venda e pode ser consultado. Sem resposta do provedor = DESCONHECIDO, nunca ERRO.
     * Uma venda tem no máximo uma NFC-e autorizada: pedir de novo devolve a mesma.
     */
    public NfceDTO emitirParaVenda(Long vendaId, String documento) {
        Preparada p = tx.execute(s -> preparar(vendaId, documento));
        if (p.pronta() != null) return p.pronta();
        RetornoEmissao r = transmitir(p.provedor(), p.nota(), p.credencial());
        return tx.execute(s -> registrar(p.documentoId(), r));
    }

    /**
     * Resolve uma NFC-e PENDENTE ou DESCONHECIDO consultando a SEFAZ pela chave. Só uma resposta
     * definitiva (autorizada, rejeitada, denegada, cancelada) muda o status; o resto mantém
     * a nota como desconhecida.
     */
    public NfceDTO consultar(Long documentoId) {
        Consulta q = tx.execute(s -> {
            DocumentoFiscal d = documentoRepo.findById(documentoId)
                    .orElseThrow(() -> new EntityNotFoundException("Documento fiscal não encontrado: " + documentoId));
            if (d.getModelo() != DocumentoFiscal.MODELO_NFCE || !EM_ABERTO.contains(d.getStatus())) {
                throw new BusinessException("Só se consulta NFC-e com resultado desconhecido ou pendente (esta está " + d.getStatus() + ").");
            }
            if (d.getChave() == null) {
                throw new BusinessException("Esta NFC-e não tem chave gravada: confira a série " + d.getSerie()
                        + " número " + d.getNumero() + " no portal da SEFAZ.");
            }
            ConfiguracaoFiscal c = configRepo.findById(ConfiguracaoFiscal.ID_UNICO)
                    .orElseThrow(() -> new BusinessException("A empresa ainda não tem configuração fiscal."));
            ProvedorFiscal p = provedor(d.getProvedor());
            return new Consulta(p, credencial(c, p, d.getAmbiente()), d.getChave(), d.getAmbiente());
        });

        Optional<RetornoEmissao> r;
        try {
            r = q.provedor().consultarNfce(referencia(documentoId), q.chave(), q.ambiente(), q.credencial());
        } catch (RuntimeException e) {
            log.warn("Consulta da NFC-e {} sem resposta: {}", q.chave(), e.getMessage());
            throw new BusinessException("A consulta não teve resposta do provedor fiscal. A nota continua com resultado desconhecido: tente de novo.");
        }
        if (r.isEmpty()) {
            throw new BusinessException("O provedor \"" + q.provedor().rotulo() + "\" não consulta notas. Confira a chave "
                    + q.chave() + " no portal da SEFAZ.");
        }
        RetornoEmissao retorno = r.get();
        return tx.execute(s -> {
            if (DEFINITIVOS.contains(retorno.status())) return registrar(documentoId, retorno);
            if (RetornoEmissao.NAO_ENCONTRADA.equals(retorno.status())) {
                // o provedor garante que nunca recebeu: não foi transmitida, a venda pode emitir de novo
                return registrar(documentoId, RetornoEmissao.erro("Não transmitida: " + retorno.mensagem()));
            }
            DocumentoFiscal d = documentoRepo.findById(documentoId).orElseThrow();
            d.setStatus("DESCONHECIDO");
            d.setMensagem("Consulta sem resposta definitiva: " + retorno.mensagem());
            return paraDto(documentoRepo.save(d), configRepo.findById(ConfiguracaoFiscal.ID_UNICO).orElse(null));
        });
    }

    /** PENDENTE (transmissão em curso ou interrompida) e DESCONHECIDO: pode ter sido autorizada. */
    static final List<String> EM_ABERTO = List.of("PENDENTE", "DESCONHECIDO");
    private static final Set<String> DEFINITIVOS = Set.of("AUTORIZADA", "REJEITADA", "DENEGADA", "CANCELADA");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private record Preparada(NfceDTO pronta, Long documentoId, ProvedorFiscal provedor, NfceEmissao nota,
                             CredencialFiscal credencial) {}

    private record Consulta(ProvedorFiscal provedor, CredencialFiscal credencial, String chave, String ambiente) {}

    private Preparada preparar(Long vendaId, String documento) {
        ConfiguracaoFiscal c = configRepo.travar()
                .orElseThrow(() -> new BusinessException("A empresa ainda não tem configuração fiscal."));
        if (!Boolean.TRUE.equals(c.getEmitirNfce())) {
            throw new BusinessException("A emissão de NFC-e está desligada. Ligue em Fiscal → Emissão de Notas.");
        }
        ProvedorFiscal provedor = provedor(c.getProvedor());
        if (provedor.simulado() && "PRODUCAO".equals(c.getAmbiente())) {
            throw new BusinessException("O provedor simulado não emite em produção. Escolha um provedor de verdade.");
        }
        // regime vigente hoje (vigência cadastrada), não o campo solto da configuração
        short regime = enquadramento.regimeEm(java.time.LocalDate.now());
        if (regime == 3) {
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
            return new Preparada(paraDto(jaAutorizada.get(), c), null, null, null, null);
        }
        Optional<DocumentoFiscal> emAberto = documentoRepo.findFirstByVendaIdAndModeloAndStatusIn(
                vendaId, DocumentoFiscal.MODELO_NFCE, EM_ABERTO);
        if (emAberto.isPresent()) {
            throw new BusinessException("A NFC-e nº " + emAberto.get().getNumero() + " desta venda está com resultado "
                    + "desconhecido: ela pode ter sido autorizada. Consulte a situação (Fiscal → Emissão de Notas → "
                    + "Documentos) antes de emitir outra.");
        }

        String destinatario = destinatario(documento);
        List<NfceEmissao.Item> itens = itens(venda);
        List<NfceEmissao.Pagamento> pagamentos = pagamentoRepo.findByVendaId(vendaId).stream()
                .map(p -> new NfceEmissao.Pagamento(TPAG.getOrDefault(p.getFormaPagamento(), "99"), p.getValor()))
                .toList();
        CredencialFiscal credencial = credencial(c, provedor, c.getAmbiente());

        // Número reservado sob a trava da configuração: nunca se repete, mesmo se a SEFAZ recusar
        int numero = c.getNfceProximoNumero();
        c.setNfceProximoNumero(numero + 1);
        configRepo.save(c);

        LocalDateTime agora = LocalDateTime.now();
        String chave = ChaveAcesso.gerar(ChaveAcesso.UF_PR, agora, c.getCnpj(), DocumentoFiscal.MODELO_NFCE,
                c.getNfceSerie(), numero, 1, codigoNumerico(numero));
        DocumentoFiscal doc = documentoRepo.save(DocumentoFiscal.builder()
                .modelo(DocumentoFiscal.MODELO_NFCE).serie(c.getNfceSerie()).numero(numero)
                .ambiente(c.getAmbiente()).status("PENDENTE").chave(chave).valorTotal(venda.getTotal())
                .vendaId(vendaId).destinatarioDocumento(destinatario).provedor(provedor.codigo())
                .usuarioId(ContextoUsuario.atual().getUsuarioId())
                .build());
        venda.setNfceStatus("PENDENTE");
        vendaRepo.save(venda);

        NfceEmissao nota = new NfceEmissao(c.getAmbiente(), c.getUf(), emitente(c, regime), c.getNfceSerie(), numero, agora,
                destinatario, itens, pagamentos, venda.getTroco(), venda.getTotal(), vendaId, chave, referencia(doc.getId()));
        return new Preparada(null, doc.getId(), provedor, nota, credencial);
    }

    /** Transmissão fora de transação: nada no banco fica travado esperando a SEFAZ. */
    static RetornoEmissao transmitir(ProvedorFiscal provedor, NfceEmissao nota, CredencialFiscal credencial) {
        try {
            return provedor.emitirNfce(nota, credencial);
        } catch (FalhaAntesDoEnvioException e) {
            log.warn("NFC-e {} não transmitida: {}", nota.numero(), e.getMessage());
            return RetornoEmissao.erro("Não transmitida: " + e.getMessage());
        } catch (RuntimeException e) {
            log.warn("NFC-e {} sem resposta do provedor (resultado desconhecido): {}", nota.numero(), e.getMessage());
            return RetornoEmissao.desconhecido(nota.chave(), "Sem resposta do provedor fiscal (" + e.getMessage()
                    + "). A nota pode ter sido autorizada: consulte a situação antes de emitir de novo.");
        }
    }

    private NfceDTO registrar(Long documentoId, RetornoEmissao r) {
        DocumentoFiscal doc = documentoRepo.findById(documentoId)
                .orElseThrow(() -> new EntityNotFoundException("Documento fiscal não encontrado: " + documentoId));
        if (r.chave() != null && !r.chave().equals(doc.getChave())) {
            log.info("Provedor devolveu a chave {} para a NFC-e gravada com {} (provedor que gera a própria chave)", r.chave(), doc.getChave());
        }
        doc.setStatus(r.status());
        if (r.chave() != null) doc.setChave(r.chave());
        doc.setProtocolo(r.protocolo());
        doc.setMensagem(r.mensagem());
        doc.setUrlQrcode(r.urlQrcode());
        doc.setUrlConsulta(r.urlConsulta());
        doc.setXml(r.xml());
        doc.setIdExterno(r.idExterno());
        if (r.autorizada()) {
            doc.setEmitidaEm(r.autorizadaEm() != null ? r.autorizadaEm() : LocalDateTime.now());
        }
        vendaRepo.findById(doc.getVendaId()).ifPresent(venda -> {
            if (r.autorizada()) venda.setNfceChave(doc.getChave());
            venda.setNfceStatus(r.status());
            vendaRepo.save(venda);
        });
        return paraDto(documentoRepo.save(doc), configRepo.findById(ConfiguracaoFiscal.ID_UNICO).orElse(null));
    }

    private ProvedorFiscal provedor(String codigo) {
        return provedores.stream().filter(p -> p.codigo().equals(codigo)).findFirst()
                .orElseThrow(() -> new BusinessException("O provedor fiscal \"" + codigo + "\" não está disponível."));
    }

    /** cNF: 8 dígitos aleatórios, diferente do número da nota (regra da SEFAZ). */
    static int codigoNumerico(int numero) {
        int cnf;
        do {
            cnf = ALEATORIO.nextInt(100_000_000);
        } while (cnf == numero);
        return cnf;
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

    private static NfceEmissao.Emitente emitente(ConfiguracaoFiscal c, short regime) {
        return new NfceEmissao.Emitente(c.getCnpj(), c.getInscricaoEstadual(), c.getRazaoSocial(), c.getNomeFantasia(),
                regime, c.getLogradouro(), c.getNumeroEndereco(), c.getComplemento(), c.getBairro(),
                c.getCodigoMunicipio(), c.getMunicipio(), c.getCep(), c.getTelefone(), c.getPisCofinsCst());
    }

    /** Identificador da tentativa no provedor: o id do documento, único na empresa. */
    static String referencia(Long documentoId) {
        return "sysflex-nfce-" + documentoId;
    }

    private CredencialFiscal credencial(ConfiguracaoFiscal c, ProvedorFiscal provedor, String ambiente) {
        return Credenciais.de(c, provedor, ambiente, cripto);
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
