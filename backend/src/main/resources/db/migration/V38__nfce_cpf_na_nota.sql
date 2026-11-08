-- NFC-e no PDV ("CPF na nota?"). Por padrão só sai quando o cliente pede (SOB_DEMANDA);
-- TODAS emite em toda venda, com ou sem CPF. O cupom não fiscal continua para as outras vendas.
ALTER TABLE configuracao_fiscal
    ADD COLUMN IF NOT EXISTS nfce_modo VARCHAR(15) NOT NULL DEFAULT 'SOB_DEMANDA'
        CHECK (nfce_modo IN ('SOB_DEMANDA', 'TODAS'));

-- Endereços que vão impressos no DANFE NFC-e (devolvidos pelo provedor na autorização)
ALTER TABLE documento_fiscal
    ADD COLUMN IF NOT EXISTS url_qrcode   TEXT,
    ADD COLUMN IF NOT EXISTS url_consulta VARCHAR(200);
