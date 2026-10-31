import { test, expect } from '../../../fixtures'
import { faker } from '@faker-js/faker'
import type { Page } from '@playwright/test'

const eanAleatorio = () => '789' + faker.string.numeric(10)

/** Leitor USB: digita muito rápido e termina com Enter, com o foco fora de qualquer campo. */
async function bipar(page: Page, codigo: string) {
    await page.keyboard.type(codigo, { delay: 5 })
    await page.keyboard.press('Enter')
}

async function criarProduto(page: Page, dados: Record<string, unknown>) {
    const token = JSON.parse(await page.evaluate(() => localStorage.getItem('acougue_sessao')!)).token
    const resp = await page.request.post('/api/estoque/produtos', {
        headers: { Authorization: `Bearer ${token}` },
        data: { unidadeMedida: 'UN', tipoProduto: 'BEBIDA', precoVenda: 10, estoqueMinimo: 0, ...dados },
    })
    expect(resp.ok()).toBeTruthy()
    return resp.json()
}

test.describe('Código de barras no cadastro', () => {
    test('Não deve permitir dois produtos com o mesmo código de barras', async ({ page, produtosPage, toast }) => {
        await produtosPage.abrir()
        const ean = eanAleatorio()
        const primeiro = await criarProduto(page, { nome: `Primeiro ${faker.string.uuid()}`, ean13: ean })

        await produtosPage.btnNovoProduto.click()
        await page.locator('input[name="nome"]').fill(`Segundo ${faker.string.uuid()}`)
        await page.locator('input[name="precoVenda"]').fill('1000')
        await page.locator('input[name="ean13"]').fill(ean)
        await page.getByRole('button', { name: 'Salvar Produto' }).click()

        await expect(toast.comMensagem(`já cadastrado no produto "${primeiro.nome}"`)).toBeVisible()
        await expect(page.getByRole('button', { name: 'Salvar Produto' })).toBeVisible()
    })
})

test.describe('Tipos de produto', () => {
    test('Deve mostrar todos os tipos e gravar um tipo novo', async ({ page, produtosPage, toast }) => {
        await produtosPage.abrir()
        await produtosPage.btnNovoProduto.click()

        const tipo = page.locator('select[name="tipoProduto"]')
        const opcoes = await tipo.locator('option').allTextContents()
        expect(opcoes).toEqual(expect.arrayContaining([
            'Corte de Carne', 'Bebida', 'Alimento', 'Mercearia', 'Hortifruti', 'Frios e Laticínios',
            'Padaria', 'Congelados', 'Limpeza', 'Higiene', 'Industrializado', 'Insumo', 'Outros',
        ]))

        const nome = `Queijo ${faker.string.uuid()}`
        await page.locator('input[name="nome"]').fill(nome)
        await page.locator('input[name="precoVenda"]').fill('1000')
        await tipo.selectOption('FRIOS_LATICINIOS')
        await page.getByRole('button', { name: 'Salvar Produto' }).click()

        await expect(toast.comMensagem('Produto criado!')).toBeVisible()
        await expect(produtosPage.linha(nome)).toContainText('Frios e Laticínios')
    })
})

test.describe('Busca de produtos', () => {
    test('Deve achar por nome sem acento, por código interno e pelo início do código de barras', async ({ page, produtosPage }) => {
        await produtosPage.abrir()
        const ean = eanAleatorio()
        const sufixo = faker.string.alphanumeric(8)
        const produto = await criarProduto(page, { nome: `Limão Taiti ${sufixo}`, ean13: ean })
        await produtosPage.abrir()
        const busca = page.getByPlaceholder('Buscar por nome, código ou código de barras...')

        await busca.fill(`limao taiti ${sufixo}`)
        await expect(produtosPage.linha(produto.nome)).toHaveCount(1)

        await busca.fill(ean)
        await expect(produtosPage.linha(produto.nome)).toHaveCount(1)

        await busca.fill(ean.slice(0, 11))
        await expect(produtosPage.linha(produto.nome)).toHaveCount(1)

        await busca.fill(produto.codigoInterno)
        await expect(produtosPage.linha(produto.nome)).toHaveCount(1)

        await busca.fill('0000000000000')
        await expect(page.getByText('Nenhum produto encontrado.')).toBeVisible()
    })

    test('Deve achar com o leitor, e o segundo código bipado substitui o primeiro', async ({ page, produtosPage }) => {
        await produtosPage.abrir()
        const a = await criarProduto(page, { nome: `Bipado A ${faker.string.uuid()}`, ean13: eanAleatorio() })
        const b = await criarProduto(page, { nome: `Bipado B ${faker.string.uuid()}`, ean13: eanAleatorio() })
        await produtosPage.abrir()
        await expect(produtosPage.linha(a.nome)).toHaveCount(1)
        const busca = page.getByPlaceholder('Buscar por nome, código ou código de barras...')

        // Foco fora do campo
        await page.getByRole('heading', { name: 'Produtos' }).click()
        await bipar(page, a.ean13)
        await expect(busca).toHaveValue(a.ean13)
        await expect(produtosPage.linha(a.nome)).toHaveCount(1)
        await expect(produtosPage.linha(b.nome)).toHaveCount(0)

        // Foco já no campo: os dígitos entram direto nele
        await expect(busca).toBeFocused()
        await bipar(page, b.ean13)
        await expect(busca).toHaveValue(b.ean13)
        await expect(produtosPage.linha(b.nome)).toHaveCount(1)
        await expect(produtosPage.linha(a.nome)).toHaveCount(0)
    })

    test('Deve achar produto de balança pela etiqueta', async ({ page, produtosPage }) => {
        await produtosPage.abrir()
        const plu = faker.number.int({ min: 100000, max: 899999 })
        const produto = await criarProduto(page, {
            nome: `Pesado ${faker.string.uuid()}`, unidadeMedida: 'KG', tipoProduto: 'CORTE', codigoBalanca: plu,
        })
        await produtosPage.abrir()

        await page.getByPlaceholder('Buscar por nome, código ou código de barras...').fill(`2${plu}012340`)

        await expect(produtosPage.linha(produto.nome)).toHaveCount(1)
    })
})

test.describe('Relatório de estoque na tela de Produtos', () => {
    test('Deve abrir, filtrar por tipo e buscar por código de barras', async ({ page, produtosPage }) => {
        await produtosPage.abrir()
        const ean = eanAleatorio()
        const higiene = await criarProduto(page, { nome: `Sabonete ${faker.string.uuid()}`, tipoProduto: 'HIGIENE', ean13: ean })
        const limpeza = await criarProduto(page, { nome: `Detergente ${faker.string.uuid()}`, tipoProduto: 'LIMPEZA' })
        await produtosPage.abrir()

        await page.getByRole('button', { name: 'Relatório de Estoque' }).click()
        const area = page.locator('#relatorio-estoque-print')
        await expect(area.getByRole('row').filter({ hasText: higiene.nome })).toContainText('Higiene')

        await page.locator('select').filter({ hasText: 'Todos' }).selectOption('LIMPEZA')
        await expect(area.getByRole('row').filter({ hasText: limpeza.nome })).toHaveCount(1)
        await expect(area.getByRole('row').filter({ hasText: higiene.nome })).toHaveCount(0)

        await page.locator('select').filter({ hasText: 'Todos' }).selectOption('')
        await page.getByPlaceholder('Buscar por nome, marca, fornecedor ou código de barras...').fill(ean)
        await expect(area.getByRole('row').filter({ hasText: higiene.nome })).toHaveCount(1)
        await expect(area.getByRole('row').filter({ hasText: limpeza.nome })).toHaveCount(0)

        await page.getByRole('button', { name: 'Fechar' }).click()
        await expect(area).toHaveCount(0)
    })
})
