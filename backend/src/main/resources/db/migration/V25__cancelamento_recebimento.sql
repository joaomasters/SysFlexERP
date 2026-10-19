-- Cancelamento de entrada de mercadoria (recebimento).
--
-- Permitido até 7 dias após a entrada (prazo de cancelamento adotado no
-- Paraná; configurável em recebimento.prazo-cancelamento-dias). Uso típico:
-- a entrada foi registrada sem a data de validade — cancela e lança de novo.
--
-- Cancelar NÃO apaga o recebimento: ele fica com status CANCELADO, com quem,
-- quando e por quê, e o estoque é estornado com uma movimentação própria
-- (SAIDA_ESTORNO_RECEBIMENTO), mantendo o histórico auditável.

ALTER TABLE recebimento_mercadoria
    ADD COLUMN cancelado_em        TIMESTAMP,
    ADD COLUMN cancelado_por_id    BIGINT REFERENCES usuarios(id),
    ADD COLUMN motivo_cancelamento TEXT;

ALTER TABLE movimentacao_estoque DROP CONSTRAINT movimentacao_estoque_tipo_movimentacao_check;
ALTER TABLE movimentacao_estoque ADD CONSTRAINT movimentacao_estoque_tipo_movimentacao_check
    CHECK (tipo_movimentacao IN (
        'ENTRADA_COMPRA','ENTRADA_DESOSSA','SAIDA_VENDA',
        'SAIDA_DESOSSA','SAIDA_DESCARTE','SAIDA_QUEBRA',
        'SAIDA_MOAGEM','AJUSTE_POSITIVO','AJUSTE_NEGATIVO',
        'SAIDA_NF','ENTRADA_ESTORNO_NF','SAIDA_ESTORNO_RECEBIMENTO'
    ));
