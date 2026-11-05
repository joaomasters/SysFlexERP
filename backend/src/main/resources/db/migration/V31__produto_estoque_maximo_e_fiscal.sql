-- Campos da nova tela de cadastro de produto: estoque máximo e a aba "Informações Fiscais".
-- Todos opcionais (produtos já cadastrados continuam válidos), menos a origem, que é
-- obrigatória na nota fiscal e começa como 0 (nacional), o caso de quase todo açougue.
-- As regras de formato são as mesmas do formulário e da entidade Produto.
ALTER TABLE produtos
    ADD COLUMN estoque_maximo NUMERIC(12,4)
        CHECK (estoque_maximo IS NULL OR estoque_maximo >= 0),
    ADD COLUMN ncm VARCHAR(8)
        CHECK (ncm ~ '^[0-9]{8}$'),
    ADD COLUMN cest VARCHAR(7)
        CHECK (cest ~ '^[0-9]{7}$'),
    ADD COLUMN cfop VARCHAR(4)
        CHECK (cfop ~ '^[1-7][0-9]{3}$'),
    ADD COLUMN origem VARCHAR(1) NOT NULL DEFAULT '0'
        CHECK (origem ~ '^[0-8]$'),
    ADD COLUMN csosn VARCHAR(3)
        CHECK (csosn IN ('101', '102', '103', '201', '202', '203', '300', '400', '500', '900'));

COMMENT ON COLUMN produtos.estoque_maximo IS 'Limite superior de estoque (opcional); nunca menor que o mínimo';
COMMENT ON COLUMN produtos.ncm IS 'Nomenclatura Comum do Mercosul, 8 dígitos';
COMMENT ON COLUMN produtos.cest IS 'Código Especificador da Substituição Tributária, 7 dígitos';
COMMENT ON COLUMN produtos.cfop IS 'CFOP padrão de saída (ex.: 5102)';
COMMENT ON COLUMN produtos.origem IS 'Origem da mercadoria (tabela A do ICMS), 0 a 8';
COMMENT ON COLUMN produtos.csosn IS 'CSOSN do Simples Nacional';
