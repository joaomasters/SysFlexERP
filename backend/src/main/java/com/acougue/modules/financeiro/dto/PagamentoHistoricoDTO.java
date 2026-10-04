package com.acougue.modules.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Uma linha do histórico de pagamentos/recebimentos de uma conta.
 *
 * usuarioNome só vem preenchido para origem PAGAMENTO (quem registrou); nas
 * origens MIGRACAO e TRANSFERENCIA não houve um usuário registrando, então vem
 * null — o frontend mostra o rótulo da origem no lugar.
 */
public record PagamentoHistoricoDTO(
        Long id,
        LocalDateTime data,
        BigDecimal valor,
        BigDecimal saldoAnterior,
        BigDecimal saldoPosterior,
        String usuarioNome,
        String origem
) {}
