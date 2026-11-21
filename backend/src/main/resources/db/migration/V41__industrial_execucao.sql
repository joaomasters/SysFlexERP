-- Módulos industriais, sprint 3: execução (MES). Apontamentos de produção, paradas com motivo,
-- consumo real de material e entrada do produto acabado no estoque.
-- A única mudança em tabela existente é ampliar a lista de tipos de movimentação de estoque
-- (todos os tipos anteriores continuam valendo).

ALTER TABLE movimentacao_estoque DROP CONSTRAINT IF EXISTS movimentacao_estoque_tipo_movimentacao_check;
ALTER TABLE movimentacao_estoque ADD CONSTRAINT movimentacao_estoque_tipo_movimentacao_check
    CHECK (tipo_movimentacao IN (
        'ENTRADA_COMPRA','ENTRADA_DESOSSA','SAIDA_VENDA',
        'SAIDA_DESOSSA','SAIDA_DESCARTE','SAIDA_QUEBRA',
        'SAIDA_MOAGEM','AJUSTE_POSITIVO','AJUSTE_NEGATIVO',
        'SAIDA_NF','ENTRADA_ESTORNO_NF','SAIDA_ESTORNO_RECEBIMENTO',
        -- produção industrial
        'ENTRADA_PRODUCAO','SAIDA_ESTORNO_PRODUCAO','SAIDA_CONSUMO_PRODUCAO','ENTRADA_ESTORNO_CONSUMO'
    ));

-- Motivos de parada (cadastro da empresa)
CREATE TABLE IF NOT EXISTS ind_motivo_parada (
    id          BIGSERIAL    PRIMARY KEY,
    codigo      VARCHAR(20)  NOT NULL,
    descricao   VARCHAR(100) NOT NULL,
    categoria   VARCHAR(15)  NOT NULL CHECK (categoria IN ('SETUP', 'MANUTENCAO', 'FALTA_MATERIAL', 'QUALIDADE',
                                                           'OPERACIONAL', 'EXTERNA', 'OUTRO')),
    planejada   BOOLEAN      NOT NULL DEFAULT FALSE,
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_ind_motivo_parada_codigo UNIQUE (codigo)
);

INSERT INTO ind_motivo_parada (codigo, descricao, categoria, planejada) VALUES
    ('SETUP', 'Preparação / troca de ferramenta', 'SETUP', TRUE),
    ('REFEICAO', 'Refeição / intervalo', 'OPERACIONAL', TRUE),
    ('MANUT-CORR', 'Quebra / manutenção corretiva', 'MANUTENCAO', FALSE),
    ('FALTA-MAT', 'Falta de material', 'FALTA_MATERIAL', FALSE),
    ('QUALIDADE', 'Problema de qualidade', 'QUALIDADE', FALSE),
    ('ENERGIA', 'Falta de energia / utilidades', 'EXTERNA', FALSE),
    ('OUTRO', 'Outro motivo', 'OUTRO', FALSE)
ON CONFLICT (codigo) DO NOTHING;

-- Apontamento: uma sessão de trabalho numa operação da OP (início, pausas, fim e quantidades).
-- Apontamentos parciais: vários por operação; a operação só conclui quando o operador marca.
CREATE TABLE IF NOT EXISTS ind_apontamento (
    id                  BIGSERIAL     PRIMARY KEY,
    chave               VARCHAR(40)   NOT NULL,
    ordem_id            BIGINT        NOT NULL REFERENCES ind_ordem_producao(id),
    operacao_id         BIGINT        NOT NULL REFERENCES ind_op_operacao(id),
    maquina_id          BIGINT        REFERENCES ind_maquina(id),
    operador_id         BIGINT        NOT NULL,
    status              VARCHAR(12)   NOT NULL DEFAULT 'EM_ANDAMENTO'
                        CHECK (status IN ('EM_ANDAMENTO', 'PAUSADO', 'FINALIZADO', 'ESTORNADO')),
    inicio              TIMESTAMP     NOT NULL,
    fim                 TIMESTAMP,
    minutos_parado      NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (minutos_parado >= 0),
    quantidade_boa      NUMERIC(14,4) NOT NULL DEFAULT 0 CHECK (quantidade_boa >= 0),
    quantidade_refugo   NUMERIC(14,4) NOT NULL DEFAULT 0 CHECK (quantidade_refugo >= 0),
    motivo_refugo       VARCHAR(200),
    lote                VARCHAR(30),
    concluiu_operacao   BOOLEAN       NOT NULL DEFAULT FALSE,
    entrada_estoque     BOOLEAN       NOT NULL DEFAULT FALSE,
    chave_finalizacao   VARCHAR(40),
    observacao          TEXT,
    estornado_em        TIMESTAMP,
    estornado_por_id    BIGINT,
    motivo_estorno      TEXT,
    versao              BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ind_apontamento_chave UNIQUE (chave),
    CONSTRAINT uk_ind_apontamento_lote UNIQUE (lote),
    CONSTRAINT ck_ind_apontamento_fim CHECK (fim IS NULL OR fim >= inicio)
);
CREATE INDEX IF NOT EXISTS ix_ind_apontamento_ordem ON ind_apontamento (ordem_id);
CREATE INDEX IF NOT EXISTS ix_ind_apontamento_operacao ON ind_apontamento (operacao_id);
-- Uma máquina e um operador têm no máximo um apontamento aberto por vez.
CREATE UNIQUE INDEX IF NOT EXISTS uk_ind_apontamento_maquina_aberto ON ind_apontamento (maquina_id)
    WHERE status IN ('EM_ANDAMENTO', 'PAUSADO') AND maquina_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ind_apontamento_operador_aberto ON ind_apontamento (operador_id)
    WHERE status IN ('EM_ANDAMENTO', 'PAUSADO');

-- Parada de máquina: durante um apontamento (pausa) ou avulsa (máquina parada sem produção).
CREATE TABLE IF NOT EXISTS ind_parada (
    id                BIGSERIAL    PRIMARY KEY,
    chave             VARCHAR(40)  NOT NULL,
    maquina_id        BIGINT       REFERENCES ind_maquina(id),
    motivo_id         BIGINT       NOT NULL REFERENCES ind_motivo_parada(id),
    apontamento_id    BIGINT       REFERENCES ind_apontamento(id),
    ordem_id          BIGINT       REFERENCES ind_ordem_producao(id),
    inicio            TIMESTAMP    NOT NULL,
    fim               TIMESTAMP,
    observacao        TEXT,
    usuario_id        BIGINT,
    encerrada_por_id  BIGINT,
    CONSTRAINT uk_ind_parada_chave UNIQUE (chave),
    CONSTRAINT ck_ind_parada_fim CHECK (fim IS NULL OR fim >= inicio)
);
CREATE INDEX IF NOT EXISTS ix_ind_parada_maquina_inicio ON ind_parada (maquina_id, inicio);
-- Uma máquina tem no máximo uma parada aberta.
CREATE UNIQUE INDEX IF NOT EXISTS uk_ind_parada_aberta ON ind_parada (maquina_id) WHERE fim IS NULL AND maquina_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_ind_parada_apontamento ON ind_parada (apontamento_id);

-- Consumo real de material da OP (baixa no estoque).
CREATE TABLE IF NOT EXISTS ind_consumo (
    id                BIGSERIAL     PRIMARY KEY,
    chave             VARCHAR(40)   NOT NULL,
    ordem_id          BIGINT        NOT NULL REFERENCES ind_ordem_producao(id),
    produto_id        BIGINT        NOT NULL REFERENCES produtos(id),
    quantidade        NUMERIC(14,4) NOT NULL CHECK (quantidade > 0),
    custo_unitario    NUMERIC(12,4),
    lote_material     VARCHAR(40),
    apontamento_id    BIGINT        REFERENCES ind_apontamento(id),
    observacao        TEXT,
    usuario_id        BIGINT,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    estornado         BOOLEAN       NOT NULL DEFAULT FALSE,
    estornado_em      TIMESTAMP,
    estornado_por_id  BIGINT,
    motivo_estorno    TEXT,
    CONSTRAINT uk_ind_consumo_chave UNIQUE (chave)
);
CREATE INDEX IF NOT EXISTS ix_ind_consumo_ordem ON ind_consumo (ordem_id);

-- Permissão do chão de fábrica: ver a fila, apontar (criar), mexer no apontamento dos outros (editar)
-- e estornar (excluir). SUPER_ADMIN e ADMIN com tudo; os demais começam sem acesso.
INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT p.id, 'IND_APONTAMENTO', TRUE, TRUE, TRUE, TRUE FROM perfis p
WHERE p.nome IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = p.id AND pp.modulo = 'IND_APONTAMENTO');

INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT p.id, 'IND_APONTAMENTO', FALSE, FALSE, FALSE, FALSE FROM perfis p
WHERE p.nome NOT IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = p.id AND pp.modulo = 'IND_APONTAMENTO');
