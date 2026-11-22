-- Contagem de cédulas e moedas na abertura e no fechamento do caixa.
-- Uma linha por denominação contada; só guarda o que o operador discriminou
-- (a contagem é opcional — o valor total continua em caixa.valor_abertura / valor_fechamento_informado).
CREATE TABLE IF NOT EXISTS caixa_cedulas (
    id           BIGSERIAL     PRIMARY KEY,
    caixa_id     BIGINT        NOT NULL REFERENCES caixa(id) ON DELETE CASCADE,
    momento      VARCHAR(12)   NOT NULL CHECK (momento IN ('ABERTURA', 'FECHAMENTO')),
    denominacao  NUMERIC(6,2)  NOT NULL CHECK (denominacao > 0),
    quantidade   INTEGER       NOT NULL CHECK (quantidade > 0),
    UNIQUE (caixa_id, momento, denominacao)
);

CREATE INDEX IF NOT EXISTS idx_caixa_cedulas_caixa ON caixa_cedulas(caixa_id);
