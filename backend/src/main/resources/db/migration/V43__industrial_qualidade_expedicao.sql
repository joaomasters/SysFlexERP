-- Módulos industriais, sprints 5 e 6: qualidade e rastreabilidade (lote de produto com saldo e situação,
-- genealogia de divisão/união, planos de inspeção, inspeções e não conformidades) e expedição
-- (pedido de expedição, reserva de lote, conferência, carregamento e ligação com a NF de saída).
-- Mudanças em tabelas existentes: só ampliar a lista de tipos de movimentação de estoque (todos os
-- anteriores continuam valendo) e uma coluna nova e opcional em nota_fiscal_saida.

ALTER TABLE movimentacao_estoque DROP CONSTRAINT IF EXISTS movimentacao_estoque_tipo_movimentacao_check;
ALTER TABLE movimentacao_estoque ADD CONSTRAINT movimentacao_estoque_tipo_movimentacao_check
    CHECK (tipo_movimentacao IN (
        'ENTRADA_COMPRA','ENTRADA_DESOSSA','SAIDA_VENDA',
        'SAIDA_DESOSSA','SAIDA_DESCARTE','SAIDA_QUEBRA',
        'SAIDA_MOAGEM','AJUSTE_POSITIVO','AJUSTE_NEGATIVO',
        'SAIDA_NF','ENTRADA_ESTORNO_NF','SAIDA_ESTORNO_RECEBIMENTO',
        'ENTRADA_PRODUCAO','SAIDA_ESTORNO_PRODUCAO','SAIDA_CONSUMO_PRODUCAO','ENTRADA_ESTORNO_CONSUMO',
        -- expedição industrial
        'SAIDA_EXPEDICAO'
    ));

-- NF de saída gerada a partir de uma expedição: o estoque já saiu no carregamento, a nota não baixa de novo.
ALTER TABLE nota_fiscal_saida ADD COLUMN IF NOT EXISTS expedicao_id BIGINT;

-- ── Lote de produto (acabado ou semiacabado) ───────────────────────────
CREATE SEQUENCE IF NOT EXISTS ind_lote_seq START 1;

CREATE TABLE IF NOT EXISTS ind_lote (
    id                 BIGSERIAL     PRIMARY KEY,
    codigo             VARCHAR(30)   NOT NULL,
    produto_id         BIGINT        NOT NULL REFERENCES produtos(id),
    ordem_id           BIGINT        REFERENCES ind_ordem_producao(id),
    apontamento_id     BIGINT        REFERENCES ind_apontamento(id),
    origem             VARCHAR(10)   NOT NULL CHECK (origem IN ('PRODUCAO', 'DIVISAO', 'UNIAO')),
    quantidade_inicial NUMERIC(14,4) NOT NULL CHECK (quantidade_inicial > 0),
    saldo              NUMERIC(14,4) NOT NULL CHECK (saldo >= 0),
    situacao           VARCHAR(12)   NOT NULL CHECK (situacao IN ('QUARENTENA', 'LIBERADO', 'BLOQUEADO', 'ESTORNADO')),
    motivo_situacao    TEXT,
    situacao_por_id    BIGINT,
    situacao_em        TIMESTAMP,
    criado_por_id      BIGINT,
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    versao             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_ind_lote_codigo UNIQUE (codigo),
    CONSTRAINT ck_ind_lote_saldo CHECK (saldo <= quantidade_inicial)
);
CREATE INDEX IF NOT EXISTS ix_ind_lote_produto ON ind_lote (produto_id, situacao);
CREATE INDEX IF NOT EXISTS ix_ind_lote_ordem ON ind_lote (ordem_id);

-- De quais lotes um lote veio (divisão e união).
CREATE TABLE IF NOT EXISTS ind_lote_genealogia (
    id           BIGSERIAL     PRIMARY KEY,
    lote_id      BIGINT        NOT NULL REFERENCES ind_lote(id),
    lote_pai_id  BIGINT        NOT NULL REFERENCES ind_lote(id),
    quantidade   NUMERIC(14,4) NOT NULL CHECK (quantidade > 0),
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_ind_lote_genealogia_pai CHECK (lote_id <> lote_pai_id)
);
CREATE INDEX IF NOT EXISTS ix_ind_lote_gen_filho ON ind_lote_genealogia (lote_id);
CREATE INDEX IF NOT EXISTS ix_ind_lote_gen_pai ON ind_lote_genealogia (lote_pai_id);

-- Extrato do lote: cada entrada e saída de saldo, com o documento que a causou.
CREATE TABLE IF NOT EXISTS ind_lote_movimento (
    id          BIGSERIAL     PRIMARY KEY,
    lote_id     BIGINT        NOT NULL REFERENCES ind_lote(id),
    tipo        VARCHAR(20)   NOT NULL CHECK (tipo IN ('PRODUCAO', 'ESTORNO_PRODUCAO', 'DIVISAO', 'UNIAO', 'EXPEDICAO', 'SUCATA')),
    quantidade  NUMERIC(14,4) NOT NULL,
    documento   VARCHAR(50),
    usuario_id  BIGINT,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_ind_lote_mov_lote ON ind_lote_movimento (lote_id);

-- ── Planos de inspeção ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ind_plano_inspecao (
    id           BIGSERIAL    PRIMARY KEY,
    codigo       VARCHAR(20)  NOT NULL,
    descricao    VARCHAR(120) NOT NULL,
    produto_id   BIGINT       NOT NULL REFERENCES produtos(id),
    revisao      INTEGER      NOT NULL DEFAULT 1 CHECK (revisao > 0),
    observacao   TEXT,
    ativo        BOOLEAN      NOT NULL DEFAULT TRUE,
    atualizado_por_id BIGINT,
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ind_plano_codigo UNIQUE (codigo)
);
-- No máximo um plano ativo por produto.
CREATE UNIQUE INDEX IF NOT EXISTS uk_ind_plano_ativo ON ind_plano_inspecao (produto_id) WHERE ativo;

CREATE TABLE IF NOT EXISTS ind_plano_caracteristica (
    id          BIGSERIAL     PRIMARY KEY,
    plano_id    BIGINT        NOT NULL REFERENCES ind_plano_inspecao(id) ON DELETE CASCADE,
    ordem       SMALLINT      NOT NULL DEFAULT 0,
    codigo      VARCHAR(30)   NOT NULL,
    nome        VARCHAR(100)  NOT NULL,
    tipo        VARCHAR(8)    NOT NULL CHECK (tipo IN ('MEDIDA', 'ATRIBUTO')),
    unidade     VARCHAR(15),
    minimo      NUMERIC(18,6),
    maximo      NUMERIC(18,6),
    obrigatorio BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_ind_plano_carac UNIQUE (plano_id, codigo),
    CONSTRAINT ck_ind_plano_carac_faixa CHECK (minimo IS NULL OR maximo IS NULL OR minimo <= maximo)
);

-- ── Inspeções ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ind_inspecao (
    id             BIGSERIAL    PRIMARY KEY,
    chave          VARCHAR(40)  NOT NULL,
    lote_id        BIGINT       NOT NULL REFERENCES ind_lote(id),
    plano_id       BIGINT       NOT NULL REFERENCES ind_plano_inspecao(id),
    plano_revisao  INTEGER      NOT NULL,
    resultado      VARCHAR(10)  NOT NULL CHECK (resultado IN ('APROVADO', 'REPROVADO')),
    amostra        INTEGER      CHECK (amostra IS NULL OR amostra > 0),
    observacao     TEXT,
    inspetor_id    BIGINT,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ind_inspecao_chave UNIQUE (chave)
);
CREATE INDEX IF NOT EXISTS ix_ind_inspecao_lote ON ind_inspecao (lote_id);

CREATE TABLE IF NOT EXISTS ind_inspecao_valor (
    id            BIGSERIAL     PRIMARY KEY,
    inspecao_id   BIGINT        NOT NULL REFERENCES ind_inspecao(id) ON DELETE CASCADE,
    codigo        VARCHAR(30)   NOT NULL,
    nome          VARCHAR(100)  NOT NULL,
    tipo          VARCHAR(8)    NOT NULL,
    unidade       VARCHAR(15),
    valor_numero  NUMERIC(18,6),
    minimo        NUMERIC(18,6),
    maximo        NUMERIC(18,6),
    conforme      BOOLEAN       NOT NULL
);

-- ── Não conformidades ──────────────────────────────────────────────────
CREATE SEQUENCE IF NOT EXISTS ind_nc_numero_seq START 1;

CREATE TABLE IF NOT EXISTS ind_nao_conformidade (
    id                BIGSERIAL    PRIMARY KEY,
    numero            VARCHAR(20)  NOT NULL,
    origem            VARCHAR(10)  NOT NULL CHECK (origem IN ('INSPECAO', 'PROCESSO', 'CLIENTE', 'INTERNA')),
    lote_id           BIGINT       REFERENCES ind_lote(id),
    ordem_id          BIGINT       REFERENCES ind_ordem_producao(id),
    inspecao_id       BIGINT       REFERENCES ind_inspecao(id),
    descricao         TEXT         NOT NULL,
    status            VARCHAR(10)  NOT NULL DEFAULT 'ABERTA' CHECK (status IN ('ABERTA', 'ENCERRADA')),
    disposicao        VARCHAR(16)  CHECK (disposicao IN ('USAR_COMO_ESTA', 'RETRABALHAR', 'SUCATEAR', 'SEM_LOTE')),
    causa             TEXT,
    acao_corretiva    TEXT,
    aberta_por_id     BIGINT,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    encerrada_por_id  BIGINT,
    encerrada_em      TIMESTAMP,
    versao            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_ind_nc_numero UNIQUE (numero)
);
CREATE INDEX IF NOT EXISTS ix_ind_nc_lote ON ind_nao_conformidade (lote_id);
CREATE INDEX IF NOT EXISTS ix_ind_nc_status ON ind_nao_conformidade (status);

-- ── Expedição ──────────────────────────────────────────────────────────
CREATE SEQUENCE IF NOT EXISTS ind_expedicao_numero_seq START 1;

CREATE TABLE IF NOT EXISTS ind_expedicao (
    id                  BIGSERIAL    PRIMARY KEY,
    numero              VARCHAR(20)  NOT NULL,
    cliente_id          BIGINT       NOT NULL REFERENCES clientes(id),
    pedido_referencia   VARCHAR(40),
    data_prevista       DATE         NOT NULL,
    status              VARCHAR(10)  NOT NULL DEFAULT 'ABERTA' CHECK (status IN ('ABERTA', 'CONFERIDA', 'CARREGADA', 'CANCELADA')),
    transportadora      VARCHAR(120),
    placa               VARCHAR(10),
    motorista           VARCHAR(120),
    observacao          TEXT,
    carregada_em        TIMESTAMP,
    carregada_por_id    BIGINT,
    nota_fiscal_id      BIGINT       REFERENCES nota_fiscal_saida(id),
    motivo_cancelamento TEXT,
    criado_por_id       BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    versao              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_ind_expedicao_numero UNIQUE (numero)
);
CREATE INDEX IF NOT EXISTS ix_ind_expedicao_status ON ind_expedicao (status);

CREATE TABLE IF NOT EXISTS ind_expedicao_item (
    id            BIGSERIAL     PRIMARY KEY,
    expedicao_id  BIGINT        NOT NULL REFERENCES ind_expedicao(id) ON DELETE CASCADE,
    produto_id    BIGINT        NOT NULL REFERENCES produtos(id),
    quantidade    NUMERIC(14,4) NOT NULL CHECK (quantidade > 0),
    CONSTRAINT uk_ind_expedicao_item UNIQUE (expedicao_id, produto_id)
);

CREATE TABLE IF NOT EXISTS ind_expedicao_reserva (
    id                BIGSERIAL     PRIMARY KEY,
    item_id           BIGINT        NOT NULL REFERENCES ind_expedicao_item(id) ON DELETE CASCADE,
    lote_id           BIGINT        NOT NULL REFERENCES ind_lote(id),
    quantidade        NUMERIC(14,4) NOT NULL CHECK (quantidade > 0),
    conferida         BOOLEAN       NOT NULL DEFAULT FALSE,
    conferida_em      TIMESTAMP,
    conferida_por_id  BIGINT,
    CONSTRAINT uk_ind_expedicao_reserva UNIQUE (item_id, lote_id)
);
CREATE INDEX IF NOT EXISTS ix_ind_reserva_lote ON ind_expedicao_reserva (lote_id);

-- Permissões: qualidade/rastreabilidade e expedição. SUPER_ADMIN e ADMIN com tudo; os demais sem acesso.
INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT p.id, m.modulo, TRUE, TRUE, TRUE, TRUE
FROM perfis p CROSS JOIN (VALUES ('IND_QUALIDADE'), ('IND_EXPEDICAO')) AS m(modulo)
WHERE p.nome IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = p.id AND pp.modulo = m.modulo);

INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT p.id, m.modulo, FALSE, FALSE, FALSE, FALSE
FROM perfis p CROSS JOIN (VALUES ('IND_QUALIDADE'), ('IND_EXPEDICAO')) AS m(modulo)
WHERE p.nome NOT IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = p.id AND pp.modulo = m.modulo);
