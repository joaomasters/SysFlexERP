import { test, expect } from '../../fixtures'
import { fileURLToPath } from 'node:url'

const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }
// Certificado autoassinado, só para teste (CN "LOJA TESTE LTDA:11222333000181", senha senha123)
const PFX = fileURLToPath(new URL('../../support/a1-teste.pfx', import.meta.url))

test.describe('Emissão de notas (NFC-e / NF-e)', () => {
    test.describe.configure({ mode: 'serial' })

    test('Deve começar no cupom não fiscal, sem deixar ligar a NFC-e com pendências', async ({ page }) => {
        await page.goto('/fiscal/emissao')
        await expect(page.getByText('O PDV continua no cupom não fiscal')).toBeVisible()
        await expect(page.getByLabel('Emitir NFC-e (modelo 65)')).toBeDisabled()
        await expect(page.getByText('Ainda não há provedor de comunicação com a SEFAZ').first()).toBeVisible()
    })

    test('Deve salvar o emitente do Paraná e o certificado A1, e as pendências diminuem', async ({ page, request }) => {
        await page.goto('/fiscal/emissao')
        await page.getByLabel('Razão social *').fill('Loja Teste Ltda')
        await page.getByLabel('CNPJ *').fill('11.222.333/0001-81')
        await page.getByLabel('Inscrição Estadual (PR) *').fill('123.45678-50')
        await page.getByLabel('Logradouro *').fill('Rua XV de Novembro')
        await page.getByLabel('Número *').fill('100')
        await page.getByLabel('Bairro *').fill('Centro')
        await page.getByLabel('Município *').fill('Rolândia')
        await page.getByLabel('Código IBGE *').fill('4122404')
        await page.getByLabel('CEP *').fill('86600-000')
        await page.getByLabel('Identificador').fill('000001')
        await page.getByLabel('Código (CSC)').fill('CSC-DE-TESTE-123')
        await page.getByRole('button', { name: 'Salvar configuração' }).click()
        await expect(page.getByText('Configuração fiscal salva.')).toBeVisible()

        await page.getByLabel('Senha do certificado').fill('senha123')
        await page.locator('input[type="file"]').setInputFiles(PFX)
        await expect(page.getByText('Certificado digital salvo.')).toBeVisible()
        await expect(page.getByText('LOJA TESTE LTDA:11222333000181')).toBeVisible()

        // Sobra só o provedor da SEFAZ, que ainda não existe
        const config = await (await request.get('/api/fiscal/configuracao', { headers })).json()
        expect(config.pendenciasNfe).toEqual([expect.stringContaining('provedor')])
        expect(config.pendenciasNfce).toEqual([expect.stringContaining('provedor')])
        expect(JSON.stringify(config)).not.toContain('CSC-DE-TESTE-123')
        expect(config.cscConfigurado).toBe(true)
        expect(config.cnpj).toBe('11222333000181')

        // A API também recusa ligar com pendência
        const ligar = await request.put('/api/fiscal/configuracao', { headers, data: { ...config, emitirNfce: true } })
        expect(ligar.status()).toBe(422)
    })

    test('Deve recusar certificado com senha errada', async ({ page }) => {
        await page.goto('/fiscal/emissao')
        await page.getByLabel('Senha do certificado').fill('errada')
        await page.locator('input[type="file"]').setInputFiles(PFX)
        await expect(page.getByText(/confira o arquivo .pfx e a senha/)).toBeVisible()
    })

    test('Deve listar os documentos fiscais (vazio enquanto não há emissão)', async ({ page }) => {
        await page.goto('/fiscal/emissao')
        await page.getByRole('button', { name: 'Documentos emitidos' }).click()
        await expect(page.getByText('Nenhuma nota emitida ainda.')).toBeVisible()
    })
})
