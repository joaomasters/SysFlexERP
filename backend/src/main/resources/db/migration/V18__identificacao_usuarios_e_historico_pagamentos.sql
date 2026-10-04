-- Identificação de usuários nas telas operacionais + histórico de pagamentos
-- parciais (Contas a Receber / Contas a Pagar).
--
-- Quem pode VER esses dados é decidido no código (só administradores — ver
-- UsuarioAutenticado#podeVerIdentificacaoUsuarios); aqui só guardamos QUEM fez.
--
-- Sem backfill de usuário: registros antigos ficam com NULL ("não
-- identificado"). Inventário, Perdas e Desossa já tinham usuario_id, mas
-- a tela mandava sempre o valor fixo 1 — não é confiável como autoria.

-- Quem fez cada ação
ALTER TABLE recebimento_mercadoria
    ADD COLUMN usuario_id BIGINT REFERENCES usuarios(id);

-- NF de saída: quem lançou a nota e quem efetivou a saída (EMITIDA = baixa de estoque).
ALTER TABLE nota_fiscal_saida
    ADD COLUMN usuario_criacao_id BIGINT REFERENCES usuarios(id),
    ADD COLUMN usuario_emissao_id BIGINT REFERENCES usuarios(id);

ALTER TABLE clientes
    ADD COLUMN criado_por_id BIGINT REFERENCES usuarios(id);

-- Inventário: usuario_id (já existente) = quem abriu; fechado_por_id = quem
-- finalizou OU cancelou (o status diferencia os dois casos).
ALTER TABLE inventario_fisico
    ADD COLUMN fechado_por_id BIGINT REFERENCES usuarios(id);

-- Histórico de pagamentos — Contas a Receber
-- Uma linha por recebimento (parcial ou total). Imutável: nunca se edita
-- nem apaga, só se acrescenta. saldo_anterior/saldo_posterior são
-- gravados no momento do recebimento (não recalculados depois).
--
-- origem:
-- PAGAMENTO     - recebimento registrado por um usuário (usuario_id preenchido)
-- MIGRACAO      - valor já recebido ANTES deste histórico existir (1 linha agregada por conta)
-- TRANSFERENCIA - pagamentos parciais de contas de fiado que foram agrupadas
-- num fechamento (Faturamento) e vieram "de carona" para a conta consolidada
CREATE TABLE contas_a_receber_pagamento (
    id              BIGSERIAL     PRIMARY KEY,
    conta_id        BIGINT        NOT NULL REFERENCES contas_a_receber(id),
    data_pagamento  TIMESTAMP     NOT NULL,
    valor           NUMERIC(12,2) NOT NULL CHECK (valor > 0),
    saldo_anterior  NUMERIC(12,2) NOT NULL,
    saldo_posterior NUMERIC(12,2) NOT NULL,
    usuario_id      BIGINT        REFERENCES usuarios(id),
    origem          VARCHAR(20)   NOT NULL DEFAULT 'PAGAMENTO'
                      CHECK (origem IN ('PAGAMENTO', 'MIGRACAO', 'TRANSFERENCIA'))
);

CREATE INDEX idx_receber_pagamento_conta ON contas_a_receber_pagamento(conta_id, data_pagamento);
CREATE INDEX idx_receber_pagamento_data  ON contas_a_receber_pagamento(data_pagamento);

-- Histórico de pagamentos — Contas a Pagar (mesma lógica)
CREATE TABLE contas_pagar_pagamento (
    id              BIGSERIAL     PRIMARY KEY,
    conta_id        BIGINT        NOT NULL REFERENCES contas_pagar(id),
    data_pagamento  TIMESTAMP     NOT NULL,
    valor           NUMERIC(12,2) NOT NULL CHECK (valor > 0),
    saldo_anterior  NUMERIC(12,2) NOT NULL,
    saldo_posterior NUMERIC(12,2) NOT NULL,
    usuario_id      BIGINT        REFERENCES usuarios(id),
    origem          VARCHAR(20)   NOT NULL DEFAULT 'PAGAMENTO'
                      CHECK (origem IN ('PAGAMENTO', 'MIGRACAO'))
);

CREATE INDEX idx_pagar_pagamento_conta ON contas_pagar_pagamento(conta_id, data_pagamento);
CREATE INDEX idx_pagar_pagamento_data  ON contas_pagar_pagamento(data_pagamento);

-- Backfill: contas que já tinham valor pago antes deste histórico ganham
-- UMA linha agregada (origem MIGRACAO, sem usuário), para que
-- SUM(histórico) == valor_pago continue valendo para todas as contas.
-- Não dá para reconstruir os pagamentos parciais individuais — nunca
-- foram registrados.
INSERT INTO contas_a_receber_pagamento
    (conta_id, data_pagamento, valor, saldo_anterior, saldo_posterior, usuario_id, origem)
SELECT id,
       COALESCE(data_pagamento::timestamp, created_at, NOW()),
       valor_pago,
       valor,
       valor - valor_pago,
       NULL,
       'MIGRACAO'
FROM contas_a_receber
WHERE COALESCE(valor_pago, 0) > 0;

INSERT INTO contas_pagar_pagamento
    (conta_id, data_pagamento, valor, saldo_anterior, saldo_posterior, usuario_id, origem)
SELECT id,
       COALESCE(data_pagamento::timestamp, created_at, NOW()),
       valor_pago,
       valor,
       valor - valor_pago,
       NULL,
       'MIGRACAO'
FROM contas_pagar
WHERE COALESCE(valor_pago, 0) > 0;
