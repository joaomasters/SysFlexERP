-- Comprovante de venda (cupom não fiscal) configurável por empresa.
--
-- Antes, nome/endereço/telefone do cupom estavam fixos no frontend com os
-- dados do Açougue do Zé — qualquer outra empresa imprimiria o cabeçalho dele.
-- Uma linha por schema (id fixo = 1). Esta migration roda em cada schema de
-- empresa: o nome inicial vem do registro da própria empresa em public.tenants.

CREATE TABLE configuracao_comprovante (
    id                     SMALLINT     PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    nome_fantasia          VARCHAR(120) NOT NULL,
    cnpj                   VARCHAR(20),
    endereco               VARCHAR(150),
    bairro                 VARCHAR(80),
    cidade                 VARCHAR(80),
    telefone               VARCHAR(30),
    mensagem_rodape        VARCHAR(300),
    largura_papel_mm       SMALLINT     NOT NULL DEFAULT 80 CHECK (largura_papel_mm IN (58, 80)),
    vias                   SMALLINT     NOT NULL DEFAULT 1  CHECK (vias BETWEEN 1 AND 3),
    -- false = ao finalizar a venda o PDV pergunta se imprime; true = imprime direto
    imprimir_automatico    BOOLEAN      NOT NULL DEFAULT FALSE,
    mostrar_aviso_nao_fiscal BOOLEAN    NOT NULL DEFAULT TRUE,
    mostrar_operador       BOOLEAN      NOT NULL DEFAULT TRUE,
    mostrar_cliente        BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at             TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by             BIGINT       REFERENCES usuarios(id)
);

INSERT INTO configuracao_comprovante (id, nome_fantasia, endereco, bairro, telefone, largura_papel_mm)
SELECT 1,
       COALESCE((SELECT nome FROM public.tenants WHERE schema_name = current_schema()), 'Minha Loja'),
       -- a empresa principal mantém o que estava fixo no código até hoje
       CASE WHEN current_schema() = 'public' THEN 'R Nicolau Cotting, 103' END,
       CASE WHEN current_schema() = 'public' THEN 'Conjunto Habitacional' END,
       CASE WHEN current_schema() = 'public' THEN '(43) 96130-248' END,
       CASE WHEN current_schema() = 'public' THEN 58 ELSE 80 END;

-- Novo módulo na matriz de permissões: configurar o comprovante é de administrador.
-- (Ler a configuração para imprimir é liberado a qualquer usuário logado: são dados do cabeçalho da loja.)
INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT id, 'COMPROVANTE', TRUE, TRUE, TRUE, TRUE FROM perfis WHERE nome IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = perfis.id AND pp.modulo = 'COMPROVANTE');

INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT id, 'COMPROVANTE', FALSE, FALSE, FALSE, FALSE FROM perfis
WHERE nome NOT IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = perfis.id AND pp.modulo = 'COMPROVANTE');
