package com.acougue.modules.fiscal.emissao;

import com.acougue.entity.ConfiguracaoFiscal;
import com.acougue.entity.DocumentoFiscal;
import com.acougue.exception.BusinessException;
import com.acougue.modules.pagamento.CredencialCripto;
import com.acougue.repository.ConfiguracaoFiscalRepository;
import com.acougue.repository.DocumentoFiscalRepository;
import com.acougue.security.ContextoUsuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDate;
import java.util.*;

/**
 * Números de NFC-e/NF-e que nunca viraram nota autorizada (recusados, não transmitidos ou pulados)
 * e o pedido de inutilização deles à SEFAZ. Mesmo cuidado da emissão: o pedido é gravado
 * (DESCONHECIDO) antes de transmitir, a transmissão fica fora da transação e só uma resposta
 * HOMOLOGADA marca os documentos como INUTILIZADA.
 */
@Service
public class InutilizacaoService {

    private static final Logger log = LoggerFactory.getLogger(InutilizacaoService.class);

    /** Status de documento cujo número pode ser inutilizado: nunca foi nota válida. */
    static final Set<String> INUTILIZAVEIS = Set.of("ERRO", "REJEITADA");
    /** Status que impedem: pode ter sido autorizada (só a consulta resolve). */
    static final Set<String> EM_ABERTO = Set.of("PENDENTE", "DESCONHECIDO");

    private final ConfiguracaoFiscalRepository configRepo;
    private final DocumentoFiscalRepository documentoRepo;
    private final InutilizacaoRepository repo;
    private final CredencialCripto cripto;
    private final List<ProvedorFiscal> provedores;
    private final TransactionOperations tx;

    public InutilizacaoService(ConfiguracaoFiscalRepository configRepo, DocumentoFiscalRepository documentoRepo,
                               InutilizacaoRepository repo, CredencialCripto cripto, List<ProvedorFiscal> provedores,
                               TransactionOperations tx) {
        this.configRepo = configRepo;
        this.documentoRepo = documentoRepo;
        this.repo = repo;
        this.cripto = cripto;
        this.provedores = provedores;
        this.tx = tx;
    }

    /** Faixa contínua de números com o mesmo motivo. */
    public record Lacuna(short modelo, short serie, int numeroInicial, int numeroFinal, String motivo, boolean inutilizavel) {}

    public record Pedido(short modelo, int numeroInicial, int numeroFinal, String justificativa) {}

    @Transactional(readOnly = true)
    public List<Inutilizacao> historico() {
        return repo.findTop100ByOrderByIdDesc();
    }

    /**
     * Números da série atual, do primeiro documento até o último reservado, que não são nota válida.
     * "Sem documento" = número pulado (ex.: próximo número aumentado na configuração).
     */
    @Transactional(readOnly = true)
    public List<Lacuna> lacunas(short modelo) {
        ConfiguracaoFiscal c = config();
        short serie = serie(c, modelo);
        int proximo = proximo(c, modelo);
        Integer primeiro = documentoRepo.menorNumero(modelo, serie, c.getAmbiente());
        if (primeiro == null || primeiro >= proximo) return List.of();
        Map<Integer, DocumentoFiscal> docs = new HashMap<>();
        documentoRepo.findByModeloAndSerieAndAmbienteAndNumeroBetweenOrderByNumero(modelo, serie, c.getAmbiente(), primeiro, proximo - 1)
                .forEach(d -> docs.put(d.getNumero(), d));
        List<Inutilizacao> feitas = repo.findByModeloAndSerieAndAmbienteAndStatusIn(modelo, serie, c.getAmbiente(),
                List.of("HOMOLOGADA", "DESCONHECIDO"));

        List<Lacuna> r = new ArrayList<>();
        Integer ini = null;
        String motivoAtual = null;
        for (int n = primeiro; n <= proximo; n++) {
            String motivo = n == proximo ? null : motivo(n, docs.get(n), feitas);
            if (!Objects.equals(motivo, motivoAtual)) {
                if (motivoAtual != null) r.add(lacuna(modelo, serie, ini, n - 1, motivoAtual));
                ini = n;
                motivoAtual = motivo;
            }
        }
        return r;
    }

    private static String motivo(int numero, DocumentoFiscal d, List<Inutilizacao> feitas) {
        Optional<Inutilizacao> inut = feitas.stream().filter(i -> i.cobre(numero)).findFirst();
        if (inut.isPresent()) {
            return "DESCONHECIDO".equals(inut.get().getStatus()) ? "Inutilização sem resposta" : null;
        }
        if (d == null) return "Sem documento";
        if (INUTILIZAVEIS.contains(d.getStatus())) return "ERRO".equals(d.getStatus()) ? "Não transmitida" : "Rejeitada";
        if (EM_ABERTO.contains(d.getStatus())) return "Resultado desconhecido";
        return null; // autorizada, cancelada, denegada ou já inutilizada
    }

    private static Lacuna lacuna(short modelo, short serie, int ini, int fim, String motivo) {
        boolean pode = !motivo.equals("Resultado desconhecido") && !motivo.equals("Inutilização sem resposta");
        return new Lacuna(modelo, serie, ini, fim, motivo, pode);
    }

    public Inutilizacao inutilizar(Pedido p) {
        String justificativa = p.justificativa() == null ? "" : p.justificativa().trim();
        if (justificativa.length() < 15 || justificativa.length() > 255) {
            throw new BusinessException("A justificativa da inutilização precisa ter de 15 a 255 caracteres.");
        }
        if (p.numeroFinal() < p.numeroInicial()) throw new BusinessException("O número final é menor que o inicial.");

        record Preparado(Long id, ProvedorFiscal provedor, PedidoInutilizacao pedido, CredencialFiscal credencial) {}
        Preparado prep = tx.execute(s -> {
            ConfiguracaoFiscal c = configRepo.travar()
                    .orElseThrow(() -> new BusinessException("A empresa ainda não tem configuração fiscal."));
            short serie = serie(c, p.modelo());
            if (p.numeroFinal() >= proximo(c, p.modelo())) {
                throw new BusinessException("Só se inutiliza número já passado: o próximo número da série é " + proximo(c, p.modelo()) + ".");
            }
            for (int n = p.numeroInicial(); n <= p.numeroFinal(); n++) {
                int numero = n;
                Optional<DocumentoFiscal> d = documentoRepo.findByModeloAndSerieAndAmbienteAndNumeroBetweenOrderByNumero(
                        p.modelo(), serie, c.getAmbiente(), numero, numero).stream().findFirst();
                if (d.isPresent() && !INUTILIZAVEIS.contains(d.get().getStatus())) {
                    throw new BusinessException("O número " + numero + " não pode ser inutilizado: a nota está "
                            + d.get().getStatus() + (EM_ABERTO.contains(d.get().getStatus()) ? " (consulte a situação antes)." : "."));
                }
            }
            boolean sobrepoe = repo.findByModeloAndSerieAndAmbienteAndStatusIn(p.modelo(), serie, c.getAmbiente(),
                    List.of("HOMOLOGADA", "DESCONHECIDO")).stream()
                    .anyMatch(i -> i.getNumeroInicial() <= p.numeroFinal() && i.getNumeroFinal() >= p.numeroInicial());
            if (sobrepoe) throw new BusinessException("Parte desta faixa já foi inutilizada ou tem pedido sem resposta.");

            ProvedorFiscal provedor = provedores.stream().filter(x -> x.codigo().equals(c.getProvedor())).findFirst()
                    .orElseThrow(() -> new BusinessException("O provedor fiscal \"" + c.getProvedor() + "\" não está disponível."));
            if (provedor.simulado() && "PRODUCAO".equals(c.getAmbiente())) {
                throw new BusinessException("O provedor simulado não inutiliza em produção.");
            }
            if (c.getCertificadoCripto() == null) throw new BusinessException("Envie o certificado A1 antes de inutilizar numeração.");
            int ano = LocalDate.now().getYear() % 100;
            Inutilizacao i = repo.save(Inutilizacao.builder().modelo(p.modelo()).serie(serie)
                    .numeroInicial(p.numeroInicial()).numeroFinal(p.numeroFinal()).ano((short) ano)
                    .ambiente(c.getAmbiente()).justificativa(justificativa).status("DESCONHECIDO")
                    .provedor(provedor.codigo()).usuarioId(ContextoUsuario.atual().getUsuarioId()).build());
            CredencialFiscal cred = new CredencialFiscal(
                    Base64.getDecoder().decode(cripto.decifrar(c.getCertificadoCripto())),
                    cripto.decifrar(c.getCertificadoSenhaCripto()), c.getCscId(), null);
            return new Preparado(i.getId(), provedor, new PedidoInutilizacao(c.getAmbiente(), c.getUf(), c.getCnpj(),
                    p.modelo(), serie, p.numeroInicial(), p.numeroFinal(), ano, justificativa), cred);
        });

        Optional<PedidoInutilizacao.Retorno> r;
        String falha = null;
        try {
            r = prep.provedor().inutilizar(prep.pedido(), prep.credencial());
        } catch (RuntimeException e) {
            log.warn("Inutilização {} sem resposta: {}", prep.id(), e.getMessage());
            r = Optional.empty();
            falha = "Sem resposta do provedor fiscal (" + e.getMessage() + "): confira no portal da SEFAZ antes de pedir de novo.";
        }
        if (r.isEmpty() && falha == null) {
            // provedor não inutiliza: nada foi transmitido, o pedido gravado não vale
            tx.executeWithoutResult(s -> repo.deleteById(prep.id()));
            throw new BusinessException("O provedor \"" + prep.provedor().rotulo() + "\" não inutiliza numeração.");
        }
        Optional<PedidoInutilizacao.Retorno> retorno = r;
        String mensagemFalha = falha;
        return tx.execute(s -> {
            Inutilizacao i = repo.findById(prep.id()).orElseThrow();
            if (retorno.isEmpty()) {
                i.setMensagem(mensagemFalha);
                return repo.save(i);
            }
            i.setStatus(retorno.get().status());
            i.setProtocolo(retorno.get().protocolo());
            i.setMensagem(retorno.get().mensagem());
            if ("HOMOLOGADA".equals(retorno.get().status())) {
                documentoRepo.findByModeloAndSerieAndAmbienteAndNumeroBetweenOrderByNumero(i.getModelo(), i.getSerie(),
                        i.getAmbiente(), i.getNumeroInicial(), i.getNumeroFinal()).forEach(d -> {
                    d.setStatus("INUTILIZADA");
                    d.setMensagem("Número inutilizado na SEFAZ, protocolo " + i.getProtocolo());
                    documentoRepo.save(d);
                });
            }
            return repo.save(i);
        });
    }

    private ConfiguracaoFiscal config() {
        return configRepo.findById(ConfiguracaoFiscal.ID_UNICO)
                .orElseThrow(() -> new BusinessException("A empresa ainda não tem configuração fiscal."));
    }

    private static short serie(ConfiguracaoFiscal c, short modelo) {
        return modelo == DocumentoFiscal.MODELO_NFCE ? c.getNfceSerie() : c.getNfeSerie();
    }

    private static int proximo(ConfiguracaoFiscal c, short modelo) {
        return modelo == DocumentoFiscal.MODELO_NFCE ? c.getNfceProximoNumero() : c.getNfeProximoNumero();
    }
}
