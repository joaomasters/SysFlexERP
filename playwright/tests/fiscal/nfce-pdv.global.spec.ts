import { test, expect } from '../../fixtures'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import type { APIRequestContext, Page } from '@playwright/test'

/*
 NFC-e no PDV ("CPF na nota?") com o provedor SIMULADO, que só existe com o backend rodando com
 FISCAL_PROVEDOR_SIMULADO=true. Liga a emissão da empresa inteira: por isso é um *.global.spec,
 que roda depois dos outros testes (ver playwright.config.ts), e desliga no fim.
*/
const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }
// Certificado autoassinado, só para teste (CN "LOJA TESTE LTDA:11222333000181", senha senha123)
const PFX = readFileSync(fileURLToPath(new URL('../../support/a1-teste.pfx', import.meta.url))).toString('base64')
const CPF = '52998224725'
const fiscais = { ncm: '02013000', cfop: '5102', csosn: '102', origem: '0' }

async function configurar(request: APIRequestContext, emitirNfce: boolean, provedor = 'SIMULADO') {
    const atual = await (await request.get('/api/fiscal/configuracao', { headers })).json()
    const r = await request.put('/api/fiscal/configuracao', {
        headers,
        data: {
            ...atual, emitirNfce, emitirNfe: false, ambiente: 'HOMOLOGACAO', provedor, nfceModo: 'SOB_DEMANDA',
            razaoSocial: 'Loja Teste Ltda', cnpj: '11222333000181', inscricaoEstadual: '1234567850',
            logradouro: 'Rua XV de Novembro', numeroEndereco: '100', bairro: 'Centro', municipio: 'Rolândia',
            codigoMunicipio: '4122404', cep: '86600000', cscId: '000001', cscToken: 'CSC-DE-TESTE-123',
        },
    })
    expect(r.ok(), await r.text()).toBeTruthy()
}

/** A Picanha da massa de teste (tem estoque); o teste põe e tira os dados fiscais dela. */
let picanhaOriginal: Record<string, unknown>

async function dadosFiscaisDaPicanha(request: APIRequestContext, dados: Record<string, unknown>) {
    const r = await request.put(`/api/estoque/produtos/${picanhaOriginal.id}`, { headers, data: { ...picanhaOriginal, ...dados } })
    expect(r.ok(), await r.text()).toBeTruthy()
}

/** Comanda aberta com 1 kg de Picanha no caixa do usuário de teste. */
async function abrirVenda(request: APIRequestContext) {
    const usuarioId = JSON.parse(Buffer.from(process.env.E2E_TOKEN!.split('.')[1], 'base64url').toString()).usuarioId
    let caixa = await request.get(`/api/pdv/caixa/aberto?operadorId=${usuarioId}`, { headers })
    if (!caixa.ok()) caixa = await request.post('/api/pdv/caixa/abrir?valorAbertura=100', { headers })
    const produto = picanhaOriginal
    const venda = await (await request.post('/api/pdv/vendas/abrir', { headers, data: { caixaId: (await caixa.json()).id } })).json()
    await request.post(`/api/pdv/vendas/${venda.id}/itens`, { headers, data: { produtoId: produto.id, quantidade: 1, tipoEntrada: 'MANUAL' } })
    return venda.id as number
}

/** Abre a comanda e paga em dinheiro, valor exato, só pelo teclado (F10, F1, F8, F10). */
async function pagarEmDinheiro(page: Page, vendaId: number) {
    await page.goto('/pdv')
    await page.getByRole('button', { name: new RegExp(`Comanda #${vendaId}\\b`) }).click()
    await expect(page.getByRole('button', { name: /FINALIZAR/ })).toBeEnabled()
    await page.keyboard.press('F10')
    await page.keyboard.press('F1')
    await expect(page.getByRole('textbox', { name: '0,00' })).toHaveValue('69,90')
    await page.getByRole('button', { name: 'Lançar pagamento' }).click()
    await expect(page.getByRole('button', { name: /Finalizar Venda/ })).toBeEnabled()
    await page.keyboard.press('F10')
}

test.describe('NFC-e no PDV: CPF na nota', () => {
    test.describe.configure({ mode: 'serial' })

    test.beforeAll(async ({ request }) => {
        const config = await (await request.get('/api/fiscal/configuracao', { headers })).json()
        test.skip(!config.provedores.some((p: { codigo: string }) => p.codigo === 'SIMULADO'),
            'Backend sem FISCAL_PROVEDOR_SIMULADO=true')
        await configurar(request, false)
        const cert = await request.post('/api/fiscal/configuracao/certificado', { headers, data: { arquivoBase64: PFX, senha: 'senha123' } })
        expect(cert.ok(), await cert.text()).toBeTruthy()
        await configurar(request, true)
        const produtos = await (await request.get('/api/estoque/produtos', { headers })).json()
        picanhaOriginal = produtos.find((p: { nome: string }) => p.nome === 'Picanha')
        await dadosFiscaisDaPicanha(request, fiscais)
    })

    test.afterAll(async ({ request }) => {
        // Deixa a empresa como os outros testes esperam: sem provedor, no cupom não fiscal
        await configurar(request, false, 'NENHUM')
        if (picanhaOriginal) await dadosFiscaisDaPicanha(request, {})
    })

    test('Deve emitir a NFC-e com o CPF e oferecer o DANFE', async ({ page, request }) => {
        const vendaId = await abrirVenda(request)
        await pagarEmDinheiro(page, vendaId)

        await expect(page.getByRole('dialog', { name: 'CPF na nota' })).toBeVisible()
        await page.getByLabel('CPF ou CNPJ do cliente').fill(CPF)
        await expect(page.getByLabel('CPF ou CNPJ do cliente')).toHaveValue('529.982.247-25')
        await page.keyboard.press('Enter')

        await expect(page.getByText(/NFC-e nº \d+ autorizada\. Imprimir o DANFE\?/)).toBeVisible()
        const nfce = await (await request.get(`/api/fiscal/nfce/venda/${vendaId}`, { headers })).json()
        expect(nfce.status).toBe('AUTORIZADA')
        expect(nfce.destinatarioDocumento).toBe(CPF)
        expect(nfce.chave).toMatch(/^4\d{43}$/)
        expect(nfce.qrCodeSvg).toContain('<svg')
    })

    test('Sem CPF, a venda sai no cupom não fiscal', async ({ page, request }) => {
        const vendaId = await abrirVenda(request)
        await pagarEmDinheiro(page, vendaId)
        await expect(page.getByRole('dialog', { name: 'CPF na nota' })).toBeVisible()
        await page.keyboard.press('Enter')

        await expect(page.getByText('Imprimir o comprovante para o cliente?')).toBeVisible()
        expect((await request.get(`/api/fiscal/nfce/venda/${vendaId}`, { headers })).status()).toBe(204)
    })

    test('CPF errado não fecha a venda; Esc volta ao pagamento', async ({ page, request }) => {
        const vendaId = await abrirVenda(request)
        await pagarEmDinheiro(page, vendaId)
        await page.getByLabel('CPF ou CNPJ do cliente').fill('52998224724')
        await page.keyboard.press('Enter')
        await expect(page.getByText('CPF ou CNPJ inválido: confira os números.')).toBeVisible()

        await page.keyboard.press('Escape')
        await expect(page.getByRole('dialog', { name: 'CPF na nota' })).toHaveCount(0)
        // O pagamento continua lançado, pronto para finalizar de novo
        await expect(page.getByRole('button', { name: /Finalizar Venda/ })).toBeEnabled()
    })

    test('Produto sem NCM: a venda é registrada e avisa o que completar', async ({ page, request }) => {
        await dadosFiscaisDaPicanha(request, { ncm: null })
        const vendaId = await abrirVenda(request)
        await pagarEmDinheiro(page, vendaId)
        await page.getByLabel('CPF ou CNPJ do cliente').fill(CPF)
        await page.keyboard.press('Enter')

        await expect(page.getByText(/NFC-e não emitida: Complete os dados fiscais/)).toBeVisible()
        await expect(page.getByText('Imprimir o comprovante para o cliente?')).toBeVisible()
    })
})
