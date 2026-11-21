import { test, expect } from '../../fixtures'

/*
 Saneamento cadastral fiscal (contábil e fiscal, etapa 1): as contagens saem do banco na hora,
 e o CNPJ alfanumérico (Receita, jul/2026) é aceito no cadastro com a mesma regra do backend.
*/
const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }
const sufixo = Date.now().toString(36).slice(-5).toUpperCase()

test.describe('Saneamento cadastral fiscal', () => {
    test('Produto sem NCM e fornecedor sem CNPJ aparecem com o motivo', async ({ page, request }) => {
        const p = await request.post('/api/estoque/produtos', { headers, data: { nome: `Sem NCM ${sufixo}`, unidadeMedida: 'KG', tipoProduto: 'INDUSTRIALIZADO', precoVenda: 10 } })
        expect(p.ok(), await p.text()).toBeTruthy()
        const f = await request.post('/api/estoque/fornecedores', { headers, data: { nome: `Fornecedor sem doc ${sufixo}`, tipoPessoa: 'PJ' } })
        expect(f.ok(), await f.text()).toBeTruthy()

        await page.goto('/fiscal/emissao')
        await page.getByRole('button', { name: 'Saneamento cadastral' }).click()
        const produtos = page.getByLabel('Produtos ativos sem dados fiscais')
        await expect(produtos.getByText('Bloqueia emissão')).toBeVisible()
        // a lista mostra até 20 exemplos: confere pela API que o produto novo foi contado
        const s = await (await request.get('/api/fiscal/saneamento', { headers })).json()
        const grupo = s.grupos.find((g: { codigo: string }) => g.codigo === 'PRODUTO_FISCAL')
        expect(grupo.quantidade).toBeGreaterThan(0)
        const forn = s.grupos.find((g: { codigo: string }) => g.codigo === 'FORNECEDOR_SEM_DOCUMENTO')
        expect(forn.quantidade).toBeGreaterThan(0)
        await expect(page.getByLabel('Fornecedores sem CPF/CNPJ')).toContainText('Atenção')
    })

    test('Fornecedor com CNPJ alfanumérico é aceito; dígito errado é recusado', async ({ page, request }) => {
        await page.goto('/estoque/fornecedores')
        await page.getByRole('button', { name: 'Novo Fornecedor' }).click()
        // os rótulos do formulário não são <label for>: acha o campo pelo texto do rótulo
        const campo = (rotulo: string) => page.getByText(rotulo, { exact: true }).locator('..').getByRole('textbox')
        await campo('Nome / Razão social *').fill(`Alfa ${sufixo}`)
        await campo('CNPJ *').fill('12abc34501de35')
        await expect(campo('CNPJ *')).toHaveValue('12.ABC.345/01DE-35')
        await campo('Telefone *').fill('43999990000')
        await campo('Endereço *').fill('Rua A, 1')
        await page.getByRole('button', { name: 'Salvar', exact: true }).click()
        await expect(page.getByRole('cell', { name: '12.ABC.345/01DE-35' })).toBeVisible()

        const errado = await request.post('/api/estoque/fornecedores', { headers, data: { nome: `Alfa errado ${sufixo}`, tipoPessoa: 'PJ', cpfCnpj: '12.ABC.345/01DE-36' } })
        expect(errado.status()).toBe(422)
        expect((await errado.json()).message).toContain('CNPJ inválido')
    })
})
