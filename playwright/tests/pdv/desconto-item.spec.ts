import { test, expect } from '../../fixtures'
import { faker } from '@faker-js/faker'
import type { APIRequestContext, Browser, Page } from '@playwright/test'

const brl = (v: number) => `R$ ${v.toFixed(2).replace('.', ',')}`

/** Comanda aberta pela API, com um produto de estoque real dos dados iniciais. */
async function abrirComanda(request: APIRequestContext, token: string, quantidade = 2, segundoItem = false) {
    const headers = { Authorization: `Bearer ${token}` }
    const usuarioId = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString()).usuarioId
    let caixa = await request.get(`/api/pdv/caixa/aberto?operadorId=${usuarioId}`, { headers })
    if (!caixa.ok()) caixa = await request.post('/api/pdv/caixa/abrir?valorAbertura=100', { headers })
    const caixaId = (await caixa.json()).id

    const produtos = await (await request.get('/api/estoque/produtos', { headers })).json()
    const produto = produtos.find((p: { nome: string }) => p.nome === 'Picanha')

    const venda = await (await request.post('/api/pdv/vendas/abrir', { headers, data: { caixaId } })).json()
    const comItem = await (await request.post(`/api/pdv/vendas/${venda.id}/itens`, {
        headers, data: { produtoId: produto.id, quantidade, tipoEntrada: 'MANUAL' },
    })).json()
    if (segundoItem) {
        const outro = produtos.find((p: { nome: string }) => p.nome !== 'Picanha')
        await request.post(`/api/pdv/vendas/${venda.id}/itens`, {
            headers, data: { produtoId: outro.id, quantidade: 1, tipoEntrada: 'MANUAL' },
        })
        return { vendaId: venda.id as number, total: comItem.total as number, nomeSegundo: outro.nome as string }
    }
    return { vendaId: venda.id as number, total: comItem.total as number, nomeSegundo: '' }
}

async function entrarNaComanda(page: Page, vendaId: number) {
    await page.goto('/pdv')
    await page.getByRole('button', { name: new RegExp(`Comanda #${vendaId}\\b`) }).click()
    await expect(page.getByRole('button', { name: 'Desconto no item' }).first()).toBeVisible()
}

const modal = (page: Page) => page.locator('form').filter({ hasText: 'Desconto no item' })

test.describe('Desconto no item do PDV', () => {
    test('Deve aplicar em R$, trocar em % e remover', async ({ page, request }) => {
        const { vendaId, total } = await abrirComanda(request, process.env.E2E_TOKEN!)
        await entrarNaComanda(page, vendaId)

        await page.getByRole('button', { name: 'Desconto no item' }).click()
        await modal(page).locator('input').fill('500')
        await expect(modal(page)).toContainText(`-${brl(5)}`)
        await expect(modal(page)).toContainText(brl(total - 5))
        await modal(page).getByRole('button', { name: 'Aplicar' }).click()

        await expect(modal(page)).toHaveCount(0)
        await expect(page.getByRole('row').filter({ hasText: 'Picanha' })).toContainText(`-${brl(5)}`)
        await expect(page.getByText('Total a Pagar').locator('..')).toContainText((total - 5).toFixed(2).replace('.', ','))

        // Em %: calcula sobre o valor cheio, não sobre o que já tinha desconto
        await page.getByRole('button', { name: 'Desconto no item' }).click()
        await expect(modal(page)).toContainText(brl(total))
        await modal(page).getByRole('button', { name: '%', exact: true }).click()
        await modal(page).locator('input').fill('10')
        const dezPorCento = Math.round(total * 10) / 100
        await expect(modal(page)).toContainText(`-${brl(dezPorCento)}`)
        await modal(page).getByRole('button', { name: 'Aplicar' }).click()
        await expect(page.getByRole('row').filter({ hasText: 'Picanha' })).toContainText(`-${brl(dezPorCento)}`)

        await page.getByRole('button', { name: 'Desconto no item' }).click()
        await modal(page).getByRole('button', { name: 'Remover' }).click()
        await expect(modal(page)).toHaveCount(0)
        await expect(page.getByRole('row').filter({ hasText: 'Picanha' })).not.toContainText('-R$')
    })

    test('Deve travar desconto igual ou maior que o item, e o teclado não age por trás do modal', async ({ page, request }) => {
        const { vendaId } = await abrirComanda(request, process.env.E2E_TOKEN!)
        await entrarNaComanda(page, vendaId)

        await page.getByRole('button', { name: 'Desconto no item' }).click()
        await modal(page).getByRole('button', { name: '%', exact: true }).click()
        await modal(page).locator('input').fill('100')
        await expect(modal(page).getByRole('button', { name: 'Aplicar' })).toBeDisabled()
        await expect(modal(page)).toContainText('menor que o valor do item')

        await page.keyboard.press('F10')
        await page.keyboard.press('F4')
        await expect(modal(page)).toBeVisible()
        await expect(page.getByText('Total a Pagar')).toBeVisible()

        await page.keyboard.press('Escape')
        await expect(modal(page)).toHaveCount(0)
        await expect(page.getByRole('button', { name: 'Desconto no item' })).toBeVisible()
    })
})

test.describe('Atalhos do item no PDV', () => {
    const linhaSelecionada = (page: Page) => page.locator('tr[aria-selected="true"]')

    test('F6 abre o desconto do item selecionado, e as setas trocam o item', async ({ page, request }) => {
        const { vendaId, nomeSegundo } = await abrirComanda(request, process.env.E2E_TOKEN!, 2, true)
        await entrarNaComanda(page, vendaId)

        // Por padrão, o último lançado
        await expect(linhaSelecionada(page)).toContainText(nomeSegundo)
        await page.keyboard.press('F6')
        await expect(modal(page)).toContainText(nomeSegundo)
        await page.keyboard.press('Escape')

        await page.keyboard.press('ArrowUp')
        await expect(linhaSelecionada(page)).toContainText('Picanha')
        await page.keyboard.press('F6')
        await expect(modal(page)).toContainText('Picanha')
        await modal(page).locator('input').fill('100')
        await page.keyboard.press('Enter')
        await expect(modal(page)).toHaveCount(0)

        // Item com desconto continua na mesma posição, e selecionado
        const linhas = page.locator('tbody tr')
        await expect(linhas.nth(0)).toContainText('Picanha')
        await expect(linhas.nth(1)).toContainText(nomeSegundo)
        await expect(linhaSelecionada(page)).toContainText('Picanha')

        // PageDown funciona mesmo com o foco no campo de busca
        await page.keyboard.press('F2')
        await page.keyboard.press('Escape')
        await page.keyboard.press('PageDown')
        await expect(linhaSelecionada(page)).toContainText(nomeSegundo)

        // Clique também escolhe o item
        await linhas.nth(0).locator('td').nth(1).click()
        await expect(linhaSelecionada(page)).toContainText('Picanha')
        await page.screenshot({ path: test.info().outputPath('itens.png') })
    })

    test('F8 pede confirmação: Esc mantém e Enter exclui', async ({ page, request }) => {
        const { vendaId, nomeSegundo } = await abrirComanda(request, process.env.E2E_TOKEN!, 2, true)
        await entrarNaComanda(page, vendaId)
        const linhas = page.locator('tbody tr')

        await page.keyboard.press('F8')
        await expect(page.getByRole('heading', { name: 'Excluir item?' })).toBeVisible()
        await page.screenshot({ path: test.info().outputPath('excluir.png') })
        await page.keyboard.press('Escape')
        await expect(page.getByRole('heading', { name: 'Excluir item?' })).toHaveCount(0)
        await expect(linhas).toHaveCount(2)

        await page.keyboard.press('F8')
        await page.keyboard.press('F10')
        await expect(page.getByRole('heading', { name: 'Pagamento' })).toHaveCount(0)
        await page.keyboard.press('Enter')
        await expect(linhas).toHaveCount(1)
        await expect(linhas.nth(0)).toContainText('Picanha')
        await expect(linhas.nth(0)).not.toContainText(nomeSegundo)
    })

    test('Atalhos não agem com o Pagamento aberto', async ({ page, request }) => {
        const { vendaId } = await abrirComanda(request, process.env.E2E_TOKEN!)
        await entrarNaComanda(page, vendaId)

        await page.keyboard.press('F10')
        await expect(page.getByRole('heading', { name: 'Pagamento' })).toBeVisible()
        await page.keyboard.press('F6')
        await page.keyboard.press('F8')
        await expect(modal(page)).toHaveCount(0)
        await expect(page.getByRole('heading', { name: 'Excluir item?' })).toHaveCount(0)
    })
})

test.describe('Cupom com desconto', () => {
    test.describe.configure({ mode: 'serial' })

    for (const largura of [80, 58]) {
        test(`Deve imprimir em Arial, com a conta fechando e sem estourar a bobina de ${largura} mm`, async ({ page, request }) => {
            const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }
            const config = await (await request.get('/api/comprovante/configuracao', { headers })).json()
            await request.put('/api/comprovante/configuracao', { headers, data: { ...config, larguraPapelMm: largura } })

            try {
                // Intercepta a impressão do iframe do cupom e guarda o HTML que iria para o papel
                await page.addInitScript(() => {
                    window.print = () => {
                        (window.parent as unknown as { __cupom: string }).__cupom = document.documentElement.outerHTML
                    }
                })
                const { vendaId, total } = await abrirComanda(request, process.env.E2E_TOKEN!)
                await entrarNaComanda(page, vendaId)

                await page.getByRole('button', { name: 'Desconto no item' }).click()
                await modal(page).locator('input').fill('500')
                await modal(page).getByRole('button', { name: 'Aplicar' }).click()
                await expect(modal(page)).toHaveCount(0)

                await page.keyboard.press('F10')
                await page.getByRole('button', { name: '✓' }).click()
                await page.getByRole('button', { name: /Finalizar Venda/ }).click()

                await expect(page.getByText(`Venda #${vendaId} finalizada`)).toBeVisible()
                await page.getByRole('button', { name: /^Imprimir/ }).click()
                await page.waitForFunction(() => Boolean((window as unknown as { __cupom?: string }).__cupom))

                const html = await page.evaluate(() => (window as unknown as { __cupom: string }).__cupom)
                const cupom = await page.context().newPage()
                await cupom.setContent(`<!doctype html>${html}`)
                const recibo = cupom.locator('.recibo')
                const texto = (await recibo.innerText()).replace(/\s+/g, ' ')
                const num = (v: number) => v.toFixed(2).replace('.', ',')

                expect(texto).toContain(`Sub Total: ${num(total)}`)
                expect(texto).toContain('Desconto -5,00')
                expect(texto).toContain('Desconto nos itens: 5,00')
                expect(texto).toContain(`Total: ${num(total - 5)}`)

                await recibo.screenshot({ path: test.info().outputPath(`cupom-${largura}.png`) })
                expect(await recibo.evaluate(el => getComputedStyle(el).fontFamily)).toContain('Arial')
                expect(await recibo.evaluate(el => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(0)
            } finally {
                await request.put('/api/comprovante/configuracao', { headers, data: config })
            }
        })
    }
})

test.describe('Permissão de desconto', () => {
    async function logarComoCaixa(browser: Browser, request: APIRequestContext, baseURL: string) {
        const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }
        const login = `caixa${faker.string.alphanumeric(8).toLowerCase()}`
        const senha = 'Caixa@12345'
        const criado = await request.post('/api/usuarios', {
            headers, data: { nome: 'Caixa E2E', login, senha, perfilId: 4, ativo: true },
        })
        expect(criado.ok()).toBeTruthy()
        const sessao = await (await request.post('/api/auth/login', {
            headers: { 'X-Tenant-Codigo': process.env.E2E_TENANT! }, data: { username: login, password: senha },
        })).json()
        const contexto = await browser.newContext({
            baseURL,
            storageState: {
                cookies: [],
                origins: [{ origin: new URL(baseURL).origin, localStorage: [{ name: 'acougue_sessao', value: JSON.stringify(sessao) }] }],
            },
        })
        return { pagina: await contexto.newPage(), token: sessao.token as string }
    }

    test('Perfil CAIXA vê o botão desabilitado', async ({ browser, request, baseURL }) => {
        const { pagina, token } = await logarComoCaixa(browser, request, baseURL!)
        const { vendaId } = await abrirComanda(request, token)

        await pagina.goto('/pdv')
        await pagina.getByRole('button', { name: new RegExp(`Comanda #${vendaId}\\b`) }).click()

        const botao = pagina.getByRole('button', { name: 'Desconto no item' })
        await expect(botao).toBeDisabled()
        await expect(botao).toHaveAttribute('title', 'Seu perfil não pode dar desconto')

        await pagina.keyboard.press('F6')
        await expect(pagina.getByRole('status').filter({ hasText: 'Seu perfil não pode dar desconto.' })).toBeVisible()
        await expect(modal(pagina)).toHaveCount(0)
    })
})
