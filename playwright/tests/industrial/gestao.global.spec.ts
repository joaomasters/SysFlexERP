import { test, expect } from '../../fixtures'
import type { APIRequestContext } from '@playwright/test'
import { FISCAL_PRODUTO } from '../../support/produtoFiscal'

/*
 Gestão industrial (sprint 7): o MRP sugere produzir o semiacabado de uma OP planejada e o usuário aprova
 criando a OP pela tela; os indicadores e os custos abrem com os dados do período.
 Liga a indústria (global.spec) e devolve no fim.
*/
const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }
const sufixo = Date.now().toString(36).slice(-5).toUpperCase()
const hoje = new Date().toISOString().slice(0, 10)

let tenantId: number
let estavaLiberada = false

async function post(request: APIRequestContext, url: string, data: unknown) {
  const r = await request.post(url, { headers, data })
  expect(r.ok(), await r.text()).toBeTruthy()
  return r.json()
}

test.describe('Gestão industrial', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeAll(async ({ request }) => {
    const tenants = await request.get('/api/admin/tenants', { headers })
    test.skip(!tenants.ok(), 'Usuário de teste não gerencia empresas')
    const t = (await tenants.json()).find((x: { codigo: string }) => x.codigo === (process.env.E2E_TENANT ?? '001'))
    tenantId = t.id
    estavaLiberada = t.moduloIndustrial
    await request.patch(`/api/admin/tenants/${tenantId}/modulo-industrial`, { headers, data: { habilitado: true } })

    const u = await post(request, '/api/industrial/unidades', { codigo: `U${sufixo}`, nome: 'Fábrica' })
    const s = await post(request, '/api/industrial/setores', { unidadeId: u.id, codigo: 'EXT', nome: 'Extrusão' })
    const c = await post(request, '/api/industrial/centros', { setorId: s.id, codigo: `C${sufixo}`, nome: `Centro ${sufixo}`, custoHora: 100 })
    const prod = async (nome: string) => post(request, '/api/estoque/produtos', { ...FISCAL_PRODUTO, nome: `${nome} ${sufixo}`, unidadeMedida: 'KG', tipoProduto: 'INDUSTRIALIZADO', precoVenda: 10 })
    const perfil = await prod('PERFIL MRP')
    const semi = await prod('SEMI MRP')
    const tarugo = await prod('TARUGO MRP')
    for (const [p, itens] of [[semi, [[tarugo, 0.5]]], [perfil, [[semi, 1]]]] as const) {
      const b = await post(request, '/api/industrial/engenharia/boms', { produtoId: p.id, quantidadeBase: 1, itens: itens.map(([x, q]) => ({ componenteId: x.id, quantidade: q })) })
      await post(request, `/api/industrial/engenharia/boms/${b.id}/ativar`, {})
      const r = await post(request, '/api/industrial/engenharia/roteiros', { produtoId: p.id, operacoes: [{ sequencia: 10, descricao: 'Extrudar', centroTrabalhoId: c.id, tempoSetupMin: 0, tempoPorUnidadeMin: 1 }] })
      await post(request, `/api/industrial/engenharia/roteiros/${r.id}/ativar`, {})
    }
    await post(request, '/api/industrial/ordens', { produtoId: perfil.id, quantidade: 40, inicioPrevisto: hoje, fimPrevisto: hoje })
  })

  test.afterAll(async ({ request }) => {
    if (tenantId) await request.patch(`/api/admin/tenants/${tenantId}/modulo-industrial`, { headers, data: { habilitado: estavaLiberada } })
  })

  test('MRP sugere produzir o semiacabado e o usuário cria a OP pela tela', async ({ page }) => {
    await page.goto('/inicio')
    await page.getByRole('link', { name: 'Gestão industrial' }).click()
    await page.getByRole('button', { name: 'MRP' }).click()
    // a linha do tarugo também cita o semiacabado na origem ("sugestão Semi..."): filtra pelo tipo
    const semi = page.getByRole('row').filter({ hasText: 'Produzir' }).filter({ hasText: `SEMI MRP ${sufixo}` })
    await expect(semi).toHaveCount(1)
    await expect(page.getByRole('row').filter({ hasText: 'Comprar' }).filter({ hasText: `TARUGO MRP ${sufixo}` })).toHaveCount(1)
    await semi.getByRole('button', { name: 'Criar OP planejada' }).click()
    await expect(page.getByText(/criada como planejada/)).toBeVisible()
    await expect(semi).toContainText('criada')
  })

  test('Indicadores e custos abrem com o período', async ({ page }) => {
    await page.goto('/industrial/gestao')
    await expect(page.getByText('OPs concluídas no prazo')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'OEE por máquina' })).toBeVisible()
    await page.getByRole('button', { name: 'Custos por OP' }).click()
    await expect(page.getByText(/Estimado: materiais previstos/)).toBeVisible()
  })
})
