-- "Limpar cache" do SUPER_ADMIN: cada limpeza sobe a versão da empresa. O servidor
-- devolve a versão em todas as respostas (X-Versao-Cache) e o navegador que estiver
-- com outra descarta o que guardou e recarrega. Só a linha em public.tenants é usada
-- (ver V21: a tabela existe em todo schema, mas é lida sempre de public).
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS versao_cache    BIGINT       NOT NULL DEFAULT 0;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS cache_limpo_em  TIMESTAMP;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS cache_limpo_por VARCHAR(150);
