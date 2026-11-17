-- Módulos industriais, sprint 7: custos por OP, MRP e indicadores.
-- Tudo é calculado a partir do que já está gravado (consumos, apontamentos, paradas, estruturas, estoque):
-- esta migration só cria índices para as consultas por período e a permissão do módulo.

CREATE INDEX IF NOT EXISTS ix_ind_apontamento_inicio ON ind_apontamento (inicio);
CREATE INDEX IF NOT EXISTS ix_ind_apontamento_maquina ON ind_apontamento (maquina_id);
CREATE INDEX IF NOT EXISTS ix_ind_op_evento_status ON ind_op_evento (status_novo, created_at);
CREATE INDEX IF NOT EXISTS ix_ind_inspecao_data ON ind_inspecao (created_at);
CREATE INDEX IF NOT EXISTS ix_ind_expedicao_carregada ON ind_expedicao (carregada_em);

INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT p.id, 'IND_CUSTOS', TRUE, TRUE, TRUE, TRUE FROM perfis p
WHERE p.nome IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = p.id AND pp.modulo = 'IND_CUSTOS');

INSERT INTO perfil_permissoes (perfil_id, modulo, pode_ver, pode_criar, pode_editar, pode_excluir)
SELECT p.id, 'IND_CUSTOS', FALSE, FALSE, FALSE, FALSE FROM perfis p
WHERE p.nome NOT IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM perfil_permissoes pp WHERE pp.perfil_id = p.id AND pp.modulo = 'IND_CUSTOS');
