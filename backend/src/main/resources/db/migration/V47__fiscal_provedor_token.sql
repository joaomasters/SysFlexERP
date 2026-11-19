-- Provedor fiscal de terceiro (API, ex.: Focus NFe): token de acesso por ambiente, guardado cifrado
-- (CredencialCripto, como o CSC e o certificado). Nunca volta pela API, só "configurado: sim/não".
ALTER TABLE configuracao_fiscal ADD COLUMN IF NOT EXISTS provedor_token_homologacao_cripto TEXT;
ALTER TABLE configuracao_fiscal ADD COLUMN IF NOT EXISTS provedor_token_producao_cripto TEXT;

-- CST de PIS/COFINS dos itens da nota. Simples Nacional normalmente usa 49 (outras operações de saída);
-- confirmar com a contabilidade de cada empresa. Nulo = não informado: a emissão recusa em vez de supor.
ALTER TABLE configuracao_fiscal ADD COLUMN IF NOT EXISTS pis_cofins_cst VARCHAR(2)
    CHECK (pis_cofins_cst IS NULL OR pis_cofins_cst ~ '^[0-9]{2}$');
