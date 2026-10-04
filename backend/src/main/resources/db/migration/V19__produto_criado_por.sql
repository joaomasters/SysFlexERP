-- Quem cadastrou o produto (exibido só para administradores — ver
-- UsuarioAutenticado#podeVerIdentificacaoUsuarios).
-- Produtos já existentes ficam com NULL ("não identificado"): a autoria nunca
-- foi registrada e não há como reconstruí-la.
ALTER TABLE produtos
    ADD COLUMN criado_por_id BIGINT REFERENCES usuarios(id);
