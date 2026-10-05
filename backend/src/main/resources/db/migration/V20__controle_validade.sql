-- Controle de validade por lote.
--
-- lote_estoque: um lote por entrada de estoque COM validade conhecida. A baixa
-- (venda, NF, perda, desossa, inventário) consome os lotes em ordem FEFO —
-- vence primeiro, sai primeiro — dentro do EstoqueService.
--
-- Estoque sem validade conhecida (anterior a esta migration, ou entrada sem
-- validade num produto sem "validade padrão") NÃO tem lote: continua só em
-- produtos.estoque_atual. Por isso vale sempre SUM(lote.quantidade_atual) <=
-- produtos.estoque_atual, e a baixa nunca falha por causa dos lotes.
CREATE TABLE lote_estoque (
    id                 BIGSERIAL     PRIMARY KEY,
    produto_id         BIGINT        NOT NULL REFERENCES produtos(id),
    data_validade      DATE          NOT NULL,
    quantidade_inicial NUMERIC(12,4) NOT NULL CHECK (quantidade_inicial > 0),
    quantidade_atual   NUMERIC(12,4) NOT NULL CHECK (quantidade_atual >= 0),
    documento_ref      VARCHAR(100),
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Só os lotes com saldo importam para baixa e alerta (índices parciais).
CREATE INDEX idx_lote_produto_validade ON lote_estoque(produto_id, data_validade)
    WHERE quantidade_atual > 0;
CREATE INDEX idx_lote_validade ON lote_estoque(data_validade)
    WHERE quantidade_atual > 0;

-- Validade informada no recebimento, por item (aparece nos detalhes do recebimento).
ALTER TABLE recebimento_item
    ADD COLUMN data_validade DATE;

-- Prazo de validade padrão do produto, em dias. Quando uma entrada chega sem
-- validade explícita (ex: cortes gerados na desossa, estorno de NF), o lote
-- nasce com validade = hoje + este prazo. NULL = produto sem controle de validade
-- automático.
ALTER TABLE produtos
    ADD COLUMN validade_padrao_dias INTEGER
        CHECK (validade_padrao_dias IS NULL OR validade_padrao_dias > 0);
