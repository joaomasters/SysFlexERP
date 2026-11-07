-- Módulo fiscal: NFC-e (modelo 65) e NF-e (modelo 55), começando pelo Paraná.
-- O cupom não fiscal do PDV continua sendo o padrão: a emissão é uma opção por empresa,
-- desligada até a configuração estar completa e haver um provedor de comunicação com a SEFAZ.

-- Uma linha por empresa (id = 1), como configuracao_comprovante
CREATE TABLE IF NOT EXISTS configuracao_fiscal (
    id                       SMALLINT     PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    emitir_nfce              BOOLEAN      NOT NULL DEFAULT FALSE,
    emitir_nfe               BOOLEAN      NOT NULL DEFAULT FALSE,
    ambiente                 VARCHAR(12)  NOT NULL DEFAULT 'HOMOLOGACAO' CHECK (ambiente IN ('HOMOLOGACAO', 'PRODUCAO')),
    uf                       VARCHAR(2)   NOT NULL DEFAULT 'PR' CHECK (uf IN ('PR')),
    provedor                 VARCHAR(30)  NOT NULL DEFAULT 'NENHUM',
    -- Emitente
    razao_social             VARCHAR(150),
    nome_fantasia            VARCHAR(120),
    cnpj                     VARCHAR(14),
    inscricao_estadual       VARCHAR(14),
    -- 1 Simples Nacional · 2 Simples, excesso de sublimite · 3 Regime normal · 4 MEI
    regime_tributario        SMALLINT     NOT NULL DEFAULT 1 CHECK (regime_tributario BETWEEN 1 AND 4),
    logradouro               VARCHAR(120),
    numero_endereco          VARCHAR(10),
    complemento              VARCHAR(60),
    bairro                   VARCHAR(60),
    codigo_municipio         VARCHAR(7),   -- código IBGE (PR começa com 41)
    municipio                VARCHAR(60),
    cep                      VARCHAR(8),
    telefone                 VARCHAR(14),
    -- Numeração: o próximo número de cada modelo, por série
    nfce_serie               SMALLINT     NOT NULL DEFAULT 1 CHECK (nfce_serie BETWEEN 0 AND 999),
    nfce_proximo_numero      INTEGER      NOT NULL DEFAULT 1 CHECK (nfce_proximo_numero BETWEEN 1 AND 999999999),
    nfe_serie                SMALLINT     NOT NULL DEFAULT 1 CHECK (nfe_serie BETWEEN 0 AND 999),
    nfe_proximo_numero       INTEGER      NOT NULL DEFAULT 1 CHECK (nfe_proximo_numero BETWEEN 1 AND 999999999),
    -- NFC-e: código de segurança do contribuinte (QR Code), gerado no portal da SEFAZ-PR
    csc_id                   VARCHAR(6),
    csc_token_cripto         TEXT,
    -- Certificado digital A1 (.pfx) e a senha: só cifrados (PAGAMENTO_CRIPTO_CHAVE)
    certificado_cripto       TEXT,
    certificado_senha_cripto TEXT,
    certificado_titular      VARCHAR(200),
    certificado_cnpj         VARCHAR(14),
    certificado_validade     TIMESTAMP,
    updated_at               TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by               BIGINT       REFERENCES usuarios(id)
);

INSERT INTO configuracao_fiscal (id, nome_fantasia)
SELECT 1, COALESCE((SELECT nome FROM public.tenants WHERE schema_name = current_schema()), NULL)
WHERE NOT EXISTS (SELECT 1 FROM configuracao_fiscal);

-- Cada NFC-e ou NF-e emitida (ou tentada). Número + série + modelo nunca se repetem.
CREATE TABLE IF NOT EXISTS documento_fiscal (
    id                     BIGSERIAL     PRIMARY KEY,
    modelo                 SMALLINT      NOT NULL CHECK (modelo IN (55, 65)),
    serie                  SMALLINT      NOT NULL,
    numero                 INTEGER       NOT NULL,
    ambiente               VARCHAR(12)   NOT NULL CHECK (ambiente IN ('HOMOLOGACAO', 'PRODUCAO')),
    status                 VARCHAR(15)   NOT NULL DEFAULT 'PENDENTE'
                             CHECK (status IN ('PENDENTE', 'AUTORIZADA', 'REJEITADA', 'DENEGADA',
                                               'CANCELADA', 'CONTINGENCIA', 'INUTILIZADA', 'ERRO')),
    chave                  VARCHAR(44),
    protocolo              VARCHAR(20),
    mensagem               TEXT,          -- motivo da SEFAZ (rejeição, cancelamento...)
    valor_total            NUMERIC(12,2) NOT NULL DEFAULT 0,
    venda_id               BIGINT        REFERENCES vendas(id),
    nota_fiscal_saida_id   BIGINT        REFERENCES nota_fiscal_saida(id),
    destinatario_documento VARCHAR(14),   -- CPF/CNPJ (NFC-e: opcional; NF-e: obrigatório)
    destinatario_nome      VARCHAR(150),
    xml                    TEXT,
    provedor               VARCHAR(30),
    id_externo             VARCHAR(100),  -- id da nota no provedor
    usuario_id             BIGINT        REFERENCES usuarios(id),
    emitida_em             TIMESTAMP,
    cancelada_em           TIMESTAMP,
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT now(),
    UNIQUE (modelo, serie, numero, ambiente)
);

CREATE INDEX IF NOT EXISTS idx_documento_fiscal_venda  ON documento_fiscal(venda_id);
CREATE INDEX IF NOT EXISTS idx_documento_fiscal_status ON documento_fiscal(status, created_at);

-- Permissão do novo módulo: só administradores configuram e consultam por padrão
INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT id, 'FISCAL', TRUE, TRUE, TRUE, TRUE FROM perfis WHERE nome IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = perfis.id AND pp.modulo = 'FISCAL');

INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT id, 'FISCAL', FALSE, FALSE, FALSE, FALSE FROM perfis
WHERE nome NOT IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = perfis.id AND pp.modulo = 'FISCAL');
