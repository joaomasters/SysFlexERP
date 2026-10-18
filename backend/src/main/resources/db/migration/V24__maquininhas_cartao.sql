-- Maquininhas de cartão (Stone, Mercado Pago, Sicredi).
--
-- Três peças:
--   pagamento_provedor_config  credenciais de cada adquirente, POR EMPRESA (cada
--                              tenant tem seu schema, então cada empresa tem as
--                              próprias). Segredos ficam criptografados (AES-GCM)
--                              com a chave PAGAMENTO_CRIPTO_CHAVE — nunca em claro.
--   terminal_pagamento         cada maquininha física e como o PDV fala com ela.
--   transacao_terminal         toda cobrança enviada a uma maquininha, aprovada
--                              ou não. O fechamento da venda só aceita cartão
--                              "integrado" se apontar para uma transação APROVADA.

CREATE TABLE pagamento_provedor_config (
    provedor               VARCHAR(20)  PRIMARY KEY
                             CHECK (provedor IN ('STONE', 'MERCADO_PAGO', 'SICREDI')),
    ativo                  BOOLEAN      NOT NULL DEFAULT FALSE,
    ambiente               VARCHAR(10)  NOT NULL DEFAULT 'SANDBOX'
                             CHECK (ambiente IN ('SANDBOX', 'PRODUCAO')),
    -- Mercado Pago: Access Token | Stone: Secret Key (sk_...) da conta Pagar.me/Stone Connect
    access_token_cripto    TEXT,
    -- Mercado Pago: "assinatura secreta" do webhook (valida o header x-signature)
    webhook_secret_cripto  TEXT,
    -- Identificação do estabelecimento na adquirente (Stone Code, código Sicredi...). Informativo.
    codigo_estabelecimento VARCHAR(100),
    observacoes            TEXT,
    updated_at             TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by             BIGINT       REFERENCES usuarios(id)
);

INSERT INTO pagamento_provedor_config (provedor) VALUES ('STONE'), ('MERCADO_PAGO'), ('SICREDI');

CREATE TABLE terminal_pagamento (
    id                        BIGSERIAL    PRIMARY KEY,
    nome                      VARCHAR(80)  NOT NULL,
    provedor                  VARCHAR(20)  NOT NULL REFERENCES pagamento_provedor_config(provedor),
    -- API_NUVEM:    o servidor cobra direto na API da adquirente (MP Point, Stone Connect)
    -- AGENTE_LOCAL: o navegador do caixa chama um agente TEF instalado no computador
    -- MANUAL:       maquininha não integrada; operador digita NSU/autorização
    modo_integracao           VARCHAR(20)  NOT NULL
                                CHECK (modo_integracao IN ('API_NUVEM', 'AGENTE_LOCAL', 'MANUAL')),
    -- MP: device_id (ex: PAX_A910__SMARTPOS1234567) | Stone Connect: número de série da POS
    identificador_dispositivo VARCHAR(100),
    agente_url                VARCHAR(200),
    aceita_debito             BOOLEAN      NOT NULL DEFAULT TRUE,
    aceita_credito            BOOLEAN      NOT NULL DEFAULT TRUE,
    max_parcelas              INTEGER      NOT NULL DEFAULT 12 CHECK (max_parcelas BETWEEN 1 AND 18),
    ativo                     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                TIMESTAMP    NOT NULL DEFAULT now(),
    CHECK (modo_integracao <> 'API_NUVEM'    OR identificador_dispositivo IS NOT NULL),
    CHECK (modo_integracao <> 'AGENTE_LOCAL' OR agente_url IS NOT NULL),
    CHECK (aceita_debito OR aceita_credito)
);

CREATE TABLE transacao_terminal (
    id               BIGSERIAL     PRIMARY KEY,
    terminal_id      BIGINT        NOT NULL REFERENCES terminal_pagamento(id),
    venda_id         BIGINT        REFERENCES vendas(id),
    provedor         VARCHAR(20)   NOT NULL,
    modo_integracao  VARCHAR(20)   NOT NULL,
    tipo             VARCHAR(10)   NOT NULL CHECK (tipo IN ('DEBITO', 'CREDITO')),
    parcelas         INTEGER       NOT NULL DEFAULT 1 CHECK (parcelas >= 1),
    valor            NUMERIC(12,2) NOT NULL CHECK (valor > 0),
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDENTE'
                       CHECK (status IN ('PENDENTE', 'APROVADA', 'NEGADA', 'CANCELADA', 'ERRO')),
    id_externo       VARCHAR(100),          -- id da cobrança na adquirente (payment intent / order)
    nsu              VARCHAR(50),
    autorizacao      VARCHAR(50),
    bandeira         VARCHAR(30),
    mensagem         TEXT,
    resposta_bruta   TEXT,                  -- último JSON da adquirente/agente, para suporte
    usuario_id       BIGINT        REFERENCES usuarios(id),
    created_at       TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT now(),
    finalizada_em    TIMESTAMP
);

CREATE INDEX idx_transacao_terminal_externo ON transacao_terminal(id_externo);
CREATE INDEX idx_transacao_terminal_venda   ON transacao_terminal(venda_id);
CREATE INDEX idx_transacao_terminal_status  ON transacao_terminal(status) WHERE status = 'PENDENTE';

-- Pagamentos da venda: guardam o comprovante do cartão e os dados de cheque,
-- que a tela já enviava mas o backend descartava.
ALTER TABLE pagamentos_venda
    ALTER COLUMN nsu         TYPE VARCHAR(50),
    ALTER COLUMN autorizacao TYPE VARCHAR(50),
    ADD COLUMN parcelas              INTEGER,
    ADD COLUMN bandeira              VARCHAR(30),
    ADD COLUMN provedor              VARCHAR(20),
    ADD COLUMN transacao_terminal_id BIGINT UNIQUE REFERENCES transacao_terminal(id),
    ADD COLUMN referencia_externa    VARCHAR(100),   -- ex: id do pagamento PIX no Mercado Pago
    ADD COLUMN cheque_banco          VARCHAR(60),
    ADD COLUMN cheque_numero         VARCHAR(30),
    ADD COLUMN cheque_titular        VARCHAR(150);

-- CHEQUE existia na tela de pagamento mas não no CHECK original (V1):
-- fechar venda com cheque estourava erro de constraint.
ALTER TABLE pagamentos_venda DROP CONSTRAINT IF EXISTS pagamentos_venda_forma_pagamento_check;
ALTER TABLE pagamentos_venda ADD CONSTRAINT pagamentos_venda_forma_pagamento_check
    CHECK (forma_pagamento IN ('DINHEIRO','CREDITO','DEBITO','PIX','FIADO','CONVENIO','VOUCHER','CHEQUE'));

-- Novo módulo MAQUININHAS na matriz de permissões: configurar credenciais e
-- terminais é coisa de administrador. Operar a maquininha no PDV usa a
-- permissão do próprio PDV.
INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT id, 'MAQUININHAS', TRUE, TRUE, TRUE, TRUE FROM perfis WHERE nome IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = perfis.id AND pp.modulo = 'MAQUININHAS');

INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT id, 'MAQUININHAS', FALSE, FALSE, FALSE, FALSE FROM perfis
WHERE nome NOT IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = perfis.id AND pp.modulo = 'MAQUININHAS');
