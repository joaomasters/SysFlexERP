-- Folga à direita do cupom, em mm. Cada driver de térmica imprime um pouco
-- menos que a área nominal (72 mm na bobina de 80) e alguns trocam a fonte
-- por uma interna mais larga: sem folga, o fim das linhas (valores) é cortado.
ALTER TABLE configuracao_comprovante
    ADD COLUMN margem_direita_mm SMALLINT NOT NULL DEFAULT 5
        CHECK (margem_direita_mm BETWEEN 0 AND 20);
