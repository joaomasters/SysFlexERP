import { test, expect } from '../../fixtures'
import { faker } from '@faker-js/faker'
import type { APIRequestContext, Page } from '@playwright/test'

const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }

/** Comanda aberta com um item no caixa do usuário de teste. */
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

async function abrirComanda(page: Page, vendaId: number) {
    await page.goto('/pdv')
    await page.getByRole('button', { name: new RegExp(`Comanda #${vendaId}\\b`) }).click()
    await expect(page.getByRole('button', { name: /FINALIZAR/ })).toBeEnabled()
}

const metodo = (page: Page, nome: string) => page.getByRole('button', { name: new RegExp(`^${nome} \\[F\\d\\]$`) })

test.describe('PDV: atalhos do pagamento', () => {
    test('Deve escolher a forma de pagamento pelo F1–F6 e alternar a venda manual com F7', async ({ page, request }) => {
        // Uma maquininha integrada qualquer, para o seletor aparecer
        const nome = `Caixa ${faker.string.alphanumeric(5)}`
        await request.post('/api/maquininhas/terminais', {
            headers,
            data: { nome, provedor: 'MERCADO_PAGO', modoIntegracao: 'API_NUVEM', identificadorDispositivo: 'PAX_TESTE',
                aceitaDebito: true, aceitaCredito: true, maxParcelas: 6, ativo: true },
        })
        await abrirComanda(page, await abrirVenda(request))

        await page.keyboard.press('F10')
        await expect(page.getByText('Pagamento', { exact: true })).toBeVisible()

        await page.keyboard.press('F2')
        await expect(metodo(page, 'Débito')).toHaveClass(/scale-105/)
        const manual = page.getByRole('button', { name: /Venda manual/ })
        await expect(manual).toHaveAttribute('aria-pressed', 'false')

        await page.keyboard.press('F7')
        await expect(manual).toHaveAttribute('aria-pressed', 'true')
        await expect(page.getByRole('button', { name: /Registrar venda manual/ })).toBeVisible()
        await page.keyboard.press('F7')
        await expect(page.getByRole('button', { name: /Enviar para maquininha/ })).toBeVisible()

        await page.keyboard.press('F3')
        await expect(metodo(page, 'Crédito')).toHaveClass(/scale-105/)
        // F4 é PIX aqui dentro, e não "Sair da comanda" do PDV
        await page.keyboard.press('F4')
        await expect(metodo(page, 'PIX')).toHaveClass(/scale-105/)
        await expect(page.getByText('Pagamento', { exact: true })).toBeVisible()
        await page.keyboard.press('F1')
        await expect(metodo(page, 'Dinheiro')).toHaveClass(/scale-105/)
        await page.keyboard.press('F6')
        await expect(metodo(page, 'Fiado')).toHaveClass(/scale-105/)
        await page.keyboard.press('F5')
        await expect(metodo(page, 'Cheque')).toHaveClass(/scale-105/)
    })

    test('Deve cancelar a venda com F9, pedindo confirmação', async ({ page, request }) => {
        await abrirComanda(page, await abrirVenda(request))
        await expect(page.getByRole('button', { name: /CANCELAR VENDA \[F9\]/ })).toBeVisible()

        await page.keyboard.press('F9')
        await expect(page.getByText('Cancelar esta venda?')).toBeVisible()
        await page.keyboard.press('Escape')
        await expect(page.getByText('Cancelar esta venda?')).toHaveCount(0)

        await page.keyboard.press('F9')
        await page.keyboard.press('Enter')
        await expect(page.getByText('Venda cancelada')).toBeVisible()
    })
})
