package com.acougue.exception;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Venda no fiado que levaria o cliente acima do limite de crédito cadastrado. Só segue com a
 * autorização de um administrador — o PDV recebe esta resposta (HTTP 409, código
 * LIMITE_CREDITO_EXCEDIDO) com os valores e pede a credencial de um administrador.
 *
 * Estende BusinessException de propósito: qualquer trecho que já trata BusinessException (e o
 * rollback da transação) continua valendo; o handler específico responde 409 em vez de 422.
 */
public class LimiteCreditoExcedidoException extends BusinessException {

    public static final String CODIGO = "LIMITE_CREDITO_EXCEDIDO";

    private final String clienteNome;
    private final BigDecimal limite;
    private final BigDecimal saldoAtual;
    private final BigDecimal valorFiado;
    private final BigDecimal excedente;

    public LimiteCreditoExcedidoException(String clienteNome, BigDecimal limite, BigDecimal saldoAtual,
                                          BigDecimal valorFiado, BigDecimal excedente) {
        super(montarMensagem(clienteNome, limite, saldoAtual, valorFiado, excedente));
        this.clienteNome = clienteNome;
        this.limite = limite;
        this.saldoAtual = saldoAtual;
        this.valorFiado = valorFiado;
        this.excedente = excedente;
    }

    private static String montarMensagem(String cliente, BigDecimal limite, BigDecimal saldo,
                                         BigDecimal valor, BigDecimal excedente) {
        return "Limite de crédito excedido para " + cliente + ": limite " + brl(limite)
                + ", saldo em aberto " + brl(saldo) + ", esta venda " + brl(valor)
                + " (excede em " + brl(excedente) + "). É necessária a autorização de um administrador.";
    }

    /** Formata como "R$ 1.000,00" (pt-BR), independente do idioma configurado no servidor. */
    public static String brl(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(v).replace('\u00a0', ' ');
    }

    public String getClienteNome() { return clienteNome; }
    public BigDecimal getLimite()  { return limite; }
    public BigDecimal getSaldoAtual() { return saldoAtual; }
    public BigDecimal getValorFiado() { return valorFiado; }
    public BigDecimal getExcedente()  { return excedente; }
}