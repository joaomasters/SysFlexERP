-- PIX cobrado na própria maquininha (Veloce Smart TEF, tipo 3): o QR aparece na
-- tela da máquina e o dinheiro cai na conta da adquirente, como no cartão.
ALTER TABLE terminal_pagamento
    ADD COLUMN IF NOT EXISTS aceita_pix BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE transacao_terminal DROP CONSTRAINT IF EXISTS transacao_terminal_tipo_check;
ALTER TABLE transacao_terminal ADD CONSTRAINT transacao_terminal_tipo_check
    CHECK (tipo IN ('DEBITO', 'CREDITO', 'PIX'));
