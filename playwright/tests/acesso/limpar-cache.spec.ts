import { test, expect } from '../../fixtures'
import type { Page } from '@playwright/test'

const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }

/** Marca a janela: se a tela recarregar, a marca some. */
const marcar = (p: Page) => p.evaluate(() => { (window as unknown as { marca: number }).marca = 1 })
const recarregou = (p: Page) => p.evaluate(() => (window as unknown as { marca?: number }).marca === undefined)

test.describe('Limpar cache (SUPER_ADMIN)', () => {
    test('Deve recarregar os outros computadores da empresa, mas não o PDV no meio da venda', async ({ page, browser, request, confirmDialog }) => {
        // Limpar o cache recarrega TODOS os navegadores abertos da empresa, inclusive os de
        // outros testes rodando em paralelo. Rode à parte: E2E_LIMPAR_CACHE=1 --workers=1
        test.skip(!process.env.E2E_LIMPAR_CACHE, 'Defina E2E_LIMPAR_CACHE=1 e rode sozinho')
        // Dois "computadores" com a mesma sessão, cada um com o seu navegador
        const estado = await page.context().storageState()
        const outro = await (await browser.newContext({ storageState: estado })).newPage()
        const caixa = await (await browser.newContext({ storageState: estado })).newPage()
        await outro.goto('/inicio')
        await caixa.goto('/pdv')
        await expect(outro.getByRole('navigation').getByRole('link', { name: 'Produtos' })).toBeVisible()
        await expect(caixa.getByText('Nova Comanda')).toBeVisible()
        await marcar(outro)
        await marcar(caixa)

        const antes = (await (await request.get('/api/manutencao/cache', { headers })).json()).versao

        await page.goto('/acesso/manutencao')
        await expect(page.getByText(/Última limpeza:/)).toBeVisible()
        await page.getByRole('button', { name: 'Limpar cache' }).click()
        await expect(confirmDialog.titulo('Limpar o cache da empresa?')).toBeVisible()
        // O botão do modal vem depois do da página
        await page.getByRole('button', { name: 'Limpar cache', exact: true }).last().click()
        await expect(page.getByText('Cache limpo.')).toBeVisible()

        const depois = await (await request.get('/api/manutencao/cache', { headers })).json()
        expect(depois.versao).toBe(antes + 1)
        expect(depois.limpoPor).toBeTruthy()

        // O outro computador recarrega na próxima chamada à API, e continua logado
        await outro.getByRole('navigation').getByRole('link', { name: 'Produtos' }).click()
        await expect.poll(() => recarregou(outro), { timeout: 15_000 }).toBe(true)
        await expect(outro).not.toHaveURL(/\/login/)

        // O PDV só avisa (pode ter cobrança na maquininha): a busca de cliente chama a API
        await caixa.getByRole('button', { name: /Nova Comanda/ }).click()
        await caixa.getByPlaceholder('Nome do cliente...').fill('Maria')
        await expect(caixa.getByText(/limpou o cache do sistema/)).toBeVisible()
        expect(await recarregou(caixa)).toBe(false)
    })

    test('Deve mostrar Manutenção no menu do SUPER_ADMIN', async ({ page }) => {
        await page.goto('/inicio')
        await expect(page.getByRole('navigation').getByRole('link', { name: 'Manutenção' })).toBeVisible()
    })
})
