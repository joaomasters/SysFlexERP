import { test, expect } from '../../fixtures'
import { faker } from '@faker-js/faker'
import { FISCAL_PRODUTO } from '../../support/produtoFiscal'

/*
 Regras do cadastro (descrição em MAIÚSCULAS, aba fiscal obrigatória) e a planilha fiscal do
 SUPER_ADMIN, que atualiza NCM/CFOP/CSOSN/descrição de vários produtos de uma vez.
*/
const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }

test.describe('Planilha fiscal de produtos', () => {
    test('Cadastro grava a descrição em maiúsculas e recusa produto sem NCM', async ({ request }) => {
        const nome = `biju de tapioca ${faker.string.alphanumeric(6)}`
        const ok = await request.post('/api/estoque/produtos', {
            headers, data: { ...FISCAL_PRODUTO, nome, unidadeMedida: 'UN', tipoProduto: 'MERCEARIA', precoVenda: 17 },
        })
        expect(ok.ok(), await ok.text()).toBeTruthy()
        expect((await ok.json()).nome).toBe(nome.toUpperCase())

        const semNcm = await request.post('/api/estoque/produtos', {
            headers, data: { ...FISCAL_PRODUTO, ncm: null, nome: `Sem NCM ${faker.string.uuid()}`, unidadeMedida: 'UN', tipoProduto: 'MERCEARIA', precoVenda: 17 },
        })
        expect(semNcm.status()).toBe(422)
        expect((await semNcm.json()).message).toContain('NCM')
    })

    test('Formulário: digita em maiúsculas e pede NCM e CSOSN antes de salvar', async ({ page }) => {
        await page.goto('/produtos/novo')
        await page.locator('input[name="nome"]').fill('picanha maturada')
        await expect(page.locator('input[name="nome"]')).toHaveValue('PICANHA MATURADA')
        await page.locator('input[name="ean13"]').fill('789' + faker.string.numeric(10))
        await page.locator('input[name="precoVenda"]').fill('4990')
        await page.getByRole('button', { name: 'Salvar Produto' }).click()
        await expect(page.getByRole('tab', { name: /Informações Fiscais/ })).toHaveAttribute('aria-selected', 'true')
        await expect(page.getByText('Informe o NCM.')).toBeVisible()
        await expect(page.getByText('Selecione o CSOSN.')).toBeVisible()
        await expect(page.locator('input[name="cfop"]')).toHaveValue('5102')
    })

    test('SUPER_ADMIN importa a planilha: prévia, aplica e o produto fica atualizado', async ({ page, request }) => {
        const criado = await (await request.post('/api/estoque/produtos', {
            headers, data: { ...FISCAL_PRODUTO, nome: `Linguica ${faker.string.alphanumeric(6)}`, unidadeMedida: 'KG', tipoProduto: 'CORTE', precoVenda: 30 },
        })).json()

        const csv = [
            'codigo_interno;codigo_barras;descricao;ncm;cest;cfop;origem;csosn',
            `${criado.codigoInterno};;linguiça toscana;1601.00.00;;;;500`,
            'NAO-EXISTE-XYZ;;;02013000;;;;',
        ].join('\r\n')

        await page.goto('/produtos')
        await page.getByRole('button', { name: 'Planilha fiscal' }).click()
        await page.getByTestId('planilha-fiscal-arquivo').setInputFiles({
            name: 'fiscal.csv', mimeType: 'text/csv', buffer: Buffer.from('﻿' + csv, 'utf-8'),
        })
        await expect(page.getByText('NCM → 16010000')).toBeVisible()
        await expect(page.getByText('Produto não encontrado.')).toBeVisible()
        await page.getByRole('button', { name: 'Aplicar 1 alteração(ões)' }).click()
        await expect(page.getByText('1 produto(s) atualizado(s).')).toBeVisible()

        const depois = await (await request.get(`/api/estoque/produtos/${criado.id}`, { headers })).json()
        expect(depois).toMatchObject({ nome: 'LINGUIÇA TOSCANA', ncm: '16010000', csosn: '500', cfop: '5102' })
    })
})
