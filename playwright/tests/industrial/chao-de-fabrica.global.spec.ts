import { test, expect } from '../../fixtures'
import type { APIRequestContext } from '@playwright/test'

/*
 Chão de fábrica (sprint 3): o operador inicia, pausa com motivo, retoma e finaliza pela tela de tablet,
 e o produto acabado entra no estoque com lote da OP. Liga a indústria da empresa (global.spec) e devolve no fim.
*/
const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }
const sufixo = Date.now().toString(36).slice(-5).toUpperCase()
const hoje = new Date().toISOString().slice(0, 10)

let tenantId: number
let estavaLiberada = false
let numeroOP = ''

async function post(request: APIRequestContext, url: string, data: unknown) {
  const r = await request.post(url, { headers, data })
  expect(r.ok(), await r.text()).toBeTruthy()
  return r.json()
}

test.describe('Chão de fábrica', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeAll(async ({ request }) => {
    const tenants = await request.get('/api/admin/tenants', { headers })
    test.skip(!tenants.ok(), 'Usuário de teste não gerencia empresas')
    const t = (await tenants.json()).find((x: { codigo: string }) => x.codigo === (process.env.E2E_TENANT ?? '001'))
    tenantId = t.id
    estavaLiberada = t.moduloIndustrial
    await request.patch(`/api/admin/tenants/${tenantId}/modulo-industrial`, { headers, data: { habilitado: true } })

    // um apontamento esquecido aberto por uma execução anterior travaria o operador de teste
    const meu = await request.get('/api/industrial/chao/meu-apontamento', { headers })
    if (meu.status() === 200) {
      const a = await meu.json()
      await post(request, `/api/industrial/apontamentos/${a.id}/finalizar`, { chave: `limpa-${sufixo}`, quantidadeBoa: 0, quantidadeRefugo: 0, concluirOperacao: false })
    }

    const u = await post(request, '/api/industrial/unidades', { codigo: `U${sufixo}`, nome: `Fábrica ${sufixo}` })
    const s = await post(request, '/api/industrial/setores', { unidadeId: u.id, codigo: 'EXT', nome: 'Extrusão' })
    const c = await post(request, '/api/industrial/centros', { setorId: s.id, codigo: `C${sufixo}`, nome: `Prensa ${sufixo}` })
    const tipo = await post(request, '/api/industrial/tipos-maquina', { codigo: `PR${sufixo}`, nome: `Prensa ${sufixo}`, categoria: 'PRENSA_EXTRUSAO', parametros: [] })
    await post(request, '/api/industrial/maquinas', { codigo: `M${sufixo}`, descricao: 'Prensa 1800 t', tipoMaquinaId: tipo.id, centroTrabalhoId: c.id, parametros: [] })
    const perfil = await post(request, '/api/estoque/produtos', { nome: `Perfil Chão ${sufixo}`, unidadeMedida: 'KG', tipoProduto: 'INDUSTRIALIZADO', precoVenda: 10 })
    const tarugo = await post(request, '/api/estoque/produtos', { nome: `Tarugo Chão ${sufixo}`, unidadeMedida: 'KG', tipoProduto: 'INSUMO', precoVenda: 10 })
    const bom = await post(request, '/api/industrial/engenharia/boms', { produtoId: perfil.id, quantidadeBase: 1, itens: [{ componenteId: tarugo.id, quantidade: 1 }] })
    await post(request, `/api/industrial/engenharia/boms/${bom.id}/ativar`, {})
    const rot = await post(request, '/api/industrial/engenharia/roteiros', {
      produtoId: perfil.id, operacoes: [{ sequencia: 10, descricao: 'Extrudar', centroTrabalhoId: c.id, tipoMaquinaId: tipo.id, tempoSetupMin: 10, tempoPorUnidadeMin: 1 }],
    })
    await post(request, `/api/industrial/engenharia/roteiros/${rot.id}/ativar`, {})
    const op = await post(request, '/api/industrial/ordens', { produtoId: perfil.id, quantidade: 10, inicioPrevisto: hoje, fimPrevisto: hoje, prioridade: 1 })
    await post(request, `/api/industrial/ordens/${op.ordem.id}/status`, { status: 'LIBERADA', versao: op.versao })
    numeroOP = op.ordem.numero
  })

  test.afterAll(async ({ request }) => {
    if (tenantId) await request.patch(`/api/admin/tenants/${tenantId}/modulo-industrial`, { headers, data: { habilitado: estavaLiberada } })
  })

  test('Operador inicia, pausa, retoma e finaliza; o acabado entra no estoque com lote', async ({ page }) => {
    await page.goto('/inicio')
    await page.getByRole('link', { name: 'Chão de fábrica' }).click()
    await page.getByLabel('Centro de trabalho').selectOption({ label: `C${sufixo} · Prensa ${sufixo}` })

    const cartao = page.locator('div', { hasText: numeroOP }).filter({ has: page.getByRole('button', { name: 'Iniciar' }) }).last()
    await expect(cartao).toContainText(`Perfil Chão ${sufixo}`)
    await cartao.getByRole('button', { name: 'Iniciar' }).click()
    // a única máquina livre do tipo já vem escolhida
    await page.getByRole('button', { name: 'Iniciar' }).last().click()

    await expect(page.getByText('Seu apontamento', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: 'Pausar' }).click()
    await page.getByRole('button', { name: 'Preparação / troca de ferramenta' }).click()
    await page.getByRole('button', { name: 'Confirmar parada' }).click()
    await expect(page.getByText('Pausado: Preparação / troca de ferramenta')).toBeVisible()

    await page.getByRole('button', { name: 'Retomar' }).click()
    await expect(page.getByRole('button', { name: 'Pausar' })).toBeVisible()

    await page.getByRole('button', { name: 'Finalizar' }).first().click()
    await page.getByLabel(/Quantidade boa/).fill('10')
    await page.getByText('Concluir esta operação').click()
    await expect(page.getByText(/entra no estoque como produto acabado/)).toBeVisible()
    await page.getByRole('button', { name: 'Finalizar' }).last().click()

    await expect(page.getByText(`Lote ${numeroOP}-L01 entrou no estoque`)).toBeVisible()
    await expect(page.getByText('Seu apontamento', { exact: true })).toHaveCount(0)
  })

  test('O PCP vê o andamento: OP concluída, produzido e a parada', async ({ page }) => {
    await page.goto('/industrial/ordens?aba=ordens')
    await page.getByRole('button', { name: 'Concluídas' }).click()
    await page.getByRole('row', { name: new RegExp(numeroOP) }).getByRole('button', { name: 'Abrir' }).click()
    await expect(page.getByText('Produzido:')).toContainText('10 de 10 KG')
    await expect(page.getByText(`lote ${numeroOP}-L01`)).toBeVisible()
    await expect(page.getByText('Paradas durante a OP')).toBeVisible()
  })
})
