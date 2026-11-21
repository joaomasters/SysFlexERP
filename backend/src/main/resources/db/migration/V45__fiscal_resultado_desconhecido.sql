-- Contábil e Fiscal, etapa 1: semântica de estados da emissão.
-- DESCONHECIDO = transmitida sem resposta (timeout, conexão caída): pode ter sido autorizada e só a
-- consulta pela chave resolve. Antes esse caso virava ERRO e liberava nova emissão da mesma venda.
-- Aditiva: só amplia o conjunto de status aceitos; nenhum dado existente muda.
-- O CHECK do V36 é anônimo: remove pelo conteúdo, não pelo nome presumido.
DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN SELECT con.conname FROM pg_constraint con
             JOIN pg_class rel ON rel.oid = con.conrelid
             JOIN pg_namespace ns ON ns.oid = rel.relnamespace
             WHERE rel.relname = 'documento_fiscal' AND ns.nspname = current_schema()
               AND con.contype = 'c' AND pg_get_constraintdef(con.oid) LIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE documento_fiscal DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;
ALTER TABLE documento_fiscal ADD CONSTRAINT documento_fiscal_status_check
    CHECK (status IN ('PENDENTE', 'AUTORIZADA', 'REJEITADA', 'DENEGADA', 'CANCELADA', 'CONTINGENCIA',
                      'INUTILIZADA', 'ERRO', 'DESCONHECIDO'));

-- Busca das notas em aberto de uma venda (bloqueio de nova emissão enquanto a anterior não se resolve)
CREATE INDEX IF NOT EXISTS idx_documento_fiscal_venda_status ON documento_fiscal (venda_id, modelo, status);
