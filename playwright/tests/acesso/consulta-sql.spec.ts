import { test, expect } from '../../fixtures'
import type { APIRequestContext } from '@playwright/test'

/*
 Consulta SQL do SUPER_ADMIN contra o Postgres de verdade: lê os dados da empresa, mas não lê
 senhas/tokens, tabelas da plataforma nem o schema de outra empresa, e não altera nada.
*/
const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }

async function consultar(request: APIRequestContext, sql: string) {
    return request.post('/api/manutencao/consulta-sql', { headers, data: { sql } })
}

async function recusada(request: APIRequestContext, sql: string, trecho: RegExp) {
    const r = await consultar(request, sql)
    expect(r.status(), sql).toBe(422)
    expect((await r.json()).message, sql).toMatch(trecho)
}

test.describe('Consulta SQL (SUPER_ADMIN)', () => {
    test('Lê os dados da empresa, inclusive o que não é segredo em tabela com segredo', async ({ request }) => {
        const r = await consultar(request, 'SELECT codigo_interno, nome, ncm FROM produtos ORDER BY id LIMIT 3;')
        expect(r.ok(), await r.text()).toBeTruthy()
        const corpo = await r.json()
        expect(corpo.colunas).toEqual(['codigo_interno', 'nome', 'ncm'])
        expect(corpo.linhas.length).toBeGreaterThan(0)

        const usuarios = await consultar(request, 'SELECT id, login FROM usuarios LIMIT 1')
        expect(usuarios.ok(), await usuarios.text()).toBeTruthy()
    })

    test('Não lê segredo, tabela da plataforma nem outra empresa', async ({ request }) => {
        await recusada(request, 'SELECT senha_hash FROM usuarios', /Sem acesso/)
        await recusada(request, 'SELECT certificado_cripto FROM configuracao_fiscal', /Sem acesso/)
        await recusada(request, 'SELECT access_token_cripto FROM pagamento_provedor_config', /Sem acesso/)
        await recusada(request, 'SELECT * FROM usuarios', /Sem acesso/)
        await recusada(request, 'SELECT * FROM tenants', /Sem acesso/)
        await recusada(request, 'SELECT * FROM public.tenants', /Sem acesso/)

        const outro = await (await consultar(request,
            "SELECT nspname FROM pg_namespace WHERE nspname LIKE 'tenant_%' ORDER BY nspname LIMIT 1")).json()
        test.skip(outro.linhas.length === 0, 'Sem outra empresa no banco de teste')
        await recusada(request, `SELECT count(*) FROM "${outro.linhas[0][0]}".produtos`, /Sem acesso/)
    })

    test('Não altera nada nem troca de usuário do banco', async ({ request }) => {
        await recusada(request, "UPDATE produtos SET ncm = NULL", /Só é permitido consultar/)
        await recusada(request, 'SELECT 1; DELETE FROM produtos', /Uma consulta por vez/)
        await recusada(request, 'WITH x AS (DELETE FROM produtos RETURNING id) SELECT * FROM x', /Só é permitido consultar|Sem acesso/)
        await recusada(request, "SELECT set_config('role', 'acougue', false)", /.+/)
        await recusada(request, "SELECT pg_read_file('postgresql.conf')", /.+/)
    })

    test('Tela: executa o exemplo e mostra o resultado; menu só do SUPER_ADMIN', async ({ page }) => {
        await page.goto('/inicio')
        await page.getByRole('navigation').getByRole('link', { name: 'Consulta SQL' }).click()
        await page.getByRole('button', { name: 'Quantos produtos sem NCM por tipo' }).click()
        await page.getByRole('button', { name: /Executar/ }).click()
        await expect(page.getByRole('table', { name: 'Resultado da consulta' })).toContainText('tipo_produto')
        await expect(page.getByRole('button', { name: 'Baixar CSV' })).toBeVisible()

        await page.getByLabel('Consulta SQL').fill('DELETE FROM produtos')
        await page.getByRole('button', { name: /Executar/ }).click()
        await expect(page.getByRole('alert')).toContainText('Só é permitido consultar')
    })
})
