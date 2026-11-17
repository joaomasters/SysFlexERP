import { test, expect } from '../../fixtures'
import type { APIRequestContext } from '@playwright/test'

/*
 Qualidade e expedição (sprints 5 e 6): lote em quarentena inspecionado pela tela, rastreabilidade e o fluxo
 completo da expedição (reserva automática, conferência lendo o código do lote, carregamento e NF pendente).
 Liga a indústria (global.spec) e devolve no fim. As faixas do plano são dados de teste digitados aqui.
*/
const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }
const sufixo = Date.now().toString(36).slice(-5).toUpperCase()
const hoje = new Date().toISOString().slice(0, 10)

let tenantId: number
let estavaLiberada = false
let codigoLote = ''
let cliente = ''

async function post(request: APIRequestContext, url: string, data: unknown) {
  const r = await request.post(url, { headers, data })
  expect(r.ok(), await r.text()).toBeTruthy()
  return r.json()
}

test.describe('Qualidade e expedição', () => {
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

    const u = await post(request, '/api/industrial/unidades', { codigo: `U${sufixo}`, nome: 'Fábrica' })
    const s = await post(request, '/api/industrial/setores', { unidadeId: u.id, codigo: 'ACB', nome: 'Acabamento' })
    const c = await post(request, '/api/industrial/centros', { setorId: s.id, codigo: `C${sufixo}`, nome: `Embalagem ${sufixo}` })
    const perfil = await post(request, '/api/estoque/produtos', { nome: `Perfil QE ${sufixo}`, unidadeMedida: 'KG', tipoProduto: 'INDUSTRIALIZADO', precoVenda: 30 })
    const insumo = await post(request, '/api/estoque/produtos', { nome: `Filme QE ${sufixo}`, unidadeMedida: 'UN', tipoProduto: 'INSUMO', precoVenda: 1 })
    await post(request, '/api/industrial/qualidade/planos', {
      codigo: `PI${sufixo}`, descricao: 'Inspeção final', produtoId: perfil.id,
      caracteristicas: [{ codigo: 'ESPESSURA', nome: 'Espessura', tipo: 'MEDIDA', unidade: 'mm', minimo: 1.9, maximo: 2.1 },
        { codigo: 'ASPECTO', nome: 'Aspecto visual', tipo: 'ATRIBUTO' }],
    })
    const bom = await post(request, '/api/industrial/engenharia/boms', { produtoId: perfil.id, quantidadeBase: 1, itens: [{ componenteId: insumo.id, quantidade: 1 }] })
    await post(request, `/api/industrial/engenharia/boms/${bom.id}/ativar`, {})
    const rot = await post(request, '/api/industrial/engenharia/roteiros', {
      produtoId: perfil.id, operacoes: [{ sequencia: 10, descricao: 'Embalar', centroTrabalhoId: c.id, tempoSetupMin: 0, tempoPorUnidadeMin: 1 }],
    })
    await post(request, `/api/industrial/engenharia/roteiros/${rot.id}/ativar`, {})
    const op = await post(request, '/api/industrial/ordens', { produtoId: perfil.id, quantidade: 30, inicioPrevisto: hoje, fimPrevisto: hoje })
    const lib = await post(request, `/api/industrial/ordens/${op.ordem.id}/status`, { status: 'LIBERADA', versao: op.versao })
    const a = await post(request, '/api/industrial/apontamentos', { chave: `ap-${sufixo}`, operacaoId: lib.operacoes[0].id })
    const fim = await post(request, `/api/industrial/apontamentos/${a.id}/finalizar`, { chave: `fim-${sufixo}`, quantidadeBoa: 30, quantidadeRefugo: 0, concluirOperacao: true })
    codigoLote = fim.lote
    cliente = `Cliente QE ${sufixo}`
    await post(request, '/api/clientes', { nome: cliente, cpfCnpj: '11222333000181', tipoPessoa: 'PJ' })
  })

  test.afterAll(async ({ request }) => {
    if (tenantId) await request.patch(`/api/admin/tenants/${tenantId}/modulo-industrial`, { headers, data: { habilitado: estavaLiberada } })
  })

  test('Lote em quarentena é inspecionado pela tela e liberado; a rastreabilidade mostra o lote', async ({ page }) => {
    await page.goto('/inicio')
    await page.getByRole('link', { name: 'Qualidade' }).click()
    const linha = page.getByRole('row', { name: new RegExp(codigoLote) })
    await expect(linha).toContainText('Quarentena')
    await linha.getByRole('button', { name: 'Inspecionar' }).click()
    await page.getByLabel(/Espessura \(mm\)/).fill('2.0')
    await page.getByRole('button', { name: 'Conforme', exact: true }).click()
    await page.getByRole('button', { name: 'Registrar inspeção' }).click()
    await expect(page.getByText(`Lote ${codigoLote} aprovado e liberado.`)).toBeVisible()

    await page.getByRole('button', { name: 'Liberados' }).click()
    await page.getByRole('button', { name: codigoLote }).click()
    await expect(page.getByText('Destino: clientes')).toBeVisible()
    await expect(page.getByText('Ainda não expedido para nenhum cliente.')).toBeVisible()
    await expect(page.getByText(/Aprovado/).first()).toBeVisible()
  })

  test('Expedição: reserva automática, conferência pelo código do lote, carregamento e NF pendente', async ({ page }) => {
    await page.goto('/industrial/expedicao')
    await page.getByRole('button', { name: 'Nova expedição' }).click()
    await page.getByLabel('Cliente *').selectOption({ label: `${cliente} · 11222333000181` })
    const produto = page.getByPlaceholder('Produto 1...')
    await produto.click()
    await produto.fill(`Perfil QE ${sufixo}`)
    await page.getByRole('option', { name: new RegExp(`Perfil QE ${sufixo}`) }).click()
    await page.getByLabel('Quantidade do item 1').fill('20')
    await page.getByRole('button', { name: 'Salvar' }).click()

    await page.getByRole('button', { name: /Reservar automático/ }).click()
    await expect(page.getByRole('cell', { name: codigoLote })).toBeVisible()
    await expect(page.getByText(`Lote ${codigoLote} não conferido.`)).toBeVisible()

    await page.getByLabel(/Conferência: leia o QR/).fill(codigoLote.toLowerCase())
    await page.getByRole('button', { name: 'Conferir' }).click()
    await expect(page.getByText(`Lote ${codigoLote} conferido.`)).toBeVisible()
    await page.getByRole('button', { name: 'Concluir conferência' }).click()

    page.once('dialog', d => d.accept())
    await page.getByRole('button', { name: 'Carregar' }).click()
    await expect(page.getByText(/Carregada em/)).toBeVisible()

    await page.getByRole('button', { name: 'Gerar NF de saída' }).click()
    await expect(page.getByText(/criada como pendente/)).toBeVisible()
  })
})
