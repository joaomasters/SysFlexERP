import { test, expect } from '../../fixtures'
import { faker } from '@faker-js/faker'
import { createHmac } from 'node:crypto'
import type { APIRequestContext, Page } from '@playwright/test'

const login = process.env.E2E_JARVIS_LOGIN ?? ''
const senha = process.env.E2E_JARVIS_SENHA ?? ''
const segredo = process.env.E2E_JARVIS_TOTP ?? ''
const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }

const TELAS = [
    '/inicio', '/pdv', '/pdv/sangria', '/estoque/produtos', '/estoque/fornecedores', '/estoque/desossa',
    '/estoque/inventario', '/estoque/perdas', '/estoque/validade', '/estoque/recebimento', '/estoque/fichas-desossa',
    '/fiscal/notas', '/financeiro/clientes', '/financeiro/faturamento', '/financeiro/contas-receber',
    '/financeiro/contas-pagar', '/financeiro/dre', '/financeiro/relatorios', '/financeiro/comissoes', '/balanca',
    '/acesso/usuarios', '/acesso/auditoria', '/acesso/perfis', '/acesso/maquininhas', '/acesso/comprovante',
]

function codigoTotp() {
    const alfabeto = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
    const bits = [...segredo].map(c => alfabeto.indexOf(c).toString(2).padStart(5, '0')).join('')
    const chave = Buffer.from((bits.match(/.{8}/g) ?? []).map(b => parseInt(b, 2)))
    const passo = Buffer.alloc(8)
    passo.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30_000)))
    const hash = createHmac('sha1', chave).update(passo).digest()
    return String((hash.readUInt32BE(hash[hash.length - 1] & 0x0f) & 0x7fffffff) % 1_000_000).padStart(6, '0')
}

async function entrarNoJarvis(page: Page, senhaUsada = senha) {
    await page.goto('/login')
    await page.getByPlaceholder('Ex: 001').fill('000')
    await page.getByPlaceholder('Digite seu usuário').fill(login)
    await page.getByPlaceholder('••••••').fill(senhaUsada)
    await page.getByPlaceholder('6 dígitos').fill(codigoTotp())
    await page.getByRole('button', { name: 'Entrar' }).click()
}

async function criarProduto(request: APIRequestContext, nome: string) {
    return request.post('/api/estoque/produtos', {
        headers,
        data: { nome, unidadeMedida: 'UN', tipoProduto: 'BEBIDA', precoVenda: 10, estoqueMinimo: 0 },
    })
}

test.describe('JARVIS', () => {
    test.skip(!login || !senha || !segredo, 'Defina E2E_JARVIS_LOGIN, E2E_JARVIS_SENHA e E2E_JARVIS_TOTP')
    test.use({ storageState: { cookies: [], origins: [] } })

    test('Não deve entrar com senha errada e usuário de empresa não acessa o painel', async ({ page, request }) => {
        await entrarNoJarvis(page, 'senha-errada')
        await expect(page.getByText('Código da empresa, usuário ou senha incorretos')).toBeVisible()

        const resp = await request.get('/api/jarvis/empresas', { headers })
        expect(resp.status()).toBe(403)
    })

    test('Deve ver a empresa em modo leitura, sem conseguir alterar nada', async ({ page, request }) => {
        const nome = `Produto da principal ${faker.string.uuid()}`
        expect((await criarProduto(request, nome)).ok()).toBeTruthy()

        await entrarNoJarvis(page)
        await expect(page.getByRole('heading', { name: 'JARVIS' })).toBeVisible()

        await page.getByRole('button', { name: 'Ver empresa 001' }).click()
        await expect(page.getByText(/JARVIS — somente leitura: 001/)).toBeVisible()

        await page.getByRole('navigation').getByRole('link', { name: 'Produtos' }).click()
        // A lista é paginada: busca o produto pelo nome
        await page.getByPlaceholder('Buscar por nome, código ou código de barras...').fill(nome)
        await expect(page.getByRole('row').filter({ hasText: nome })).toHaveCount(1)

        const token = await page.evaluate(() => JSON.parse(sessionStorage.getItem('jarvis_visualizacao')!).token)
        const comToken = { Authorization: `Bearer ${token}` }
        const escrita = await page.request.post('/api/estoque/produtos', {
            headers: comToken,
            data: { nome: 'Não pode', unidadeMedida: 'UN', tipoProduto: 'BEBIDA', precoVenda: 10, estoqueMinimo: 0 },
        })
        expect(escrita.status()).toBe(403)
        expect((await page.request.get('/api/admin/tenants', { headers: comToken })).status()).toBe(403)
        expect((await page.request.get('/api/jarvis/empresas', { headers: comToken })).status()).toBe(403)

        await page.getByRole('button', { name: 'Voltar ao JARVIS' }).click()
        await expect(page.getByRole('heading', { name: 'Acessos recentes' })).toBeVisible()
        await expect(page.getByText(new RegExp(`${login} visualizou 001`)).first()).toBeVisible()
    })

    test('Todas as telas devem abrir sem erro, e o modo leitura não pode contaminar as conexões', async ({ page, request }) => {
        test.setTimeout(180_000)
        const erros: string[] = []
        page.on('response', r => { if (r.status() >= 500) erros.push(`${r.status()} ${r.url()}`) })

        await entrarNoJarvis(page)
        await page.getByRole('button', { name: 'Ver empresa 001' }).click()
        await expect(page.getByText(/JARVIS — somente leitura/)).toBeVisible()

        for (const tela of TELAS) {
            await page.goto(tela)
            await expect(page.getByText(/JARVIS — somente leitura/)).toBeVisible()
            await page.waitForLoadState('networkidle')
        }
        expect(erros).toEqual([])

        // Sessão normal depois da visualização, em paralelo para passar por várias conexões do pool.
        // Código interno repetido é a corrida conhecida de gerarCodigoInterno; o que não pode
        // aparecer é a trava de leitura vazando para quem não é o JARVIS.
        let gravados = 0
        for (let i = 0; i < 6; i++) {
            const lote = await Promise.all([1, 2, 3, 4, 5].map(n => criarProduto(request, `Pós JARVIS ${i}-${n} ${faker.string.uuid()}`)))
            for (const r of lote) {
                expect(await r.text()).not.toMatch(/read-only/i)
                if (r.ok()) gravados++
            }
        }
        expect(gravados).toBeGreaterThan(0)
    })
})
