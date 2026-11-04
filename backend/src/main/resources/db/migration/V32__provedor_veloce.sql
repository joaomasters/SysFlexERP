-- Veloce Smart TEF: app instalado na maquininha (ex.: Máquina Smart Sicredi,
-- Ingenico DX8000 com M-SiTef) que recebe a cobrança pela API da Veloce.
-- Ver VeloceSmartTefProvedor e docs/MAQUININHAS.md.
ALTER TABLE pagamento_provedor_config DROP CONSTRAINT IF EXISTS pagamento_provedor_config_provedor_check;
ALTER TABLE pagamento_provedor_config ADD CONSTRAINT pagamento_provedor_config_provedor_check
    CHECK (provedor IN ('STONE', 'MERCADO_PAGO', 'SICREDI', 'VELOCE'));

INSERT INTO pagamento_provedor_config (provedor) VALUES ('VELOCE') ON CONFLICT (provedor) DO NOTHING;
