import { test, expect } from '../../fixtures'
import type { APIRequestContext } from '@playwright/test'

/*
 Estrutura industrial (sprint 1). Libera os módulos industriais da empresa inteira, por isso é um
 *.global.spec (roda depois dos outros, ver playwright.config.ts) e devolve como estava no fim.
 O usuário de teste precisa ser o super admin da empresa principal (quem gerencia as empresas).
*/
const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }
const sufixo = Date.now().toString(36).slice(-5).toUpperCase()

let tenantId: number
let estavaLiberada = false

async function liberar(request: APIRequestContext, habilitado: boolean) {
  const r = await request.patch(`/api/admin/tenants/${tenantId}/modulo-industrial`, { headers, data: { habilitado } })
  expect(r.ok(), await r.text()).toBeTruthy()
}

test.describe('Estrutura industrial', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeAll(async ({ request }) => {
    const tenants = await request.get('/api/admin/tenants', { headers })
    test.skip(!tenants.ok(), 'Usuário de teste não gerencia empresas')
    const t = (await tenants.json()).find((x: { codigo: string }) => x.codigo === (process.env.E2E_TENANT ?? '001'))
    tenantId = t.id
    estavaLiberada = t.moduloIndustrial
  })

  test.afterAll(async ({ request }) => {
    if (tenantId) await liberar(request, estavaLiberada)
  })

  test('Empresa sem indústria não vê o menu nem acessa a API', async ({ page, request }) => {
    await liberar(request, false)
    expect((await request.get('/api/industrial/unidades', { headers })).status()).toBe(403)
    await page.goto('/inicio')
    await expect(page.getByRole('link', { name: 'Estrutura industrial' })).toHaveCount(0)
  })

  test('Cadastra unidade, setor e centro pela tela', async ({ page, request }) => {
    await liberar(request, true)
    await page.goto('/inicio')
    await page.getByRole('link', { name: 'Estrutura industrial' }).click()
    await expect(page.getByRole('heading', { name: 'Estrutura industrial' })).toBeVisible()

    await page.getByRole('button', { name: 'Nova unidade' }).click()
    await page.getByLabel('Código *').fill(`fab-${sufixo}`)
    await page.getByLabel('Nome *').fill(`Fábrica ${sufixo}`)
    await page.getByRole('button', { name: 'Salvar' }).click()
    await expect(page.getByRole('cell', { name: `FAB-${sufixo}` })).toBeVisible()

    await page.getByRole('button', { name: 'Setores' }).click()
    await page.getByRole('button', { name: 'Novo setor' }).click()
    await page.getByLabel('Unidade *').selectOption({ label: `FAB-${sufixo} · Fábrica ${sufixo}` })
    await page.getByLabel('Código *').fill(`ext-${sufixo}`)
    await page.getByLabel('Nome *').fill(`Extrusão ${sufixo}`)
    await page.getByRole('button', { name: 'Salvar' }).click()
    await expect(page.getByRole('cell', { name: `Extrusão ${sufixo}` })).toBeVisible()

    await page.getByRole('button', { name: 'Centros de trabalho' }).click()
    await page.getByRole('button', { name: 'Novo centro de trabalho' }).click()
    await page.getByLabel('Setor *').selectOption({ label: `Extrusão ${sufixo} (Fábrica ${sufixo})` })
    await page.getByLabel('Código *').fill(`ct-${sufixo}`)
    await page.getByLabel('Nome *').fill(`Prensa ${sufixo}`)
    await page.getByLabel('Custo por hora (R$)').fill('180')
    await page.getByRole('button', { name: 'Salvar' }).click()
    await expect(page.getByRole('cell', { name: `Prensa ${sufixo}` })).toBeVisible()
  })

  test('Máquina mostra os parâmetros do tipo e recusa valor fora da faixa', async ({ page, request }) => {
    await liberar(request, true)
    const tipo = await (await request.post('/api/industrial/tipos-maquina', {
      headers,
      data: {
        codigo: `forno-${sufixo}`, nome: `Forno ${sufixo}`, categoria: 'FORNO',
        parametros: [{ codigo: 'TEMP_MAX', nome: 'Temperatura máxima', unidade: '°C', tipoValor: 'NUMERO', minimo: 0, maximo: 650, obrigatorio: true }],
      },
    })).json()
    expect(tipo.id).toBeTruthy()

    await page.goto('/industrial/estrutura?aba=maquinas')
    await page.getByRole('button', { name: 'Nova máquina' }).click()
    await page.getByLabel('Código *').fill(`f-${sufixo}`)
    await page.getByLabel('Descrição *').fill(`Forno de tarugos ${sufixo}`)
    await page.getByLabel('Tipo de máquina *').selectOption({ label: `Forno ${sufixo}` })
    await page.getByLabel('Centro de trabalho *').selectOption({ label: `Prensa ${sufixo} (Extrusão ${sufixo} · Fábrica ${sufixo})` })

    const temperatura = page.getByLabel('Temperatura máxima (°C) *')
    await expect(temperatura).toBeVisible()
    await temperatura.fill('700')
    await page.getByRole('button', { name: 'Salvar' }).click()
    await expect(page.getByText(/Temperatura máxima fora da faixa permitida/)).toBeVisible()

    await temperatura.fill('550')
    await page.getByRole('button', { name: 'Salvar' }).click()
    const linha = page.getByRole('row', { name: new RegExp(`F-${sufixo}`) })
    await expect(linha).toContainText('Disponível')

    // Manutenção pela tela de operação: muda o status e aparece no histórico
    await linha.getByRole('button', { name: 'Operação' }).click()
    await page.getByLabel('Novo status').selectOption({ label: 'Em manutenção' })
    await page.getByLabel('Motivo').fill('Troca de resistência')
    await page.getByRole('button', { name: 'Aplicar' }).click()
    await expect(page.getByText('Disponível → Em manutenção:')).toBeVisible()
  })
})
