import { test, expect } from '../../fixtures'
import { faker } from '@faker-js/faker'
import type { APIRequestContext } from '@playwright/test'

const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }

async function buscarPorNome(request: APIRequestContext, nome: string) {
    const lista = await (await request.get('/api/estoque/produtos', { headers })).json()
    return lista.find((p: { nome: string }) => p.nome === nome)
}

test.describe('Cadastro de produto: estoque máximo e informações fiscais', () => {
    test('Deve gravar a aba fiscal, recarregar na edição e não perder ao editar outro campo', async ({ page, request }) => {
        const nome = `Fraldinha fiscal ${faker.string.alphanumeric(8)}`

        await page.goto('/produtos/novo')
        await page.locator('#produto-ean13').fill('789' + faker.string.numeric(10))
        await page.locator('#produto-nome').fill(nome)
        await page.locator('#produto-precoVenda').fill('5990')

        await page.getByRole('tab', { name: 'Informações Fiscais' }).click()
        await page.locator('#produto-ncm').fill('02013000')
        await page.locator('#produto-cest').fill('1700100')
        await page.locator('#produto-cfop').fill('5102')
        await page.locator('#produto-origem').selectOption('2')
        await page.locator('#produto-csosn').selectOption('102')
        await page.getByRole('button', { name: 'Salvar Produto' }).click()
        await expect(page).toHaveURL(/\/produtos$/)

        const criado = await buscarPorNome(request, nome)
        expect(criado).toMatchObject({ ncm: '02013000', cest: '1700100', cfop: '5102', origem: '2', csosn: '102' })

        // Edição: os campos voltam preenchidos
        await page.goto(`/produtos/${criado.id}/editar`)
        await page.getByRole('tab', { name: 'Informações Fiscais' }).click()
        await expect(page.locator('#produto-ncm')).toHaveValue('02013000')
        await expect(page.locator('#produto-cest')).toHaveValue('1700100')
        await expect(page.locator('#produto-cfop')).toHaveValue('5102')
        await expect(page.locator('#produto-origem')).toHaveValue('2')
        await expect(page.locator('#produto-csosn')).toHaveValue('102')

        // Muda só o preço: o fiscal tem de continuar lá
        await page.getByRole('tab', { name: 'Informações Básicas' }).click()
        await page.locator('#produto-precoVenda').fill('6490')
        await page.getByRole('button', { name: 'Salvar Produto' }).click()
        await expect(page).toHaveURL(/\/produtos$/)

        const editado = await (await request.get(`/api/estoque/produtos/${criado.id}`, { headers })).json()
        expect(editado.precoVenda).toBeCloseTo(64.9, 2)
        expect(editado).toMatchObject({ ncm: '02013000', cest: '1700100', cfop: '5102', origem: '2', csosn: '102' })
    })

    test('Deve gravar e devolver o estoque máximo, e recusar máximo menor que o mínimo', async ({ request }) => {
        const base = { unidadeMedida: 'KG', tipoProduto: 'CORTE', precoVenda: 50, estoqueMinimo: 5 }

        const ok = await request.post('/api/estoque/produtos', {
            headers, data: { ...base, nome: `Máximo ${faker.string.uuid()}`, estoqueMaximo: 40 },
        })
        expect(ok.ok()).toBeTruthy()
        const produto = await ok.json()
        expect(produto.estoqueMaximo).toBe(40)
        expect(produto.origem).toBe('0')

        const menor = await request.post('/api/estoque/produtos', {
            headers, data: { ...base, nome: `Máximo errado ${faker.string.uuid()}`, estoqueMaximo: 2 },
        })
        expect(menor.status()).toBe(422)
        expect((await menor.json()).message).toContain('estoque máximo')
    })

    test('Deve recusar formatos fiscais inválidos com mensagem por campo', async ({ request }) => {
        const resp = await request.post('/api/estoque/produtos', {
            headers,
            data: {
                nome: `Fiscal inválido ${faker.string.uuid()}`, unidadeMedida: 'UN', tipoProduto: 'BEBIDA', precoVenda: 10,
                ncm: '123', cest: '12', cfop: '9999', origem: '9', csosn: '104',
            },
        })
        expect(resp.status()).toBe(400)
        const erros = await resp.json()
        expect(erros).toMatchObject({
            ncm: 'O NCM deve ter 8 dígitos.',
            cest: 'O CEST deve ter 7 dígitos.',
            cfop: 'Informe um CFOP válido (4 dígitos).',
            origem: 'Origem da mercadoria inválida.',
            csosn: 'CSOSN inválido.',
        })
    })
})
