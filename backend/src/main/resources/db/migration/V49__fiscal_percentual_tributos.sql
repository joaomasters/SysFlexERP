-- Lei 12.741/2012 (Lei da Transparência): o DANFE ao consumidor mostra o valor aproximado dos tributos.
-- Optante do Simples pode informar só a alíquota a que está sujeito (Decreto 8.264/2014): o percentual
-- vem da contabilidade (alíquota efetiva do DAS). Nulo = não informado (o DANFE mostra R$ 0,00).
ALTER TABLE configuracao_fiscal ADD COLUMN IF NOT EXISTS percentual_tributos NUMERIC(5,2)
    CHECK (percentual_tributos IS NULL OR (percentual_tributos >= 0 AND percentual_tributos <= 100));
