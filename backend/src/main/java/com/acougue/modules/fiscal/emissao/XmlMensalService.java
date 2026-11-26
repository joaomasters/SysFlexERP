package com.acougue.modules.fiscal.emissao;

import com.acougue.entity.DocumentoFiscal;
import com.acougue.exception.BusinessException;
import com.acougue.repository.DocumentoFiscalRepository;
import com.acougue.security.auditoria.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/*
 XMLs das notas para o contador: um por vez ou o mês inteiro num .zip (só produção; homologação
 não tem valor fiscal). Vai junto um resumo.csv com série, número, chave, data, valor e situação.
 Só sai o que tem XML autorizado gravado (inclusive as canceladas depois, que o contador também precisa).
 */
@Service
@RequiredArgsConstructor
public class XmlMensalService {

    static final int MAX_NOTAS = 50_000;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final DocumentoFiscalRepository documentos;
    private final AuditoriaService auditoria;

    public record Arquivo(String nome, byte[] conteudo) {}

    @Transactional   // não readOnly: a auditoria grava na mesma transação
    public Arquivo xml(Long id) {
        DocumentoFiscal d = documentos.findById(id).orElseThrow(() -> new BusinessException("Nota não encontrada."));
        if (d.getXml() == null) throw new BusinessException("Esta nota não tem XML autorizado.");
        auditoria.registrar("FISCAL", "BAIXAR_XML", "XML da " + rotulo(d) + " " + d.getSerie() + "/" + d.getNumero());
        return new Arquivo(nomeXml(d), d.getXml().getBytes(StandardCharsets.UTF_8));
    }

    @Transactional   // não readOnly: a auditoria grava na mesma transação
    public Arquivo zipDoMes(YearMonth mes) {
        LocalDateTime de = mes.atDay(1).atStartOfDay();
        List<DocumentoFiscal> notas = documentos.comXmlNoPeriodo(de, de.plusMonths(1));
        if (notas.isEmpty()) {
            throw new BusinessException("Nenhuma nota de produção com XML em " + mes.format(DateTimeFormatter.ofPattern("MM/yyyy")) + ".");
        }
        if (notas.size() > MAX_NOTAS) {
            throw new BusinessException("Mais de " + MAX_NOTAS + " notas no mês: fale com o suporte para exportar em partes.");
        }

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        StringBuilder resumo = new StringBuilder("﻿modelo;serie;numero;chave;emitida_em;valor;situacao;arquivo\r\n");
        BigDecimal total = BigDecimal.ZERO;
        Set<String> nomes = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(saida, StandardCharsets.UTF_8)) {
            for (DocumentoFiscal d : notas) {
                String nome = nomeXml(d);
                if (!nomes.add(nome)) nome = d.getId() + "-" + nome;
                zip.putNextEntry(new ZipEntry(nome));
                zip.write(d.getXml().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                resumo.append(d.getModelo()).append(';').append(d.getSerie()).append(';').append(d.getNumero()).append(';')
                        .append(d.getChave() == null ? "" : "=\"" + d.getChave() + "\"").append(';')
                        .append(d.getEmitidaEm().format(DATA)).append(';')
                        .append(d.getValorTotal().toPlainString().replace('.', ',')).append(';')
                        .append(d.getStatus()).append(';').append(nome).append("\r\n");
                if ("AUTORIZADA".equals(d.getStatus())) total = total.add(d.getValorTotal());
            }
            zip.putNextEntry(new ZipEntry("resumo.csv"));
            zip.write(resumo.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao montar o .zip dos XMLs", e);
        }
        auditoria.registrar("FISCAL", "BAIXAR_XML", "XMLs de " + mes + ": " + notas.size() + " nota(s), autorizadas R$ " + total);
        return new Arquivo("XML-notas-" + mes + ".zip", saida.toByteArray());
    }

    /** Mesmo padrão dos emissores: chave + "-nfce.xml" / "-nfe.xml". */
    static String nomeXml(DocumentoFiscal d) {
        String sufixo = d.getModelo() == DocumentoFiscal.MODELO_NFE ? "-nfe.xml" : "-nfce.xml";
        String base = d.getChave() != null && d.getChave().matches("\\d{44}")
                ? d.getChave() : "serie" + d.getSerie() + "-numero" + d.getNumero();
        return base + sufixo;
    }

    private static String rotulo(DocumentoFiscal d) {
        return d.getModelo() == DocumentoFiscal.MODELO_NFE ? "NF-e" : "NFC-e";
    }
}
