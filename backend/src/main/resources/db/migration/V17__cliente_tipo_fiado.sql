-- A tabela clientes tinha uma constraint de banco (criada lá na
-- V1__initial_schema.sql) restringindo tipo_cliente a um conjunto fixo de
-- valores, que não incluía 'FIADO' (tipo novo, adicionado só no frontend
-- até agora). Sem esta migration, qualquer tentativa de salvar um cliente
-- como FIADO falha com:
--   ERROR: new row for relation "clientes" violates check constraint
--   "clientes_tipo_cliente_check"
ALTER TABLE clientes DROP CONSTRAINT clientes_tipo_cliente_check;

ALTER TABLE clientes ADD CONSTRAINT clientes_tipo_cliente_check
    CHECK (tipo_cliente IN ('VAREJO', 'ATACADO', 'RESTAURANTE', 'CONVENIADO', 'FIADO'));