import { test, expect } from '../../fixtures'
import type { APIRequestContext } from '@playwright/test'
import { FISCAL_PRODUTO } from '../../support/produtoFiscal'

/*
 Extrusão (sprint 4): liga e lote de matéria-prima pela tela (lote fora da liga não libera), e no chão
 de fábrica a medição contra a receita e o consumo escolhendo o lote. Liga a indústria (global.spec) e devolve no fim.
 As faixas de composição e de processo são dados de teste digitados aqui; o sistema não traz nenhuma.
*/
const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }
const sufixo = Date.now().toString(36).slice(-5).toUpperCase()
const hoje = new Date().toISOString().slice(0, 10)

let tenantId: number
let estavaLiberada = false
const ids: Record<string, number> = {}
let numeroOP = ''

async function post(request: APIRequestContext, url: string, data: unknown) {
  const r = await request.post(url, { headers, data })
  expect(r.ok(), await r.text()).toBeTruthy()
  return r.json()
}

test.describe('Extrusão', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeAll(async ({ request }) => {
    const tenants = await request.get('/api/admin/tenants', { headers })
    test.skip(!tenants.ok(), 'Usuário de teste não gerencia empresas')
    const t = (await tenants.json()).find((x: { codigo: string }) => x.codigo === (process.env.E2E_TENANT ?? '001'))
    tenantId = t.id
    estavaLiberada = t.moduloIndustrial
    await request.patch(`/api/admin/tenants/${tenantId}/modulo-industrial`, { headers, data: { habilitado: true } })
    const meu = await request.get('/api/industrial/chao/meu-apontamento', { headers })
    if (meu.status() === 200) {
      const a = await meu.json()
      await post(request, `/api/industrial/apontamentos/${a.id}/finalizar`, { chave: `limpa-${sufixo}`, quantidadeBoa: 0, quantidadeRefugo: 0, concluirOperacao: false })
    }
    ids.tarugo = (await post(request, '/api/estoque/produtos', { ...FISCAL_PRODUTO, nome: `TARUGO EXT ${sufixo}`, unidadeMedida: 'KG', tipoProduto: 'INSUMO', precoVenda: 10 })).id
  })

  test.afterAll(async ({ request }) => {
    if (tenantId) await request.patch(`/api/admin/tenants/${tenantId}/modulo-industrial`, { headers, data: { habilitado: estavaLiberada } })
  })

  test('Liga e lotes pela tela: lote fora da faixa não libera', async ({ page }) => {
    await page.goto('/inicio')
    await page.getByRole('link', { name: 'Extrusão' }).click()
    await page.getByRole('button', { name: 'Ligas' }).click()
    await page.getByRole('button', { name: 'Nova liga' }).click()
    await page.getByLabel('Código *').fill(`L${sufixo}`)
    await page.getByLabel('Descrição *').fill(`Liga teste ${sufixo}`)
    await page.getByLabel('Elemento 1', { exact: true }).fill('Mg')
    await page.getByLabel('Mínimo do elemento 1').fill('0.45')
    await page.getByLabel('Máximo do elemento 1').fill('0.9')
    await page.getByRole('button', { name: 'Salvar' }).click()
    await expect(page.getByRole('cell', { name: `L${sufixo}` })).toBeVisible()

    await page.getByRole('button', { name: 'Lotes de matéria-prima' }).click()
    for (const [numero, mg] of [[`F${sufixo}`, '0.95'], [`B${sufixo}`, '0.6']]) {
      await page.getByRole('button', { name: 'Receber lote' }).click()
      const material = page.getByPlaceholder('Tarugo, lingote...')
      await material.click()
      await material.fill(`TARUGO EXT ${sufixo}`)
      await page.getByRole('option', { name: new RegExp(`TARUGO EXT ${sufixo}`) }).click()
      await page.getByLabel('Número do lote / corrida *').fill(numero)
      await page.getByLabel('Liga *').selectOption({ label: `L${sufixo} · Liga teste ${sufixo}` })
      await page.getByLabel('Quantidade *').fill('500')
      await page.getByLabel('Mg', { exact: true }).fill(mg)
      await page.getByRole('button', { name: 'Salvar' }).click()
      await expect(page.getByRole('row', { name: new RegExp(numero) })).toBeVisible()
    }

    const fora = page.getByRole('row', { name: new RegExp(`F${sufixo}`) })
    await expect(fora).toContainText('fora da liga')
    await expect(fora.getByRole('button', { name: 'Liberar' })).toBeDisabled()

    const bom = page.getByRole('row', { name: new RegExp(`B${sufixo}`) })
    await bom.getByRole('button', { name: 'Liberar' }).click()
    await page.getByRole('button', { name: 'Confirmar' }).click()
    await page.getByRole('button', { name: 'Liberados' }).click()
    await expect(page.getByRole('row', { name: new RegExp(`B${sufixo}`) })).toContainText('Liberado')
  })

  test('Chão de fábrica: medição fora da receita e consumo escolhendo o lote', async ({ page, request }) => {
    const ligas = await (await request.get('/api/industrial/extrusao/ligas', { headers })).json()
    const liga = ligas.find((l: { codigo: string }) => l.codigo === `L${sufixo}`)
    const u = await post(request, '/api/industrial/unidades', { codigo: `U${sufixo}`, nome: 'Fábrica' })
    const s = await post(request, '/api/industrial/setores', { unidadeId: u.id, codigo: 'EXT', nome: 'Extrusão' })
    const c = await post(request, '/api/industrial/centros', { setorId: s.id, codigo: `C${sufixo}`, nome: `Fornos ${sufixo}` })
    const tipo = await post(request, '/api/industrial/tipos-maquina', { codigo: `FN${sufixo}`, nome: `Forno ${sufixo}`, categoria: 'FORNO', parametros: [] })
    await post(request, '/api/industrial/maquinas', { codigo: `M${sufixo}`, descricao: 'Forno de tarugos', tipoMaquinaId: tipo.id, centroTrabalhoId: c.id, parametros: [] })
    const perfil = await post(request, '/api/estoque/produtos', { ...FISCAL_PRODUTO, nome: `PERFIL EXT ${sufixo}`, unidadeMedida: 'KG', tipoProduto: 'INDUSTRIALIZADO', precoVenda: 10 })
    await request.put(`/api/industrial/engenharia/fichas/${perfil.id}`, { headers, data: { tipoItem: 'ACABADO', ligaId: liga.id } })
    await post(request, '/api/industrial/extrusao/receitas', {
      codigo: `AQ${sufixo}`, descricao: 'Aquecimento', etapa: 'AQUECIMENTO', ligaId: liga.id, tipoMaquinaId: tipo.id,
      parametros: [{ codigo: 'TEMP', nome: 'Temperatura do tarugo', unidade: '°C', minimo: 460, maximo: 500 }],
    })
    const bom = await post(request, '/api/industrial/engenharia/boms', { produtoId: perfil.id, quantidadeBase: 1, itens: [{ componenteId: ids.tarugo, quantidade: 1 }] })
    await post(request, `/api/industrial/engenharia/boms/${bom.id}/ativar`, {})
    const rot = await post(request, '/api/industrial/engenharia/roteiros', {
      produtoId: perfil.id, operacoes: [{ sequencia: 10, descricao: 'Aquecer', centroTrabalhoId: c.id, tipoMaquinaId: tipo.id, tempoSetupMin: 0, tempoPorUnidadeMin: 1 }],
    })
    await post(request, `/api/industrial/engenharia/roteiros/${rot.id}/ativar`, {})
    const op = await post(request, '/api/industrial/ordens', { produtoId: perfil.id, quantidade: 10, inicioPrevisto: hoje, fimPrevisto: hoje, prioridade: 1 })
    await post(request, `/api/industrial/ordens/${op.ordem.id}/status`, { status: 'LIBERADA', versao: op.versao })
    numeroOP = op.ordem.numero

    await page.goto('/industrial/chao')
    await page.getByLabel('Centro de trabalho').selectOption({ label: `C${sufixo} · Fornos ${sufixo}` })
    const cartao = page.locator('div', { hasText: numeroOP }).filter({ has: page.getByRole('button', { name: 'Iniciar' }) }).last()
    await cartao.getByRole('button', { name: 'Iniciar' }).click()
    await page.getByRole('button', { name: 'Iniciar' }).last().click()
    await expect(page.getByText('Seu apontamento', { exact: true })).toBeVisible()

    await page.getByRole('button', { name: 'Medição' }).click()
    await page.getByLabel(/Temperatura do tarugo/).fill('520')
    await expect(page.getByText(/Fora da faixa da receita/)).toBeVisible()
    await page.getByLabel('Ação tomada *').fill('Tarugo devolvido ao forno')
    await page.getByRole('button', { name: 'Registrar medição' }).click()
    await expect(page.getByText('Medição registrada FORA da faixa da receita.')).toBeVisible()
    await page.getByRole('button', { name: 'Fechar' }).last().click()

    // o tarugo tem lote liberado: o consumo mostra os lotes e o servidor recusa se não tiver saldo no estoque
    await page.getByRole('button', { name: 'Material' }).click()
    await page.getByRole('button', { name: new RegExp(`TARUGO EXT ${sufixo}`) }).click()
    await expect(page.getByRole('button', { name: new RegExp(`B${sufixo}`) })).toBeVisible()
    await expect(page.getByRole('button', { name: new RegExp(`F${sufixo}`) })).toHaveCount(0)
    await page.getByLabel(/Quantidade \(KG\)/).fill('5')
    await page.getByRole('button', { name: 'Registrar consumo' }).click()
    await expect(page.getByText(/Estoque insuficiente/)).toBeVisible()
    await page.getByRole('button', { name: 'Fechar' }).last().click()

    await page.getByRole('button', { name: 'Finalizar' }).first().click()
    await page.getByLabel(/Quantidade boa/).fill('0')
    await page.getByRole('button', { name: 'Finalizar' }).last().click()
    await expect(page.getByText('Seu apontamento', { exact: true })).toHaveCount(0)
  })
})
