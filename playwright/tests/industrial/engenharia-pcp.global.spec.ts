import { test, expect } from '../../fixtures'
import type { APIRequestContext, Page } from '@playwright/test'

/*
 Engenharia e PCP (sprint 2): estrutura pela tela, ativação, OP criada e liberada pela tela, e carga do centro.
 Liga os módulos industriais da empresa inteira, por isso é um *.global.spec, e devolve como estava no fim.
*/
const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }
const sufixo = Date.now().toString(36).slice(-5).toUpperCase()
const hoje = new Date().toISOString().slice(0, 10)

let tenantId: number
let estavaLiberada = false
const ids: Record<string, number> = {}

async function post(request: APIRequestContext, url: string, data: unknown) {
  const r = await request.post(url, { headers, data })
  expect(r.ok(), await r.text()).toBeTruthy()
  return r.json()
}

async function escolherProduto(page: Page, placeholder: string | RegExp, nome: string) {
  const campo = page.getByPlaceholder(placeholder).first()
  await campo.click()
  await campo.fill(nome)
  await page.getByRole('option', { name: new RegExp(nome) }).first().click()
}

test.describe('Engenharia e ordens de produção', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeAll(async ({ request }) => {
    const tenants = await request.get('/api/admin/tenants', { headers })
    test.skip(!tenants.ok(), 'Usuário de teste não gerencia empresas')
    const t = (await tenants.json()).find((x: { codigo: string }) => x.codigo === (process.env.E2E_TENANT ?? '001'))
    tenantId = t.id
    estavaLiberada = t.moduloIndustrial
    await request.patch(`/api/admin/tenants/${tenantId}/modulo-industrial`, { headers, data: { habilitado: true } })

    const u = await post(request, '/api/industrial/unidades', { codigo: `U${sufixo}`, nome: `Fábrica ${sufixo}` })
    const s = await post(request, '/api/industrial/setores', { unidadeId: u.id, codigo: 'EXT', nome: `Extrusão ${sufixo}` })
    const c = await post(request, '/api/industrial/centros', { setorId: s.id, codigo: `C${sufixo}`, nome: `Prensa ${sufixo}` })
    const turno = await post(request, '/api/industrial/turnos', { codigo: `T${sufixo}`, nome: 'Turno', horaInicio: '06:00', horaFim: '14:00', diasSemana: '1111111' })
    const tipo = await post(request, '/api/industrial/tipos-maquina', { codigo: `PR${sufixo}`, nome: `Prensa ${sufixo}`, categoria: 'PRENSA_EXTRUSAO', parametros: [] })
    await post(request, '/api/industrial/maquinas', { codigo: `M${sufixo}`, descricao: 'Prensa', tipoMaquinaId: tipo.id, centroTrabalhoId: c.id, turnoIds: [turno.id], parametros: [] })
    ids.centro = c.id
    for (const [chave, nome, un, tp] of [
      ['perfil', `Perfil E2E ${sufixo}`, 'KG', 'INDUSTRIALIZADO'],
      ['tarugo', `Tarugo E2E ${sufixo}`, 'KG', 'INSUMO'],
    ] as const) {
      ids[chave] = (await post(request, '/api/estoque/produtos', { nome, unidadeMedida: un, tipoProduto: tp, precoVenda: 10 })).id
    }
    const roteiro = await post(request, '/api/industrial/engenharia/roteiros', {
      produtoId: ids.perfil, operacoes: [{ sequencia: 10, descricao: 'Extrudar', centroTrabalhoId: c.id, tempoSetupMin: 30, tempoPorUnidadeMin: 0.5 }],
    })
    await post(request, `/api/industrial/engenharia/roteiros/${roteiro.id}/ativar`, {})
  })

  test.afterAll(async ({ request }) => {
    if (tenantId) await request.patch(`/api/admin/tenants/${tenantId}/modulo-industrial`, { headers, data: { habilitado: estavaLiberada } })
  })

  test('Cria e ativa a estrutura do produto pela tela', async ({ page }) => {
    await page.goto('/inicio')
    await page.getByRole('link', { name: 'Engenharia de produto' }).click()
    await expect(page.getByRole('heading', { name: 'Engenharia de produto' })).toBeVisible()

    await page.getByRole('button', { name: 'Nova estrutura' }).click()
    await escolherProduto(page, 'Buscar o produto fabricado...', `Perfil E2E ${sufixo}`)
    await escolherProduto(page, 'Componente 1...', `Tarugo E2E ${sufixo}`)
    await page.getByLabel('Quantidade do componente 1').fill('1.1')
    await page.getByLabel('Perda do componente 1').fill('10')
    page.once('dialog', d => d.accept())
    await page.getByRole('button', { name: 'Salvar e ativar' }).click()

    await expect(page.getByText(`Estrutura de Perfil E2E ${sufixo} · v1`)).toBeVisible()
    // ativada, a versão não é mais editável: só tornar obsoleta ou criar nova versão
    await expect(page.getByRole('button', { name: 'Tornar obsoleta' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Nova versão a partir desta' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Salvar rascunho' })).toHaveCount(0)
    await page.getByLabel('Quantidade a produzir (KG)').fill('100')
    await page.getByRole('button', { name: 'Calcular' }).click()
    await expect(page.getByRole('cell', { name: /122,2222 KG/ })).toBeVisible()
  })

  test('Cria a OP, vê a falta de material e libera congelando a engenharia', async ({ page }) => {
    await page.goto('/industrial/ordens')
    await page.getByRole('button', { name: 'Nova OP' }).click()
    await escolherProduto(page, 'Buscar o produto a fabricar...', `Perfil E2E ${sufixo}`)
    await page.getByLabel(/Quantidade \*/).fill('200')
    await page.getByLabel('Início previsto *').fill(hoje)
    await page.getByLabel('Fim previsto *').fill(hoje)
    await page.getByLabel('Pedido do cliente').fill(`PED-${sufixo}`)
    await page.getByRole('button', { name: 'Salvar' }).click()

    await expect(page.getByText(/prévia da estrutura ativa/)).toBeVisible()
    // 200 × 1,1 / 0,9 = 244,4444 kg de tarugo, sem estoque: falta tudo
    await expect(page.getByRole('row', { name: new RegExp(`Tarugo E2E ${sufixo}`) })).toContainText('244,4444')

    await page.getByRole('button', { name: 'Liberar para produção' }).click()
    await page.getByRole('button', { name: 'Confirmar' }).click()
    await expect(page.getByText('Engenharia congelada')).toBeVisible()
    await expect(page.getByText('Estrutura v1 · roteiro v1')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Iniciar produção' })).toBeVisible()

    // cancelar exige motivo
    await page.getByRole('button', { name: 'Cancelar OP' }).click()
    await page.getByRole('button', { name: 'Confirmar' }).click()
    await expect(page.getByText('Informe o motivo.')).toBeVisible()
    await page.getByRole('button', { name: 'Voltar', exact: true }).click()
  })

  test('A OP liberada aparece na carga do centro de trabalho', async ({ page }) => {
    await page.goto('/industrial/ordens?aba=carga')
    const linha = page.getByRole('row', { name: new RegExp(`Prensa ${sufixo}`) })
    await expect(linha).toBeVisible()
    // carga: 30 + 0,5 × 200 = 130 min = 2h10; capacidade: 14 dias (período padrão) × 8h de turno = 112h
    await expect(linha).toContainText('2h10')
    await expect(linha).toContainText('112h')
    await linha.getByRole('button', { name: /Ver OPs/ }).click()
    await expect(page.getByRole('row', { name: new RegExp(`Perfil E2E ${sufixo}`) })).toBeVisible()
  })
})
