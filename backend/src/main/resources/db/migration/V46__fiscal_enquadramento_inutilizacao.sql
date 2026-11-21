-- Contábil e Fiscal, etapa 1b: parâmetros versionados por vigência e inutilização de numeração.

-- Regime tributário da empresa com vigência: a apuração de um mês usa o regime daquele mês, não o de hoje.
-- Nunca se apaga nem se edita: mudar o regime encerra a vigência aberta e abre outra.
CREATE TABLE IF NOT EXISTS fiscal_enquadramento (
    id                 BIGSERIAL    PRIMARY KEY,
    regime_tributario  SMALLINT     NOT NULL CHECK (regime_tributario BETWEEN 1 AND 4),
    inicio             DATE         NOT NULL,
    fim                DATE,
    observacao         VARCHAR(255),
    usuario_id         BIGINT       REFERENCES usuarios(id),
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    CHECK (fim IS NULL OR fim >= inicio),
    UNIQUE (inicio)
);

-- O regime que já estava configurado vira a primeira vigência (início real desconhecido: registrado desde 2000-01-01)
INSERT INTO fiscal_enquadramento (regime_tributario, inicio, observacao)
SELECT c.regime_tributario, DATE '2000-01-01', 'Regime que já estava na configuração fiscal (início desconhecido)'
FROM configuracao_fiscal c
WHERE NOT EXISTS (SELECT 1 FROM fiscal_enquadramento);

-- Pedido de inutilização de uma faixa de números (NFC-e/NF-e) que nunca virou nota autorizada.
CREATE TABLE IF NOT EXISTS fiscal_inutilizacao (
    id              BIGSERIAL     PRIMARY KEY,
    modelo          SMALLINT      NOT NULL CHECK (modelo IN (55, 65)),
    serie           SMALLINT      NOT NULL,
    numero_inicial  INTEGER       NOT NULL,
    numero_final    INTEGER       NOT NULL,
    ano             SMALLINT      NOT NULL,
    ambiente        VARCHAR(12)   NOT NULL CHECK (ambiente IN ('HOMOLOGACAO', 'PRODUCAO')),
    justificativa   VARCHAR(255)  NOT NULL CHECK (char_length(justificativa) >= 15),
    status          VARCHAR(15)   NOT NULL CHECK (status IN ('HOMOLOGADA', 'REJEITADA', 'DESCONHECIDO')),
    protocolo       VARCHAR(20),
    mensagem        TEXT,
    provedor        VARCHAR(30),
    usuario_id      BIGINT        REFERENCES usuarios(id),
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    CHECK (numero_final >= numero_inicial)
);
CREATE INDEX IF NOT EXISTS idx_fiscal_inutilizacao_serie ON fiscal_inutilizacao (modelo, serie, ambiente);
