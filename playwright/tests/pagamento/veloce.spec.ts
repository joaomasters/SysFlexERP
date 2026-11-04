import { test, expect } from '../../fixtures'
import { faker } from '@faker-js/faker'
import type { APIRequestContext, Page } from '@playwright/test'

// Precisa do backend com PAGAMENTO_VELOCE_URL apontando para support/veloce-falso.mjs
const falso = process.env.E2E_VELOCE_FALSO
const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }

async function configurarPelaTela(page: Page, senha: string) {
    await page.goto('/acesso/maquininhas')
    // O cartão da Veloce: o div mais interno que tem o nome e o botão Configurar
    const cartao = page.locator('div')
        .filter({ has: page.getByText('Veloce Smart TEF', { exact: true }) })
        .filter({ has: page.getByRole('button', { name: 'Configurar' }) })
        .last()
    await cartao.getByRole('button', { name: 'Configurar' }).click()

    const ativo = page.getByLabel('Adquirente ativa nesta empresa')
    if (!(await ativo.isChecked())) await ativo.check()
    await page.locator('input[type="email"]').fill('integracao@loja.com')
    await page.locator('input[type="password"]').fill(senha)
    await page.getByPlaceholder('00.000.000/0000-00').fill('28.803.350/0001-05')
    await page.getByRole('button', { name: 'Salvar' }).click()
    await expect(page.getByText('Veloce Smart TEF atualizado.')).toBeVisible()
    return cartao
}

async function criarMaquininha(request: APIRequestContext, token = 'EOIVM') {
    const resp = await request.post('/api/maquininhas/terminais', {
        headers,
        data: {
            nome: `Smart Sicredi ${faker.string.alphanumeric(5)}`, provedor: 'VELOCE', modoIntegracao: 'API_NUVEM',
            identificadorDispositivo: token, aceitaDebito: true, aceitaCredito: true, maxParcelas: 6, ativo: true,
        },
    })
    expect(resp.ok()).toBeTruthy()
    return (await resp.json()).id as number
}

/** Comanda aberta com um item, para ter venda com saldo a pagar. */
async function abrirVenda(request: APIRequestContext) {
    const usuarioId = JSON.parse(Buffer.from(process.env.E2E_TOKEN!.split('.')[1], 'base64url').toString()).usuarioId
    let caixa = await request.get(`/api/pdv/caixa/aberto?operadorId=${usuarioId}`, { headers })
    if (!caixa.ok()) caixa = await request.post('/api/pdv/caixa/abrir?valorAbertura=100', { headers })
    const produtos = await (await request.get('/api/estoque/produtos', { headers })).json()
    const produto = produtos.find((p: { nome: string }) => p.nome === 'Picanha')
    const venda = await (await request.post('/api/pdv/vendas/abrir', { headers, data: { caixaId: (await caixa.json()).id } })).json()
    await request.post(`/api/pdv/vendas/${venda.id}/itens`, {
        headers, data: { produtoId: produto.id, quantidade: 1, tipoEntrada: 'MANUAL' },
    })
    return venda.id as number
}

async function cobrar(request: APIRequestContext, terminalId: number, vendaId: number, valor: number, tipo = 'CREDITO', parcelas = 2) {
    const resp = await request.post('/api/pagamento/transacoes', { headers, data: { terminalId, vendaId, tipo, parcelas, valor } })
    expect(resp.ok()).toBeTruthy()
    return resp.json()
}

const consultar = async (request: APIRequestContext, id: number) =>
    (await (await request.get(`/api/pagamento/transacoes/${id}`, { headers })).json())

test.describe('Maquininha Veloce Smart TEF', () => {
    test.skip(!falso, 'Defina E2E_VELOCE_FALSO com o endereço do support/veloce-falso.mjs')
    test.describe.configure({ mode: 'serial' })

    test('Deve recusar senha errada no teste de conexão e aceitar a certa', async ({ page }) => {
        let cartao = await configurarPelaTela(page, 'errada')
        await cartao.getByRole('button', { name: 'Testar conexão' }).click()
        await expect(cartao.getByText(/Senha incorreta/)).toBeVisible()

        cartao = await configurarPelaTela(page, 'Senha#123456')
        await cartao.getByRole('button', { name: 'Testar conexão' }).click()
        await expect(cartao.getByText(/CNPJ 28803350000105 liberado/)).toBeVisible()
    })

    test('Deve mandar a cobrança para a maquininha e aprovar pelo aviso da Veloce', async ({ request }) => {
        const terminalId = await criarMaquininha(request)
        const vendaId = await abrirVenda(request)

        const t = await cobrar(request, terminalId, vendaId, 10)
        expect(t.status).toBe('PENDENTE')

        const enviado = (await (await request.get(`${falso}/ultimo`)).json()).pagamento
        expect(enviado).toMatchObject({ token: 'EOIVM', cnpj: '28803350000105', tipo: 1, parcelas: '2', indice: 0, pedido: vendaId })
        expect(enviado.url_retorno).toMatch(/\/api\/pagamento\/webhook\/veloce\?tenant=001$/)

        // O aviso da Veloce chega no webhook e a transação aprova sem esperar a consulta do PDV
        await expect.poll(async () => (await (await request.get(`${falso}/ultimo`)).json()).aviso?.status, { timeout: 15_000 }).toBe(200)
        await expect.poll(async () => (await consultar(request, t.id)).status, { timeout: 15_000 }).toBe('APROVADA')
        const aprovada = await consultar(request, t.id)
        expect(aprovada).toMatchObject({ nsu: '882342', autorizacao: '076442', bandeira: 'MASTERCARD' })
    })

    test('Deve mostrar a recusa do cartão', async ({ request }) => {
        const terminalId = await criarMaquininha(request)
        const t = await cobrar(request, terminalId, await abrirVenda(request), 10.13, 'DEBITO', 1)

        await expect.poll(async () => (await consultar(request, t.id)).status, { timeout: 15_000 }).toBe('NEGADA')
        expect((await consultar(request, t.id)).mensagem).toContain('Saldo insuficiente')
    })

    test('Deve cancelar a cobrança que ainda espera o cartão', async ({ request }) => {
        const terminalId = await criarMaquininha(request)
        const t = await cobrar(request, terminalId, await abrirVenda(request), 9.99)

        const resp = await request.post(`/api/pagamento/transacoes/${t.id}/cancelar`, { headers })
        expect(resp.ok()).toBeTruthy()
        expect((await resp.json()).status).toBe('CANCELADA')
    })

    test('Deve explicar quando o token da maquininha não existe na Veloce', async ({ request }) => {
        const terminalId = await criarMaquininha(request, 'ERRADO')
        const t = await cobrar(request, terminalId, await abrirVenda(request), 10)

        expect(t.status).toBe('ERRO')
        expect(t.mensagem).toContain('Dispositivos')
    })
})
