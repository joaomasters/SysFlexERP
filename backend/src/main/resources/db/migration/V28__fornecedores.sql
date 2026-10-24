-- Cadastro de fornecedores, ligado ao cadastro de produtos.
--
-- Antes o fornecedor do produto era um texto livre (produtos.fornecedor), digitado
-- à mão em cada produto: o mesmo fornecedor aparecia com grafias diferentes
-- ("Distribuidora ABC", "distrib. abc"...). Agora existe a tabela fornecedores e o
-- produto aponta para ela (fornecedor_id).
--
-- produtos.fornecedor (texto) continua existindo de propósito: guarda o NOME do
-- fornecedor vinculado. Assim as telas e relatórios que já leem esse texto seguem
-- funcionando sem alteração, e um renomear em Fornecedores atualiza o texto dos produtos.

CREATE TABLE fornecedores (
    id            BIGSERIAL    PRIMARY KEY,
    nome          VARCHAR(150) NOT NULL,
    tipo_pessoa   VARCHAR(5)   NOT NULL DEFAULT 'PJ' CHECK (tipo_pessoa IN ('PF', 'PJ')),
    cpf_cnpj      VARCHAR(18),
    telefone      VARCHAR(20),
    email         VARCHAR(100),
    contato       VARCHAR(100),
    endereco      TEXT,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_por_id BIGINT       REFERENCES usuarios(id),
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- Dois fornecedores com o mesmo nome (ignorando maiúsculas/espaços nas pontas) só geram confusão no dropdown.
CREATE UNIQUE INDEX ux_fornecedores_nome ON fornecedores (LOWER(TRIM(nome)));

ALTER TABLE produtos
    ADD COLUMN fornecedor_id BIGINT REFERENCES fornecedores(id);
CREATE INDEX idx_produto_fornecedor_id ON produtos (fornecedor_id);

-- Migra o que já existe: cada nome distinto vira um fornecedor (sem CNPJ/telefone —
-- esses dados nunca foram coletados; o usuário completa depois em Fornecedores).
-- Fontes: produtos, notas de recebimento e contas a pagar.
INSERT INTO fornecedores (nome)
SELECT DISTINCT ON (LOWER(TRIM(nome))) TRIM(nome)
FROM (
    SELECT fornecedor AS nome FROM produtos
    UNION ALL
    SELECT fornecedor FROM recebimento_mercadoria
    UNION ALL
    SELECT fornecedor FROM contas_pagar
) origem
WHERE nome IS NOT NULL AND TRIM(nome) <> ''
ORDER BY LOWER(TRIM(nome)), TRIM(nome);

UPDATE produtos p
SET fornecedor_id = f.id,
    fornecedor    = f.nome
FROM fornecedores f
WHERE p.fornecedor IS NOT NULL
  AND LOWER(TRIM(p.fornecedor)) = LOWER(TRIM(f.nome));

-- Novo módulo FORNECEDORES na matriz de permissões. Quem já podia mexer em
-- Produtos recebe as mesmas permissões, para ninguém perder acesso ao que usava.
INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT pp.perfil_id, 'FORNECEDORES', pp.pode_ver, pp.pode_criar, pp.pode_editar, pp.pode_excluir
FROM perfil_permissoes pp
WHERE pp.modulo = 'PRODUTOS'
  AND NOT EXISTS (
      SELECT 1 FROM perfil_permissoes x WHERE x.perfil_id = pp.perfil_id AND x.modulo = 'FORNECEDORES');

INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT id, 'FORNECEDORES', TRUE, TRUE, TRUE, TRUE FROM perfis WHERE nome IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = perfis.id AND pp.modulo = 'FORNECEDORES');