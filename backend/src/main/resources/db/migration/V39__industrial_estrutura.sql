-- Módulo industrial, sprint 1: estrutura produtiva (unidades, setores, centros de trabalho,
-- turnos, tipos de máquina com parâmetros próprios, máquinas, ferramentas/matrizes e
-- alocação de operadores). Só tabelas novas: nada do PDV, estoque ou fiscal muda.
-- Cada empresa tem o seu schema, então o isolamento entre empresas vem do próprio schema.

-- Habilitação por empresa, controlada pela plataforma (tela Empresas). Desligado por padrão:
-- quem usa só o PDV não vê nem acessa nada industrial.
ALTER TABLE IF EXISTS public.tenants ADD COLUMN IF NOT EXISTS modulo_industrial BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS ind_unidade (
    id            BIGSERIAL    PRIMARY KEY,
    codigo        VARCHAR(20)  NOT NULL,
    nome          VARCHAR(120) NOT NULL,
    cidade        VARCHAR(60),
    uf            VARCHAR(2),
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_por_id BIGINT       REFERENCES usuarios(id),
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_ind_unidade_codigo UNIQUE (codigo)
);

CREATE TABLE IF NOT EXISTS ind_setor (
    id          BIGSERIAL    PRIMARY KEY,
    unidade_id  BIGINT       NOT NULL REFERENCES ind_unidade(id),
    codigo      VARCHAR(20)  NOT NULL,
    nome        VARCHAR(120) NOT NULL,
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_ind_setor_codigo UNIQUE (unidade_id, codigo)
);

CREATE TABLE IF NOT EXISTS ind_centro_trabalho (
    id          BIGSERIAL     PRIMARY KEY,
    setor_id    BIGINT        NOT NULL REFERENCES ind_setor(id),
    codigo      VARCHAR(20)   NOT NULL,
    nome        VARCHAR(120)  NOT NULL,
    -- Custo/hora do centro (mão de obra + indiretos), base para o custeio das operações
    custo_hora  NUMERIC(14,4) CHECK (custo_hora IS NULL OR custo_hora >= 0),
    ativo       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uk_ind_centro_codigo UNIQUE (codigo)
);
CREATE INDEX IF NOT EXISTS idx_ind_centro_setor ON ind_centro_trabalho (setor_id);

-- Turno: dias_semana tem 7 posições, de segunda (1ª) a domingo (7ª), '1' = trabalha.
-- hora_fim menor que hora_inicio = turno que vira a meia-noite.
CREATE TABLE IF NOT EXISTS ind_turno (
    id           BIGSERIAL   PRIMARY KEY,
    codigo       VARCHAR(20) NOT NULL,
    nome         VARCHAR(60) NOT NULL,
    hora_inicio  TIME        NOT NULL,
    hora_fim     TIME        NOT NULL,
    dias_semana  VARCHAR(7)  NOT NULL DEFAULT '1111100' CHECK (dias_semana ~ '^[01]{7}$' AND dias_semana <> '0000000'),
    ativo        BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_ind_turno_codigo UNIQUE (codigo),
    CONSTRAINT ck_ind_turno_horas CHECK (hora_inicio <> hora_fim)
);

-- Tipo de máquina e os parâmetros técnicos que fazem sentido para ele (um forno tem
-- temperatura máxima; uma prensa, força e diâmetro do recipiente). Nada fixo no código.
CREATE TABLE IF NOT EXISTS ind_tipo_maquina (
    id         BIGSERIAL    PRIMARY KEY,
    codigo     VARCHAR(20)  NOT NULL,
    nome       VARCHAR(100) NOT NULL,
    categoria  VARCHAR(25)  NOT NULL CHECK (categoria IN ('FORNO', 'PRENSA_EXTRUSAO', 'CORTE', 'TRATAMENTO_TERMICO',
                                                           'CNC', 'ACABAMENTO', 'EMBALAGEM', 'OUTRO')),
    ativo      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_ind_tipo_maquina_codigo UNIQUE (codigo)
);

CREATE TABLE IF NOT EXISTS ind_tipo_maquina_parametro (
    id              BIGSERIAL     PRIMARY KEY,
    tipo_maquina_id BIGINT        NOT NULL REFERENCES ind_tipo_maquina(id) ON DELETE CASCADE,
    codigo          VARCHAR(30)   NOT NULL,
    nome            VARCHAR(100)  NOT NULL,
    unidade         VARCHAR(15),
    tipo_valor      VARCHAR(6)    NOT NULL DEFAULT 'NUMERO' CHECK (tipo_valor IN ('NUMERO', 'TEXTO')),
    minimo          NUMERIC(18,6),
    maximo          NUMERIC(18,6),
    obrigatorio     BOOLEAN       NOT NULL DEFAULT FALSE,
    ordem           SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_ind_tipo_param UNIQUE (tipo_maquina_id, codigo),
    CONSTRAINT ck_ind_tipo_param_faixa CHECK (minimo IS NULL OR maximo IS NULL OR minimo <= maximo)
);

CREATE TABLE IF NOT EXISTS ind_maquina (
    id                 BIGSERIAL     PRIMARY KEY,
    codigo             VARCHAR(20)   NOT NULL,
    descricao          VARCHAR(150)  NOT NULL,
    tipo_maquina_id    BIGINT        NOT NULL REFERENCES ind_tipo_maquina(id),
    centro_trabalho_id BIGINT        NOT NULL REFERENCES ind_centro_trabalho(id),
    capacidade         NUMERIC(14,4) CHECK (capacidade IS NULL OR capacidade > 0),
    unidade_capacidade VARCHAR(10)   CHECK (unidade_capacidade IN ('KG_H', 'T_H', 'PECAS_H', 'M_MIN', 'M_H', 'CICLOS_H', 'KG_CARGA')),
    status             VARCHAR(15)   NOT NULL DEFAULT 'DISPONIVEL'
                         CHECK (status IN ('DISPONIVEL', 'EM_OPERACAO', 'PARADA', 'EM_MANUTENCAO', 'INATIVA')),
    custo_hora         NUMERIC(14,4) CHECK (custo_hora IS NULL OR custo_hora >= 0),
    horimetro          NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (horimetro >= 0),
    fabricante         VARCHAR(80),
    modelo             VARCHAR(80),
    numero_serie       VARCHAR(60),
    ano_fabricacao     SMALLINT      CHECK (ano_fabricacao IS NULL OR ano_fabricacao BETWEEN 1900 AND 2100),
    observacao         TEXT,
    versao             BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMP     NOT NULL DEFAULT now(),
    criado_por_id      BIGINT        REFERENCES usuarios(id),
    CONSTRAINT uk_ind_maquina_codigo UNIQUE (codigo),
    CONSTRAINT ck_ind_maquina_capacidade CHECK ((capacidade IS NULL) = (unidade_capacidade IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_ind_maquina_centro ON ind_maquina (centro_trabalho_id);
CREATE INDEX IF NOT EXISTS idx_ind_maquina_status ON ind_maquina (status);

-- Limitações técnicas da máquina: valores dos parâmetros do tipo dela
CREATE TABLE IF NOT EXISTS ind_maquina_parametro (
    id            BIGSERIAL     PRIMARY KEY,
    maquina_id    BIGINT        NOT NULL REFERENCES ind_maquina(id) ON DELETE CASCADE,
    parametro_id  BIGINT        NOT NULL REFERENCES ind_tipo_maquina_parametro(id),
    valor_numero  NUMERIC(18,6),
    valor_texto   VARCHAR(200),
    CONSTRAINT uk_ind_maquina_param UNIQUE (maquina_id, parametro_id),
    CONSTRAINT ck_ind_maquina_param_valor CHECK (valor_numero IS NOT NULL OR valor_texto IS NOT NULL)
);

-- Calendário: em quais turnos a máquina trabalha
CREATE TABLE IF NOT EXISTS ind_maquina_turno (
    maquina_id BIGINT NOT NULL REFERENCES ind_maquina(id) ON DELETE CASCADE,
    turno_id   BIGINT NOT NULL REFERENCES ind_turno(id),
    PRIMARY KEY (maquina_id, turno_id)
);

-- Histórico operacional e de manutenção. Nunca é editado nem apagado.
CREATE TABLE IF NOT EXISTS ind_maquina_evento (
    id              BIGSERIAL     PRIMARY KEY,
    maquina_id      BIGINT        NOT NULL REFERENCES ind_maquina(id),
    tipo            VARCHAR(25)   NOT NULL CHECK (tipo IN ('STATUS', 'MANUTENCAO_PREVENTIVA', 'MANUTENCAO_CORRETIVA',
                                                           'HORIMETRO', 'OBSERVACAO')),
    status_anterior VARCHAR(15),
    status_novo     VARCHAR(15),
    horimetro       NUMERIC(12,2),
    custo           NUMERIC(14,2) CHECK (custo IS NULL OR custo >= 0),
    inicio          TIMESTAMP,
    fim             TIMESTAMP,
    descricao       TEXT          NOT NULL,
    usuario_id      BIGINT        REFERENCES usuarios(id),
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT ck_ind_evento_periodo CHECK (fim IS NULL OR inicio IS NULL OR fim >= inicio)
);
CREATE INDEX IF NOT EXISTS idx_ind_evento_maquina ON ind_maquina_evento (maquina_id, created_at DESC);

-- Ferramenta, matriz ou dispositivo. A matriz de extrusão tem revisão e vida útil em ciclos.
CREATE TABLE IF NOT EXISTS ind_ferramenta (
    id               BIGSERIAL     PRIMARY KEY,
    codigo           VARCHAR(30)   NOT NULL,
    descricao        VARCHAR(150)  NOT NULL,
    tipo             VARCHAR(20)   NOT NULL CHECK (tipo IN ('MATRIZ', 'DISPOSITIVO', 'FERRAMENTA_CORTE', 'OUTRO')),
    revisao          VARCHAR(10),
    tipo_maquina_id  BIGINT        REFERENCES ind_tipo_maquina(id),
    vida_util_ciclos INTEGER       CHECK (vida_util_ciclos IS NULL OR vida_util_ciclos > 0),
    ciclos_utilizados INTEGER      NOT NULL DEFAULT 0 CHECK (ciclos_utilizados >= 0),
    status           VARCHAR(15)   NOT NULL DEFAULT 'DISPONIVEL'
                       CHECK (status IN ('DISPONIVEL', 'EM_USO', 'EM_MANUTENCAO', 'DESCARTADA')),
    custo_aquisicao  NUMERIC(14,2) CHECK (custo_aquisicao IS NULL OR custo_aquisicao >= 0),
    observacao       TEXT,
    created_at       TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uk_ind_ferramenta_codigo_rev UNIQUE (codigo, revisao)
);

-- Operador alocado a um centro de trabalho em um turno, com vigência
CREATE TABLE IF NOT EXISTS ind_alocacao_operador (
    id                 BIGSERIAL   PRIMARY KEY,
    usuario_id         BIGINT      NOT NULL REFERENCES usuarios(id),
    centro_trabalho_id BIGINT      NOT NULL REFERENCES ind_centro_trabalho(id),
    turno_id           BIGINT      NOT NULL REFERENCES ind_turno(id),
    funcao             VARCHAR(60),
    vigente_de         DATE        NOT NULL,
    vigente_ate        DATE,
    created_at         TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT ck_ind_alocacao_vigencia CHECK (vigente_ate IS NULL OR vigente_ate >= vigente_de)
);
CREATE INDEX IF NOT EXISTS idx_ind_alocacao_usuario ON ind_alocacao_operador (usuario_id);

-- Permissão do novo módulo: ADMIN e SUPER_ADMIN com tudo, demais perfis sem acesso
INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT id, 'IND_ESTRUTURA', TRUE, TRUE, TRUE, TRUE FROM perfis WHERE nome IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = perfis.id AND pp.modulo = 'IND_ESTRUTURA');

INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT id, 'IND_ESTRUTURA', FALSE, FALSE, FALSE, FALSE FROM perfis WHERE nome NOT IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = perfis.id AND pp.modulo = 'IND_ESTRUTURA');
