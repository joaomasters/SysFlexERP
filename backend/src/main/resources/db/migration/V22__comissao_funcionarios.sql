-- Comissão de funcionários.
--
-- Cada usuário (funcionário) tem um percentual de comissão sobre as vendas
-- que ele fecha no PDV (vendas.operador_id). 0 = não comissionado.
ALTER TABLE usuarios
    ADD COLUMN percentual_comissao NUMERIC(5,2) NOT NULL DEFAULT 0
        CHECK (percentual_comissao >= 0 AND percentual_comissao <= 100);

-- Snapshot da comissão no momento do fechamento da venda: se o percentual do
-- funcionário mudar depois, as vendas já fechadas mantêm o valor original.
-- Vendas fechadas antes desta migration ficam com NULL (sem comissão apurada).
ALTER TABLE vendas
    ADD COLUMN percentual_comissao NUMERIC(5,2),
    ADD COLUMN valor_comissao      NUMERIC(12,2);

CREATE INDEX idx_vendas_operador_data ON vendas(operador_id, data_venda)
    WHERE status = 'FECHADA';

-- Novo módulo COMISSOES na matriz de permissões (mesma lógica da V11).
-- SUPER_ADMIN e ADMIN: acesso total; GESTOR: só visualiza a apuração;
-- demais perfis recebem a linha negada, para manter a matriz completa.
INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT id, 'COMISSOES', TRUE, TRUE, TRUE, TRUE FROM perfis WHERE nome IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = perfis.id AND pp.modulo = 'COMISSOES');

INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT id, 'COMISSOES', TRUE, FALSE, FALSE, FALSE FROM perfis WHERE nome = 'GESTOR'
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = perfis.id AND pp.modulo = 'COMISSOES');

INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT id, 'COMISSOES', FALSE, FALSE, FALSE, FALSE FROM perfis
WHERE nome NOT IN ('SUPER_ADMIN', 'ADMIN', 'GESTOR')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = perfis.id AND pp.modulo = 'COMISSOES');
