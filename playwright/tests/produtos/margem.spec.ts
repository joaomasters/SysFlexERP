import { test, expect } from '../../fixtures'

test.describe('Cadastro de produto: margem', () => {
    test('Deve calcular o preço de venda pela margem digitada, e a margem pelo preço', async ({ page }) => {
        await page.goto('/produtos/novo')
        const margem = page.locator('#produto-margem')
        const venda = page.locator('#produto-precoVenda')

        // Sem custo não há margem para calcular
        await expect(margem).toBeDisabled()

        await page.locator('#produto-precoCusto').fill('1000') // R$ 10,00 (máscara de caixa)
        await margem.fill('50')
        await expect(venda).toHaveValue(/20,00/)               // 50% sobre a venda: 10 / (1 - 0,5)

        await margem.fill('100')
        await expect(page.getByText('Use uma margem de 0 a 99,9%.')).toBeVisible()
        await expect(venda).toHaveValue(/20,00/)               // valor inválido não mexe no preço

        // O caminho de antes continua: mudar o preço atualiza a margem
        await venda.fill('2500')
        await expect(margem).toHaveValue('60,0')
    })
})
