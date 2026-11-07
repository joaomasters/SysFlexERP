-- Código interno automático do produto (P000123). Antes era "quantidade de produtos + 1":
-- dois cadastros ao mesmo tempo recebiam o mesmo código, e um código digitado à mão na
-- faixa automática também batia. A sequência entrega números únicos mesmo em paralelo.
CREATE SEQUENCE IF NOT EXISTS produto_codigo_interno_seq;

-- Continua de onde a empresa está: o maior entre a quantidade de produtos e o maior P000000 já usado
SELECT setval('produto_codigo_interno_seq', GREATEST(m, 1), m > 0)
FROM (
    SELECT GREATEST(
               (SELECT COUNT(*) FROM produtos),
               COALESCE((SELECT MAX(CAST(SUBSTRING(codigo_interno FROM 2) AS BIGINT))
                         FROM produtos WHERE codigo_interno ~ '^P[0-9]{1,15}$'), 0)
           ) AS m
) atual;
