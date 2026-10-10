-- Código numérico fixo que a empresa informa no login (ex: "001"),
-- separado do slug interno (usado só pra nome do schema). O slug não é
-- mostrado pra ninguém fora do sistema; o código é o que a pessoa digita.
ALTER TABLE tenants ADD COLUMN codigo VARCHAR(10);

UPDATE tenants SET codigo = '001' WHERE schema_name = 'public';

ALTER TABLE tenants ALTER COLUMN codigo SET NOT NULL;
ALTER TABLE tenants ADD CONSTRAINT uk_tenants_codigo UNIQUE (codigo);
