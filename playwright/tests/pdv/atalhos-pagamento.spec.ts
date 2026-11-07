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

    test('Deve registrar a venda manual só pelo teclado (F2, F7, F8, F8)', async ({ page, request }) => {
        await abrirComanda(page, await abrirVenda(request))
        await page.keyboard.press('F10')
        await page.keyboard.press('F2')
        // Sem maquininha de débito o painel já abre no manual; com, o F7 alterna
        const manual = page.getByRole('button', { name: /Venda manual/ })
        if (await manual.getAttribute('aria-pressed') === 'false') await page.keyboard.press('F7')
        await expect(page.getByRole('button', { name: /Registrar venda manual \[F8\]/ })).toBeVisible()

        await page.keyboard.press('F8')
        await page.getByPlaceholder('NSU / DOC').fill('123456')
        await page.keyboard.press('F8')
        // Venda quitada: o pagamento entra na lista e o painel do cartão sai
        await expect(page.getByText(/NSU 123456/)).toBeVisible()
        await expect(page.getByRole('button', { name: 'Finalizar Venda' })).toBeEnabled()
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

test.describe('PDV: troco e quantidade', () => {
    test('Deve mostrar o troco da nota escolhida e finalizar só pelo teclado (F8, F10)', async ({ page, request }) => {
        await abrirComanda(page, await abrirVenda(request)) // 1 kg de Picanha a R$ 69,90
        await page.keyboard.press('F10')
        await page.keyboard.press('F1')
        await page.getByRole('button', { name: 'R$ 100,00' }).click()
        await expect(page.getByText('Troco para o cliente')).toBeVisible()
        await expect(page.getByText('R$ 30,10').first()).toBeVisible()

        await page.keyboard.press('F8')
        await expect(page.getByRole('button', { name: /Finalizar Venda · Troco R\$ 30,10/ })).toBeEnabled()
        await page.keyboard.press('F10')
        await expect(page.getByText('Imprimir o comprovante para o cliente?')).toBeVisible()
    })

    test('Esc cancela a janela de pagamento', async ({ page, request }) => {
        await abrirComanda(page, await abrirVenda(request))
        await page.keyboard.press('F10')
        await expect(page.getByText('Pagamento', { exact: true })).toBeVisible()
        await page.keyboard.press('Escape')
        await expect(page.getByText('Pagamento', { exact: true })).toHaveCount(0)
    })

    test('Deve mudar a quantidade do item com + e − e com F7', async ({ page, request, produtosApi }) => {
        const vendaId = await abrirVenda(request)
        const espeto = await produtosApi.criar({ nome: `Espeto ${faker.string.alphanumeric(6)}`, precoVenda: 9 })
        await request.post(`/api/pdv/vendas/${vendaId}/itens`, {
            headers, data: { produtoId: espeto.id, quantidade: 1, tipoEntrada: 'MANUAL' },
        })
        await abrirComanda(page, vendaId)
        const linha = page.getByRole('row').filter({ hasText: espeto.nome })
        await linha.click()

        await page.keyboard.press('+')
        await expect(linha).toContainText('2 un')
        await expect(linha).toContainText('R$ 18,00')

        await page.keyboard.press('F7')
        await page.getByLabel('Quantidade', { exact: true }).fill('5')
        await page.keyboard.press('Enter')
        await expect(linha).toContainText('5 un')

        await page.keyboard.press('-')
        await expect(linha).toContainText('4 un')
        await expect(linha).toContainText('R$ 36,00')
    })
})
