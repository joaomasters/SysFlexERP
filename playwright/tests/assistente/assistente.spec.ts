import { test, expect } from '../../fixtures'
import { faker } from '@faker-js/faker'
import type { Page } from '@playwright/test'

// Precisa do backend apontando para support/gemini-falso.mjs (GEMINI_URL) e da porta dele aqui
const falso = process.env.E2E_GEMINI_FALSO
const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }

async function perguntar(page: Page, pergunta: string) {
    const dialogo = page.getByRole('dialog', { name: 'Assistente' })
    await dialogo.getByPlaceholder(/qual o custo/).fill(pergunta)
    await dialogo.getByRole('button', { name: 'Enviar pergunta' }).click()
    const resposta = dialogo.getByText(/^Resultado: /).last()
    await expect(resposta).toBeVisible()
    // O falso devolve como texto o JSON que o backend consultou
    return JSON.parse((await resposta.innerText()).replace('Resultado: ', ''))
}

test.describe('Assistente', () => {
    test.skip(!falso, 'Defina E2E_GEMINI_FALSO com o endereço do support/gemini-falso.mjs')

    test('Deve responder com os mesmos números do Relatório de Vendas', async ({ page, request }) => {
        const hoje = new Date().toLocaleDateString('sv-SE', { timeZone: 'America/Sao_Paulo' })
        const relatorio = await (await request.get(
            `/api/financeiro/relatorios/vendas?inicio=${hoje}&fim=${hoje}`, { headers })).json()

        await page.goto('/inicio')
        await page.getByRole('button', { name: 'Assistente' }).click()
        await page.getByRole('button', { name: 'Qual meu lucro hoje?' }).click()

        const texto = await page.getByRole('dialog', { name: 'Assistente' }).getByText(/^Resultado: /).innerText()
        const resumo = JSON.parse(texto.replace('Resultado: ', ''))
        await expect(page.getByText('Consultou: Resumo de vendas')).toBeVisible()
        expect(resumo.faturamento).toBeCloseTo(Number(relatorio.totalVendas), 2)
        expect(resumo.custo_produtos_vendidos).toBeCloseTo(Number(relatorio.totalCMV), 2)
        expect(resumo.quantidade_vendas).toBe(relatorio.quantidadeVendas)

        const ultima = await (await request.get(`${falso}/ultima`)).json()
        expect(ultima.chave).toBeTruthy()
        expect(ultima.corpo.store).toBe(false)
        expect(ultima.corpo.tools.map((t: { name: string }) => t.name)).toEqual(
            ['resumo_vendas', 'produtos_mais_vendidos', 'custo_produto', 'estoque_baixo'])
        // O raciocínio volta com a assinatura, e o resultado vai com o mesmo id da chamada
        expect(ultima.corpo.input.some((p: { signature?: string }) => p.signature === 'assinatura-falsa')).toBe(true)
        expect(ultima.corpo.input.find((p: { type: string }) => p.type === 'function_result').call_id).toBe('call_1')
    })

    test('Deve consultar custo, estoque baixo e mais vendidos no banco da empresa', async ({ page, request }) => {
        const nome = `Costela ${faker.string.alphanumeric(8)}`
        const criado = await request.post('/api/estoque/produtos', {
            headers,
            data: { nome, unidadeMedida: 'KG', tipoProduto: 'CORTE', precoVenda: 50, precoCusto: 30, estoqueMinimo: 5 },
        })
        expect(criado.ok()).toBeTruthy()

        await page.goto('/inicio')
        await page.getByRole('button', { name: 'Assistente' }).click()

        const custo = await perguntar(page, `qual o custo da ${nome}?`)
        expect(custo.produtos).toHaveLength(1)
        expect(custo.produtos[0]).toMatchObject({ produto: nome, preco_custo: 30, preco_venda: 50, margem_percentual: 40 })

        const baixo = await perguntar(page, 'quais produtos estão com estoque baixo?')
        expect(baixo.erro).toBeUndefined()
        expect(Array.isArray(baixo.produtos)).toBe(true)

        const vendidos = await perguntar(page, 'quais os mais vendidos hoje?')
        expect(vendidos.erro).toBeUndefined()
        expect(vendidos.produtos.length).toBeLessThanOrEqual(5)
    })

    test('Visualização do JARVIS não mostra o assistente', async ({ page }) => {
        await page.goto('/inicio')
        await page.evaluate(() => {
            const sessao = JSON.parse(localStorage.getItem('acougue_sessao')!)
            sessionStorage.setItem('jarvis_visualizacao', JSON.stringify({ ...sessao, somenteLeitura: true, jarvis: true }))
        })
        await page.reload()
        await expect(page.getByRole('heading').first()).toBeVisible()
        await expect(page.getByRole('button', { name: 'Assistente' })).toHaveCount(0)
    })
})
