-- Módulos industriais, sprint 2: engenharia de produto (ficha técnica, estrutura/BOM e roteiro,
-- ambos versionados) e ordens de produção com a revisão de engenharia congelada na liberação.
-- Só cria tabelas: nada existente é alterado. Produto continua sendo o cadastro único (tabela produtos).

-- Ficha técnica industrial: estende o produto sem mexer na tabela produtos.
CREATE TABLE IF NOT EXISTS ind_ficha_tecnica (
    id                   BIGSERIAL     PRIMARY KEY,
    produto_id           BIGINT        NOT NULL REFERENCES produtos(id),
    tipo_item            VARCHAR(15)   NOT NULL CHECK (tipo_item IN ('MATERIA_PRIMA', 'SEMIACABADO', 'ACABADO', 'EMBALAGEM', 'INSUMO')),
    codigo_desenho       VARCHAR(40),
    revisao_desenho      VARCHAR(10),
    peso_unitario_kg     NUMERIC(14,6) CHECK (peso_unitario_kg IS NULL OR peso_unitario_kg > 0),
    peso_linear_kg_m     NUMERIC(14,6) CHECK (peso_linear_kg_m IS NULL OR peso_linear_kg_m > 0),
    comprimento_mm       NUMERIC(12,3) CHECK (comprimento_mm IS NULL OR comprimento_mm > 0),
    lote_minimo          NUMERIC(14,4) CHECK (lote_minimo IS NULL OR lote_minimo > 0),
    lote_multiplo        NUMERIC(14,4) CHECK (lote_multiplo IS NULL OR lote_multiplo > 0),
    prazo_producao_dias  INTEGER       CHECK (prazo_producao_dias IS NULL OR prazo_producao_dias >= 0),
    controla_lote        BOOLEAN       NOT NULL DEFAULT TRUE,
    observacao           TEXT,
    updated_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ind_ficha_produto UNIQUE (produto_id)
);

-- Estrutura de produto (BOM). Cada versão é imutável depois de ativada.
CREATE TABLE IF NOT EXISTS ind_bom (
    id                BIGSERIAL     PRIMARY KEY,
    produto_id        BIGINT        NOT NULL REFERENCES produtos(id),
    versao            INTEGER       NOT NULL CHECK (versao > 0),
    status            VARCHAR(10)   NOT NULL DEFAULT 'RASCUNHO' CHECK (status IN ('RASCUNHO', 'ATIVA', 'OBSOLETA')),
    quantidade_base   NUMERIC(14,4) NOT NULL CHECK (quantidade_base > 0),
    vigencia_inicio   DATE,
    vigencia_fim      DATE,
    observacao        TEXT,
    ativada_por_id    BIGINT,
    ativada_em        TIMESTAMP,
    criado_por_id     BIGINT,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ind_bom_versao UNIQUE (produto_id, versao),
    CONSTRAINT ck_ind_bom_vigencia CHECK (vigencia_fim IS NULL OR vigencia_inicio IS NULL OR vigencia_fim >= vigencia_inicio)
);
-- No máximo uma versão ativa por produto.
CREATE UNIQUE INDEX IF NOT EXISTS uk_ind_bom_ativa ON ind_bom (produto_id) WHERE status = 'ATIVA';

CREATE TABLE IF NOT EXISTS ind_bom_item (
    id                BIGSERIAL     PRIMARY KEY,
    bom_id            BIGINT        NOT NULL REFERENCES ind_bom(id) ON DELETE CASCADE,
    sequencia         INTEGER       NOT NULL,
    componente_id     BIGINT        NOT NULL REFERENCES produtos(id),
    quantidade        NUMERIC(14,6) NOT NULL CHECK (quantidade > 0),
    perda_percentual  NUMERIC(5,2)  NOT NULL DEFAULT 0 CHECK (perda_percentual >= 0 AND perda_percentual < 100),
    observacao        VARCHAR(200),
    CONSTRAINT uk_ind_bom_item_seq UNIQUE (bom_id, sequencia) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX IF NOT EXISTS ix_ind_bom_item_componente ON ind_bom_item (componente_id);

-- Roteiro de fabricação, versionado como a BOM.
CREATE TABLE IF NOT EXISTS ind_roteiro (
    id                BIGSERIAL     PRIMARY KEY,
    produto_id        BIGINT        NOT NULL REFERENCES produtos(id),
    versao            INTEGER       NOT NULL CHECK (versao > 0),
    status            VARCHAR(10)   NOT NULL DEFAULT 'RASCUNHO' CHECK (status IN ('RASCUNHO', 'ATIVO', 'OBSOLETO')),
    vigencia_inicio   DATE,
    vigencia_fim      DATE,
    observacao        TEXT,
    ativado_por_id    BIGINT,
    ativado_em        TIMESTAMP,
    criado_por_id     BIGINT,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ind_roteiro_versao UNIQUE (produto_id, versao),
    CONSTRAINT ck_ind_roteiro_vigencia CHECK (vigencia_fim IS NULL OR vigencia_inicio IS NULL OR vigencia_fim >= vigencia_inicio)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ind_roteiro_ativo ON ind_roteiro (produto_id) WHERE status = 'ATIVO';

CREATE TABLE IF NOT EXISTS ind_roteiro_operacao (
    id                      BIGSERIAL     PRIMARY KEY,
    roteiro_id              BIGINT        NOT NULL REFERENCES ind_roteiro(id) ON DELETE CASCADE,
    sequencia               INTEGER       NOT NULL CHECK (sequencia > 0),
    descricao               VARCHAR(120)  NOT NULL,
    centro_trabalho_id      BIGINT        NOT NULL REFERENCES ind_centro_trabalho(id),
    tipo_maquina_id         BIGINT        REFERENCES ind_tipo_maquina(id),
    tempo_setup_min         NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (tempo_setup_min >= 0),
    tempo_por_unidade_min   NUMERIC(12,6) NOT NULL DEFAULT 0 CHECK (tempo_por_unidade_min >= 0),
    instrucoes              TEXT,
    CONSTRAINT uk_ind_roteiro_op_seq UNIQUE (roteiro_id, sequencia) DEFERRABLE INITIALLY DEFERRED
);

-- Ordem de produção
CREATE SEQUENCE IF NOT EXISTS ind_op_numero_seq START 1;

CREATE TABLE IF NOT EXISTS ind_ordem_producao (
    id                    BIGSERIAL     PRIMARY KEY,
    numero                VARCHAR(20)   NOT NULL,
    produto_id            BIGINT        NOT NULL REFERENCES produtos(id),
    quantidade            NUMERIC(14,4) NOT NULL CHECK (quantidade > 0),
    status                VARCHAR(12)   NOT NULL DEFAULT 'PLANEJADA'
                          CHECK (status IN ('PLANEJADA', 'LIBERADA', 'EM_PRODUCAO', 'PAUSADA', 'CONCLUIDA', 'ENCERRADA', 'CANCELADA')),
    prioridade            SMALLINT      NOT NULL DEFAULT 3 CHECK (prioridade BETWEEN 1 AND 5),
    inicio_previsto       DATE          NOT NULL,
    fim_previsto          DATE          NOT NULL,
    cliente_id            BIGINT        REFERENCES clientes(id),
    pedido_referencia     VARCHAR(40),
    observacao            TEXT,
    bom_id                BIGINT        REFERENCES ind_bom(id),
    roteiro_id            BIGINT        REFERENCES ind_roteiro(id),
    liberada_em           TIMESTAMP,
    liberada_por_id       BIGINT,
    versao                BIGINT        NOT NULL DEFAULT 0,
    criado_por_id         BIGINT,
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ind_op_numero UNIQUE (numero),
    CONSTRAINT ck_ind_op_datas CHECK (fim_previsto >= inicio_previsto)
);
CREATE INDEX IF NOT EXISTS ix_ind_op_status ON ind_ordem_producao (status);
CREATE INDEX IF NOT EXISTS ix_ind_op_produto ON ind_ordem_producao (produto_id);

-- Materiais e operações copiados da BOM e do roteiro na liberação (a revisão fica congelada na OP).
CREATE TABLE IF NOT EXISTS ind_op_material (
    id                    BIGSERIAL     PRIMARY KEY,
    ordem_id              BIGINT        NOT NULL REFERENCES ind_ordem_producao(id) ON DELETE CASCADE,
    sequencia             INTEGER       NOT NULL,
    produto_id            BIGINT        NOT NULL REFERENCES produtos(id),
    quantidade_liquida    NUMERIC(16,6) NOT NULL CHECK (quantidade_liquida > 0),
    perda_percentual      NUMERIC(5,2)  NOT NULL DEFAULT 0,
    quantidade_prevista   NUMERIC(16,6) NOT NULL CHECK (quantidade_prevista > 0),
    unidade               VARCHAR(5)    NOT NULL
);

CREATE TABLE IF NOT EXISTS ind_op_operacao (
    id                    BIGSERIAL     PRIMARY KEY,
    ordem_id              BIGINT        NOT NULL REFERENCES ind_ordem_producao(id) ON DELETE CASCADE,
    sequencia             INTEGER       NOT NULL,
    descricao             VARCHAR(120)  NOT NULL,
    centro_trabalho_id    BIGINT        NOT NULL REFERENCES ind_centro_trabalho(id),
    tipo_maquina_id       BIGINT        REFERENCES ind_tipo_maquina(id),
    maquina_id            BIGINT        REFERENCES ind_maquina(id),
    tempo_setup_min       NUMERIC(10,2) NOT NULL DEFAULT 0,
    tempo_previsto_min    NUMERIC(14,2) NOT NULL DEFAULT 0,
    instrucoes            TEXT,
    status                VARCHAR(12)   NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE', 'EM_EXECUCAO', 'CONCLUIDA')),
    CONSTRAINT uk_ind_op_operacao_seq UNIQUE (ordem_id, sequencia)
);
CREATE INDEX IF NOT EXISTS ix_ind_op_operacao_centro ON ind_op_operacao (centro_trabalho_id);

CREATE TABLE IF NOT EXISTS ind_op_evento (
    id               BIGSERIAL    PRIMARY KEY,
    ordem_id         BIGINT       NOT NULL REFERENCES ind_ordem_producao(id) ON DELETE CASCADE,
    status_anterior  VARCHAR(12),
    status_novo      VARCHAR(12)  NOT NULL,
    motivo           TEXT,
    usuario_id       BIGINT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_ind_op_evento_ordem ON ind_op_evento (ordem_id);

-- Permissões: engenharia e PCP liberados para SUPER_ADMIN e ADMIN; demais perfis começam sem acesso.
INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT p.id, m.modulo, TRUE, TRUE, TRUE, TRUE
FROM perfis p CROSS JOIN (VALUES ('IND_ENGENHARIA'), ('IND_PCP')) AS m(modulo)
WHERE p.nome IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = p.id AND pp.modulo = m.modulo);

INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT p.id, m.modulo, FALSE, FALSE, FALSE, FALSE
FROM perfis p CROSS JOIN (VALUES ('IND_ENGENHARIA'), ('IND_PCP')) AS m(modulo)
WHERE p.nome NOT IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = p.id AND pp.modulo = m.modulo);
