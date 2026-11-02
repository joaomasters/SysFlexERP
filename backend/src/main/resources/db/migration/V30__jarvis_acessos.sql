-- Histórico de acessos do JARVIS (visão da plataforma sobre todas as empresas).
-- As migrations rodam em TODOS os schemas (ver MigradorSchemasEmpresas): por isso
-- a tabela é qualificada com public e tem IF NOT EXISTS — existe uma vez só.
CREATE TABLE IF NOT EXISTS public.jarvis_acessos (
    id              BIGSERIAL    PRIMARY KEY,
    login           VARCHAR(100) NOT NULL,
    acao            VARCHAR(30)  NOT NULL,
    tenant_id       BIGINT,
    tenant_codigo   VARCHAR(10),
    tenant_nome     VARCHAR(150),
    criado_em       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_jarvis_acessos_criado_em ON public.jarvis_acessos (criado_em DESC);
