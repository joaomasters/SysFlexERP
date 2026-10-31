-- Mais tipos de produto para o cadastro (BEBIDA já existia desde a V7, mas não
-- aparecia na tela). O código é o que fica gravado; o nome vem do frontend.
ALTER TABLE produtos
    DROP CONSTRAINT IF EXISTS produtos_tipo_produto_check;

ALTER TABLE produtos
    ADD CONSTRAINT produtos_tipo_produto_check
        CHECK (tipo_produto IN (
            'CORTE', 'INDUSTRIALIZADO', 'INSUMO', 'SUBPRODUTO', 'BEBIDA',
            'ALIMENTO', 'MERCEARIA', 'HORTIFRUTI', 'FRIOS_LATICINIOS', 'PADARIA',
            'CONGELADOS', 'LIMPEZA', 'HIGIENE', 'OUTROS'
        ));
