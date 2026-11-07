-- Voucher (vale-alimentação / vale-refeição: Alelo, Pluxee, Ticket, VR) cobrado na
-- maquininha. Na Veloce Smart TEF é o tipo 2; o cartão de vale não passa como débito.
-- pagamentos_venda.forma_pagamento já aceita 'VOUCHER' desde a V24.
ALTER TABLE terminal_pagamento
    ADD COLUMN IF NOT EXISTS aceita_voucher BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE transacao_terminal DROP CONSTRAINT IF EXISTS transacao_terminal_tipo_check;
ALTER TABLE transacao_terminal ADD CONSTRAINT transacao_terminal_tipo_check
    CHECK (tipo IN ('DEBITO', 'CREDITO', 'PIX', 'VOUCHER'));
