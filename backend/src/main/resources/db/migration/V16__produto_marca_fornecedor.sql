-- Adiciona Marca e Fornecedor ao cadastro de produtos.
-- Campos usados principalmente por produtos vendidos por UNIDADE ou CAIXA
-- (industrializados, insumos) — cortes vendidos por KG/G normalmente não
-- têm uma "marca" comercial, mas o campo fica disponível pra qualquer
-- produto, sem restrição no banco.
ALTER TABLE produtos
    ADD COLUMN marca VARCHAR(100),
    ADD COLUMN fornecedor VARCHAR(150);

CREATE INDEX idx_produto_marca ON produtos (marca);
CREATE INDEX idx_produto_fornecedor ON produtos (fornecedor);