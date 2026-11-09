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

    test('Deve cobrar no voucher (vale) com F9 na maquininha que aceita voucher', async ({ page, request }) => {
        const nome = `Vale ${faker.string.alphanumeric(5)}`
        const terminal = await request.post('/api/maquininhas/terminais', {
            headers,
            data: { nome, provedor: 'SICREDI', modoIntegracao: 'MANUAL', aceitaDebito: true, aceitaCredito: true,
                maxParcelas: 6, aceitaVoucher: true, ativo: true },
        })
        expect(terminal.ok()).toBeTruthy()
        expect((await terminal.json()).aceitaVoucher).toBe(true)

        const vendaId = await abrirVenda(request)
        const usuarioId = JSON.parse(Buffer.from(process.env.E2E_TOKEN!.split('.')[1], 'base64url').toString()).usuarioId
        const caixaId = (await (await request.get(`/api/pdv/caixa/aberto?operadorId=${usuarioId}`, { headers })).json()).id
        const voucherAntes = (await (await request.get(`/api/pdv/caixa/${caixaId}/fechamento`, { headers })).json()).totalVoucher
        await abrirComanda(page, vendaId)
        await page.keyboard.press('F10')
        await page.keyboard.press('F9')
        await expect(metodo(page, 'Voucher')).toHaveClass(/scale-105/)
        // O pagamento continua aberto: F9 aqui é Voucher, não "Cancelar venda"
        await expect(page.getByText('Cancelar esta venda?')).toHaveCount(0)

        await page.locator("select").filter({ hasText: nome }).selectOption({ label: `${nome} · Sicredi` })
        await page.keyboard.press('F8')
        await page.getByPlaceholder('NSU / DOC').fill('777001')
        await page.keyboard.press('F8')
        await expect(page.getByText(/NSU 777001/)).toBeVisible()
        await expect(page.getByText('VOUCHER', { exact: true })).toBeVisible()

        await page.keyboard.press('F10')
        await expect(page.getByText('Imprimir o comprovante para o cliente?')).toBeVisible()
        // No fechamento do caixa o voucher aparece separado do débito
        const fechamento = await (await request.get(`/api/pdv/caixa/${caixaId}/fechamento`, { headers })).json()
        // "Pelo menos": outros testes em paralelo também lançam voucher neste mesmo caixa
        expect(fechamento.totalVoucher).toBeGreaterThanOrEqual(voucherAntes + 69.9 - 0.001)
    })

    test('Deve dividir o pagamento em débito, crédito, voucher e dinheiro', async ({ page, request }) => {
        await abrirComanda(page, await abrirVenda(request)) // 1 kg de Picanha a R$ 69,90
        await page.keyboard.press('F10')
        const valor = page.getByPlaceholder('0,00')

        /** Cartão pela venda manual: escolhe a forma, digita o valor e confirma com F8, F8 */
        async function cartao(tecla: string, centavos: string, nsu: string) {
            await page.keyboard.press(tecla)
            const manual = page.getByRole('button', { name: /Venda manual/ })
            if (await manual.getAttribute('aria-pressed') === 'false') await page.keyboard.press('F7')
            await valor.fill(centavos)
            await page.keyboard.press('F8')
            await page.getByPlaceholder('NSU / DOC').fill(nsu)
            await page.keyboard.press('F8')
        }

        // Cartão não dá troco: acima do que falta, o botão trava
        await page.keyboard.press('F2')
        await valor.fill('8000')
        await expect(page.getByText(/Cartão não dá troco: o máximo é R\$\s69,90/)).toBeVisible()
        await expect(page.getByRole('button', { name: /maquininha|Registrar venda manual/ })).toBeDisabled()

        // "falta" e o valor ficam em elementos separados: confere o parágrafo inteiro
        const falta = (v: string) => page.locator('p', { hasText: new RegExp(`falta\\s*R\\$\\s${v}`) })

        await cartao('F2', '3000', '1001')
        await expect(page.getByText(/R\$\s30,00 registrado manualmente · NSU 1001/)).toBeVisible()
        await expect(falta('39,90')).toBeVisible()
        // O próximo pagamento já vem com o que falta
        await expect(valor).toHaveValue('39,90')

        await cartao('F3', '2000', '1002')
        await expect(falta('19,90')).toBeVisible()
        await cartao('F9', '1000', '1003')
        await expect(falta('9,90')).toBeVisible()

        await page.keyboard.press('F1')
        await page.getByRole('button', { name: 'R$ 10,00' }).click()
        await page.keyboard.press('F8')
        await expect(page.getByRole('button', { name: /Finalizar Venda · Troco R\$ 0,10/ })).toBeEnabled()
        for (const forma of ['DEBITO', 'CREDITO', 'VOUCHER', 'DINHEIRO']) {
            await expect(page.getByText(forma, { exact: true })).toBeVisible()
        }
        await page.keyboard.press('F10')
        await expect(page.getByText('Imprimir o comprovante para o cliente?')).toBeVisible()
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
