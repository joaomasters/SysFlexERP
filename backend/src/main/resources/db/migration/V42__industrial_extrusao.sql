-- Módulos industriais, sprint 4: extrusão. Ligas com faixa de composição, lotes de matéria-prima
-- (tarugo/lingote) com certificado e composição real, receitas de processo por liga e tipo de máquina,
-- medições de processo (origem manual ou sensor) e perdas metálicas.
-- Nenhum valor de processo (temperatura, pressão, velocidade) é gravado aqui: tudo vem do cadastro da empresa.
-- Mudanças em tabelas existentes: só colunas novas e opcionais (ind_ficha_tecnica.liga_id, ind_consumo.lote_mp_id).

CREATE TABLE IF NOT EXISTS ind_liga (
    id               BIGSERIAL    PRIMARY KEY,
    codigo           VARCHAR(20)  NOT NULL,
    descricao        VARCHAR(120) NOT NULL,
    norma            VARCHAR(60),
    densidade_g_cm3  NUMERIC(6,3) CHECK (densidade_g_cm3 IS NULL OR densidade_g_cm3 > 0),
    observacao       TEXT,
    ativo            BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_ind_liga_codigo UNIQUE (codigo)
);

-- Faixa de cada elemento químico na liga (% em massa).
CREATE TABLE IF NOT EXISTS ind_liga_elemento (
    id        BIGSERIAL    PRIMARY KEY,
    liga_id   BIGINT       NOT NULL REFERENCES ind_liga(id) ON DELETE CASCADE,
    elemento  VARCHAR(4)   NOT NULL,
    minimo    NUMERIC(7,4) CHECK (minimo IS NULL OR (minimo >= 0 AND minimo <= 100)),
    maximo    NUMERIC(7,4) CHECK (maximo IS NULL OR (maximo >= 0 AND maximo <= 100)),
    CONSTRAINT uk_ind_liga_elemento UNIQUE (liga_id, elemento),
    CONSTRAINT ck_ind_liga_elemento_faixa CHECK (minimo IS NULL OR maximo IS NULL OR minimo <= maximo)
);

-- A liga do produto (perfil, tarugo): coluna nova e opcional na ficha técnica.
ALTER TABLE ind_ficha_tecnica ADD COLUMN IF NOT EXISTS liga_id BIGINT REFERENCES ind_liga(id);

-- Lote de matéria-prima recebido (corrida do fornecedor), com certificado e situação.
CREATE TABLE IF NOT EXISTS ind_lote_mp (
    id                BIGSERIAL     PRIMARY KEY,
    numero            VARCHAR(40)   NOT NULL,
    produto_id        BIGINT        NOT NULL REFERENCES produtos(id),
    liga_id           BIGINT        NOT NULL REFERENCES ind_liga(id),
    fornecedor_id     BIGINT        REFERENCES fornecedores(id),
    certificado       VARCHAR(60),
    data_recebimento  DATE          NOT NULL,
    quantidade        NUMERIC(14,4) NOT NULL CHECK (quantidade > 0),
    diametro_mm       NUMERIC(8,2)  CHECK (diametro_mm IS NULL OR diametro_mm > 0),
    comprimento_mm    NUMERIC(10,2) CHECK (comprimento_mm IS NULL OR comprimento_mm > 0),
    status            VARCHAR(12)   NOT NULL DEFAULT 'QUARENTENA' CHECK (status IN ('QUARENTENA', 'LIBERADO', 'BLOQUEADO')),
    motivo_status     TEXT,
    status_por_id     BIGINT,
    status_em         TIMESTAMP,
    observacao        TEXT,
    criado_por_id     BIGINT,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    versao            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_ind_lote_mp_numero UNIQUE (produto_id, numero)
);
CREATE INDEX IF NOT EXISTS ix_ind_lote_mp_status ON ind_lote_mp (status);

CREATE TABLE IF NOT EXISTS ind_lote_mp_composicao (
    id          BIGSERIAL    PRIMARY KEY,
    lote_id     BIGINT       NOT NULL REFERENCES ind_lote_mp(id) ON DELETE CASCADE,
    elemento    VARCHAR(4)   NOT NULL,
    percentual  NUMERIC(7,4) NOT NULL CHECK (percentual >= 0 AND percentual <= 100),
    CONSTRAINT uk_ind_lote_mp_elemento UNIQUE (lote_id, elemento)
);

-- O consumo passa a poder apontar o lote de matéria-prima (rastreabilidade).
ALTER TABLE ind_consumo ADD COLUMN IF NOT EXISTS lote_mp_id BIGINT REFERENCES ind_lote_mp(id);
CREATE INDEX IF NOT EXISTS ix_ind_consumo_lote_mp ON ind_consumo (lote_mp_id);

-- Receita de processo: parâmetros com alvo e faixa para uma liga num tipo de máquina, numa etapa.
CREATE TABLE IF NOT EXISTS ind_receita (
    id               BIGSERIAL    PRIMARY KEY,
    codigo           VARCHAR(20)  NOT NULL,
    descricao        VARCHAR(120) NOT NULL,
    etapa            VARCHAR(20)  NOT NULL CHECK (etapa IN ('AQUECIMENTO', 'EXTRUSAO', 'RESFRIAMENTO', 'TRATAMENTO_TERMICO', 'OUTRA')),
    liga_id          BIGINT       NOT NULL REFERENCES ind_liga(id),
    tipo_maquina_id  BIGINT       NOT NULL REFERENCES ind_tipo_maquina(id),
    produto_id       BIGINT       REFERENCES produtos(id),
    revisao          INTEGER      NOT NULL DEFAULT 1 CHECK (revisao > 0),
    observacao       TEXT,
    ativo            BOOLEAN      NOT NULL DEFAULT TRUE,
    atualizado_por_id BIGINT,
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ind_receita_codigo UNIQUE (codigo)
);

CREATE TABLE IF NOT EXISTS ind_receita_parametro (
    id          BIGSERIAL     PRIMARY KEY,
    receita_id  BIGINT        NOT NULL REFERENCES ind_receita(id) ON DELETE CASCADE,
    ordem       SMALLINT      NOT NULL DEFAULT 0,
    codigo      VARCHAR(30)   NOT NULL,
    nome        VARCHAR(100)  NOT NULL,
    unidade     VARCHAR(15),
    alvo        NUMERIC(18,6),
    minimo      NUMERIC(18,6),
    maximo      NUMERIC(18,6),
    obrigatorio BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_ind_receita_parametro UNIQUE (receita_id, codigo),
    CONSTRAINT ck_ind_receita_param_faixa CHECK (minimo IS NULL OR maximo IS NULL OR minimo <= maximo)
);

-- Medição de processo. Guarda a faixa vigente no momento (a receita pode mudar depois).
CREATE TABLE IF NOT EXISTS ind_medicao (
    id                  BIGSERIAL    PRIMARY KEY,
    chave               VARCHAR(40)  NOT NULL,
    ordem_id            BIGINT       NOT NULL REFERENCES ind_ordem_producao(id),
    apontamento_id      BIGINT       REFERENCES ind_apontamento(id),
    maquina_id          BIGINT       REFERENCES ind_maquina(id),
    receita_id          BIGINT       NOT NULL REFERENCES ind_receita(id),
    receita_revisao     INTEGER      NOT NULL,
    etapa               VARCHAR(20)  NOT NULL,
    origem              VARCHAR(8)   NOT NULL DEFAULT 'MANUAL' CHECK (origem IN ('MANUAL', 'SENSOR')),
    medido_em           TIMESTAMP    NOT NULL,
    fora_especificacao  BOOLEAN      NOT NULL DEFAULT FALSE,
    observacao          TEXT,
    usuario_id          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ind_medicao_chave UNIQUE (chave)
);
CREATE INDEX IF NOT EXISTS ix_ind_medicao_ordem ON ind_medicao (ordem_id);
CREATE INDEX IF NOT EXISTS ix_ind_medicao_data ON ind_medicao (medido_em);

CREATE TABLE IF NOT EXISTS ind_medicao_valor (
    id            BIGSERIAL     PRIMARY KEY,
    medicao_id    BIGINT        NOT NULL REFERENCES ind_medicao(id) ON DELETE CASCADE,
    codigo        VARCHAR(30)   NOT NULL,
    nome          VARCHAR(100)  NOT NULL,
    unidade       VARCHAR(15),
    valor         NUMERIC(18,6) NOT NULL,
    alvo          NUMERIC(18,6),
    minimo        NUMERIC(18,6),
    maximo        NUMERIC(18,6),
    dentro_faixa  BOOLEAN       NOT NULL
);

-- Perdas metálicas da extrusão (topo de tarugo, ponta de perfil, retalho, sucata), em kg.
CREATE TABLE IF NOT EXISTS ind_perda (
    id                BIGSERIAL     PRIMARY KEY,
    chave             VARCHAR(40)   NOT NULL,
    ordem_id          BIGINT        NOT NULL REFERENCES ind_ordem_producao(id),
    apontamento_id    BIGINT        REFERENCES ind_apontamento(id),
    tipo              VARCHAR(16)   NOT NULL CHECK (tipo IN ('TOPO_TARUGO', 'PONTA_PERFIL', 'RETALHO', 'SUCATA_PROCESSO', 'OUTRO')),
    quantidade_kg     NUMERIC(14,4) NOT NULL CHECK (quantidade_kg > 0),
    observacao        TEXT,
    usuario_id        BIGINT,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    estornada         BOOLEAN       NOT NULL DEFAULT FALSE,
    estornada_em      TIMESTAMP,
    estornada_por_id  BIGINT,
    motivo_estorno    TEXT,
    CONSTRAINT uk_ind_perda_chave UNIQUE (chave)
);
CREATE INDEX IF NOT EXISTS ix_ind_perda_ordem ON ind_perda (ordem_id);

-- Permissão do cadastro de extrusão (ligas, lotes de MP, receitas). Medições e perdas são do chão (IND_APONTAMENTO).
INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT p.id, 'IND_EXTRUSAO', TRUE, TRUE, TRUE, TRUE FROM perfis p
WHERE p.nome IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = p.id AND pp.modulo = 'IND_EXTRUSAO');

INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT p.id, 'IND_EXTRUSAO', FALSE, FALSE, FALSE, FALSE FROM perfis p
WHERE p.nome NOT IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = p.id AND pp.modulo = 'IND_EXTRUSAO');
