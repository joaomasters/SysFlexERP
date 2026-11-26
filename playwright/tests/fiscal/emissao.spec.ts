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

    test('Deve listar os documentos fiscais (vazio enquanto não há emissão)', async ({ page, request }) => {
        // o banco de teste pode já ter NFC-e de execuções anteriores do nfce-pdv.global.spec
        const h = new Date()
        const mes = `${h.getFullYear()}-${String(h.getMonth() + 1).padStart(2, '0')}`
        const docs = await (await request.get(`/api/fiscal/documentos?mes=${mes}`, { headers })).json()
        await page.goto('/fiscal/emissao')
        await page.getByRole('button', { name: 'Documentos emitidos' }).click()
        if (docs.length === 0) await expect(page.getByText('Nenhuma nota neste mês.')).toBeVisible()
        else await expect(page.getByRole('columnheader', { name: 'Série / Número' })).toBeVisible()
    })

    test('Deve mostrar o regime tributário com vigência em vigor', async ({ page }) => {
        await page.goto('/fiscal/emissao')
        await expect(page.getByRole('heading', { name: 'Vigências do regime tributário' })).toBeVisible()
        await expect(page.getByRole('cell', { name: 'em vigor' })).toHaveCount(1)
    })

    test('Com a Focus NFe, pede token e CST de PIS/COFINS no lugar do CSC', async ({ page }) => {
        await page.goto('/fiscal/emissao')
        await page.getByLabel('Comunicação com a SEFAZ').selectOption({ label: 'Focus NFe' })
        await expect(page.getByRole('heading', { name: 'Acesso ao provedor fiscal' })).toBeVisible()
        await expect(page.getByLabel('Token de homologação')).toBeVisible()
        await expect(page.getByLabel('CST de PIS/COFINS')).toBeVisible()
        await expect(page.getByRole('heading', { name: 'CSC da NFC-e' })).toHaveCount(0)
    })

    test('Faturamento do mês: as formas de pagamento fecham com o total e o extrato baixa em CSV', async ({ page, request }) => {
        const h = new Date()
        const mes = `${h.getFullYear()}-${String(h.getMonth() + 1).padStart(2, '0')}`
        const r = await request.get(`/api/fiscal/faturamento-do-mes?mes=${mes}`, { headers })
        expect(r.ok(), await r.text()).toBeTruthy()
        const e = await r.json()
        const somaFormas = Object.values(e.totais.porForma as Record<string, number>).reduce((s, v) => s + Number(v), 0)
        expect(somaFormas).toBeCloseTo(Number(e.totais.total), 2)
        expect(Number(e.totais.totalComNfce)).toBeLessThanOrEqual(Number(e.totais.total))

        await page.goto('/fiscal/emissao')
        await page.getByRole('button', { name: 'Faturamento do mês' }).click()
        await expect(page.getByLabel('Mês')).toHaveValue(mes)
        if (e.dias.length === 0) {
            await expect(page.getByText('Nenhuma venda fechada neste mês.')).toBeVisible()
            return
        }
        await expect(page.getByRole('row').filter({ hasText: 'Total do mês' })).toContainText(String(e.totais.vendas))
        const download = page.waitForEvent('download')
        await page.getByRole('button', { name: /Baixar extrato/ }).click()
        expect((await download).suggestedFilename()).toBe(`faturamento-${mes}.csv`)
    })

    test('DANFE: prévia no Comprovante com código, quantidade de itens e tributos da Lei 12.741', async ({ page, request }) => {
        const atual = await (await request.get('/api/fiscal/configuracao', { headers })).json()
        const salvar = (percentualTributos: number | null) =>
            request.put('/api/fiscal/configuracao', { headers, data: { ...atual, cscToken: null, percentualTributos } })
        expect((await salvar(4.5)).ok()).toBeTruthy()
        expect((await salvar(101)).status()).toBe(400)
        try {
            await page.goto('/fiscal/emissao')
            await expect(page.getByLabel('Tributos aproximados no DANFE (%)')).toHaveValue('4,5')

            await page.goto('/acesso/comprovante')
            const previa = page.getByTestId('previa-comprovante')
            await expect(previa).toContainText('NÃO É DOCUMENTO FISCAL')
            await page.getByRole('button', { name: 'DANFE NFC-e' }).click()
            await expect(previa).toContainText('Documento Auxiliar da Nota Fiscal de Consumidor Eletrônica')
            await expect(previa).toContainText('P000012 - PICANHA')
            await expect(previa).toContainText(/Qtd\. total de itens\s*2/)
            // venda de exemplo: R$ 84,40 x 4,5% = R$ 3,80
            await expect(previa).toContainText('Tributos Totais Incidentes (Lei Federal 12.741/2012): R$ 3,80 (4,5%)')
            await expect(previa).not.toContainText('NÃO É DOCUMENTO FISCAL')
        } finally {
            await salvar(atual.percentualTributos ?? null)
        }
    })
})
