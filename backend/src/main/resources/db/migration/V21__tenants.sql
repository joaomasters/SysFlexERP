-- Registro central de tenants (multi-tenancy por schema Postgres).
-- Esta tabela é sempre lida do schema public, independente do tenant
-- corrente — ver com.acougue.tenant.TenantRepository. Roda em TODO schema
-- de tenant (porque a cadeia de migration inteira é reaplicada ao
-- provisionar um tenant novo), mas só é usada de fato em public.
CREATE TABLE tenants (
    id            BIGSERIAL    PRIMARY KEY,
    slug          VARCHAR(50)  NOT NULL UNIQUE,
    nome          VARCHAR(150) NOT NULL,
    schema_name   VARCHAR(63)  NOT NULL UNIQUE,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);
