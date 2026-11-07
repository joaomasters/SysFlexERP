import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { ArrowLeft, Info } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { TIPOS_PRODUTO } from '@/types/produto'
import type { Produto } from '@/types/produto'
import type { OpcaoFornecedor } from '@/types/fornecedor'
import { casasDaUnidade } from '@/shared/utils/mask'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { margemPct, corMargem, precoPorMargem } from './produtoUtils'
import {
  PageHeader, Card, Button, Field, baseInputClass, disabledInputClass,
  CurrencyInput, WeightInput, ConfirmDialog, LoadingState,
} from '@/shared/components/ui'

type Aba = 'basico' | 'fiscal'

const ABAS: { value: Aba; label: string }[] = [
  { value: 'basico', label: 'Informações Básicas' },
  { value: 'fiscal', label: 'Informações Fiscais' },
]

const ORIGENS: { value: string; label: string }[] = [
  { value: '0', label: '0 — Nacional' },
  { value: '1', label: '1 — Estrangeira, importação direta' },
  { value: '2', label: '2 — Estrangeira, adquirida no mercado interno' },
  { value: '3', label: '3 — Nacional, conteúdo de importação > 40% e ≤ 70%' },
  { value: '4', label: '4 — Nacional, processos produtivos básicos' },
  { value: '5', label: '5 — Nacional, conteúdo de importação ≤ 40%' },
  { value: '6', label: '6 — Estrangeira, importação direta, sem similar nacional' },
  { value: '7', label: '7 — Estrangeira, mercado interno, sem similar nacional' },
  { value: '8', label: '8 — Nacional, conteúdo de importação > 70%' },
]

const CSOSNS: { value: string; label: string }[] = [
  { value: '101', label: '101 — Tributada com permissão de crédito' },
  { value: '102', label: '102 — Tributada sem permissão de crédito' },
  { value: '103', label: '103 — Isenção do ICMS para faixa de receita bruta' },
  { value: '201', label: '201 — Com permissão de crédito e cobrança do ICMS por ST' },
  { value: '202', label: '202 — Sem permissão de crédito e com cobrança do ICMS por ST' },
  { value: '203', label: '203 — Isenção para faixa de receita bruta e cobrança por ST' },
  { value: '300', label: '300 — Imune' },
  { value: '400', label: '400 — Não tributada' },
  { value: '500', label: '500 — ICMS cobrado anteriormente por ST ou antecipação' },
  { value: '900', label: '900 — Outros' },
]

interface FormState {
  codigoInterno: string
  ean13: string
  nome: string
  unidadeMedida: Produto['unidadeMedida']
  tipoProduto: Produto['tipoProduto']
  codigoBalanca: string
  precoCusto: number
  precoVenda: number
  estoqueMinimo: number
  estoqueAtual: number
  estoqueMaximo: number
  fornecedorId: number | null
  marca: string
  validadePadraoDias: string
  ncm: string
  cest: string
  cfop: string
  origem: string
  csosn: string
}

type Erros = Partial<Record<keyof FormState, string>>

const FORM_VAZIO: FormState = {
  codigoInterno: '',
  ean13: '',
  nome: '',
  unidadeMedida: 'KG',
  tipoProduto: 'CORTE',
  codigoBalanca: '',
  precoCusto: 0,
  precoVenda: 0,
  estoqueMinimo: 0,
  estoqueAtual: 0,
  estoqueMaximo: 0,
  fornecedorId: null,
  marca: '',
  validadePadraoDias: '',
  ncm: '',
  cest: '',
  cfop: '',
  origem: '0',
  csosn: '',
}

// Campos de cada aba, na ordem visual — usada para abrir a aba certa e focar o
// primeiro campo inválido ao salvar.
const CAMPOS_POR_ABA: Record<Aba, (keyof FormState)[]> = {
  basico: [
    'codigoInterno', 'ean13', 'nome', 'unidadeMedida', 'marca', 'tipoProduto', 'codigoBalanca',
    'precoCusto', 'precoVenda', 'fornecedorId',
    'estoqueMinimo', 'estoqueAtual', 'estoqueMaximo', 'validadePadraoDias',
  ],
  fiscal: ['ncm', 'cest', 'cfop', 'origem', 'csosn'],
}

const usaBalanca = (unidade: FormState['unidadeMedida']) => unidade === 'KG' || unidade === 'G'
const soDigitos = (v: string) => v.replace(/\D/g, '')

function validarProduto(f: FormState, exigeEan: boolean): Erros {
  const e: Erros = {}
  if (f.codigoInterno.trim().length > 20) e.codigoInterno = 'Máximo de 20 caracteres.'
  if (exigeEan && !f.ean13) e.ean13 = 'Informe o código de barras.'
  if (!f.nome.trim()) e.nome = 'Informe a descrição do produto.'
  if (f.precoVenda <= 0) e.precoVenda = 'Informe o preço de venda.'
  if (f.estoqueMaximo > 0 && f.estoqueMaximo < f.estoqueMinimo) {
    e.estoqueMaximo = 'Deve ser maior ou igual ao estoque mínimo.'
  }
  if (f.validadePadraoDias && Number(f.validadePadraoDias) <= 0) {
    e.validadePadraoDias = 'Informe um número inteiro maior que zero.'
  }
  if (f.ncm && f.ncm.length !== 8) e.ncm = 'O NCM deve ter 8 dígitos.'
  if (f.cest && f.cest.length !== 7) e.cest = 'O CEST deve ter 7 dígitos.'
  if (f.cfop && !/^[1-7]\d{3}$/.test(f.cfop)) e.cfop = 'Informe um CFOP válido (4 dígitos).'
  return e
}

export default function ProdutoCadastroPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { podeVer } = usePermissao()
  const { id } = useParams()
  const isEdicao = Boolean(id)
  const [aba, setAba] = useState<Aba>('basico')
  const [form, setForm] = useState<FormState>(FORM_VAZIO)
  // Estado de partida (vazio ou o produto carregado) — base para detectar alterações não salvas.
  const [inicial, setInicial] = useState<FormState>(FORM_VAZIO)
  const [tentouSalvar, setTentouSalvar] = useState(false)
  const [confirmandoSaida, setConfirmandoSaida] = useState(false)

  const { data: produto } = useQuery<Produto>({
    queryKey: ['produto', id],
    queryFn: () => api.get(`/estoque/produtos/${id}`).then(r => r.data),
    enabled: isEdicao,
  })

  useEffect(() => {
    if (!produto) return
    const carregado: FormState = {
      ...FORM_VAZIO,
      codigoInterno: produto.codigoInterno ?? '',
      ean13: produto.ean13 ?? '',
      nome: produto.nome ?? '',
      unidadeMedida: produto.unidadeMedida,
      tipoProduto: produto.tipoProduto,
      codigoBalanca: produto.codigoBalanca != null ? String(produto.codigoBalanca) : '',
      precoCusto: produto.precoCusto ?? 0,
      precoVenda: produto.precoVenda ?? 0,
      estoqueMinimo: produto.estoqueMinimo ?? 0,
      estoqueAtual: produto.estoqueAtual ?? 0,
      fornecedorId: produto.fornecedorId ?? null,
      marca: produto.marca ?? '',
      validadePadraoDias: produto.validadePadraoDias != null ? String(produto.validadePadraoDias) : '',
      estoqueMaximo: produto.estoqueMaximo ?? 0,
      ncm: produto.ncm ?? '',
      cest: produto.cest ?? '',
      cfop: produto.cfop ?? '',
      origem: produto.origem ?? '0',
      csosn: produto.csosn ?? '',
    }
    setForm(carregado)
    setInicial(carregado)
  }, [produto])

  // Fornecedores cadastrados (só ativos). O produto grava o vínculo (fornecedorId); o nome
  // é preenchido pelo backend a partir do cadastro.
  const { data: fornecedores = [], isLoading: carregandoFornecedores } = useQuery<OpcaoFornecedor[]>({
    queryKey: ['fornecedores-opcoes'],
    queryFn: () => api.get('/estoque/fornecedores-opcoes').then(r => r.data),
  })

  // Código de barras obrigatório no cadastro novo; produto antigo sem código pode continuar sem.
  const exigeEan = !isEdicao || Boolean(produto?.ean13)
  const erros = tentouSalvar ? validarProduto(form, exigeEan) : {}
  const abaTemErro = (a: Aba) => CAMPOS_POR_ABA[a].some(c => erros[c])
  const alterado = JSON.stringify(form) !== JSON.stringify(inicial)
  // Produto de fornecedor já inativado: mantém o nome na lista para não "perder" o vínculo ao editar.
  const fornecedorInativo = produto?.fornecedorId && !fornecedores.some(f => f.id === produto.fornecedorId)
    ? { id: produto.fornecedorId, nome: `${produto.fornecedor ?? 'Fornecedor'} (inativo)` }
    : null
  const balanca = usaBalanca(form.unidadeMedida)
  const casas = casasDaUnidade(form.unidadeMedida)
  const unidade = form.unidadeMedida.toLowerCase()
  const margem = margemPct(form.precoCusto, form.precoVenda)
  // Margem editável: enquanto digita, guarda o texto e recalcula o preço de venda; fora do foco mostra a calculada
  const [margemTexto, setMargemTexto] = useState<string | null>(null)
  const margemDigitada = margemTexto === null ? null : Number(margemTexto.replace(',', '.'))
  const erroMargem = margemDigitada !== null && margemTexto !== '' && !(margemDigitada >= 0 && margemDigitada < 100)
    ? 'Use uma margem de 0 a 99,9%.' : undefined
  function digitarMargem(texto: string) {
    const limpo = texto.replace(/[^\d,.]/g, '')
    setMargemTexto(limpo)
    const preco = precoPorMargem(form.precoCusto, Number(limpo.replace(',', '.')))
    if (limpo !== '' && preco !== null) set('precoVenda', preco)
  }

  function set<K extends keyof FormState>(campo: K, valor: FormState[K]) {
    setForm(f => ({ ...f, [campo]: valor }))
  }

  function trocarUnidade(u: FormState['unidadeMedida']) {
    // PLU só existe para produto pesado na balança; marca, só para o vendido por unidade/caixa
    // (cortes por KG/G normalmente não têm marca comercial própria).
    setForm(f => ({
      ...f,
      unidadeMedida: u,
      codigoBalanca: usaBalanca(u) ? f.codigoBalanca : '',
      marca: usaBalanca(u) ? '' : f.marca,
    }))
  }

  const salvar = useMutation({
    mutationFn: (f: FormState) => {
      const payload = {
        codigoInterno: f.codigoInterno.trim() || null,
        ean13: f.ean13 || null,
        nome: f.nome.trim().replace(/\s+/g, ' '),
        unidadeMedida: f.unidadeMedida,
        tipoProduto: f.tipoProduto,
        codigoBalanca: balanca && f.codigoBalanca ? Number(f.codigoBalanca) : null,
        precoCusto: f.precoCusto || null,
        precoVenda: f.precoVenda,
        estoqueMinimo: f.estoqueMinimo,
        estoqueAtual: f.estoqueAtual,
        estoqueMaximo: f.estoqueMaximo || null,
        fornecedorId: f.fornecedorId,
        marca: balanca ? null : f.marca.trim() || null,
        validadePadraoDias: f.validadePadraoDias ? Number(f.validadePadraoDias) : null,
        ncm: f.ncm || null,
        cest: f.cest || null,
        cfop: f.cfop || null,
        origem: f.origem,
        csosn: f.csosn || null,
      }
      // Na edição o PUT sobrescreve campos que não estão no formulário (ativo, categoria,
      // descrição...), então reenvia o produto carregado com as alterações por cima.
      return isEdicao
        ? api.put(`/estoque/produtos/${id}`, { ...produto, ...payload })
        : api.post('/estoque/produtos', payload)
    },
    onSuccess: () => {
      toast.success(isEdicao ? 'Produto atualizado!' : 'Produto cadastrado!')
      qc.invalidateQueries({ queryKey: ['produtos'] })
      qc.invalidateQueries({ queryKey: ['produto', id] })
      qc.invalidateQueries({ queryKey: ['produtos-alertas'] })
      qc.invalidateQueries({ queryKey: ['produtos-balanca'] })
      qc.invalidateQueries({ queryKey: ['pdv-produtos'] })
      navigate('/produtos')
    },
  })

  function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setTentouSalvar(true)
    const errosAtuais = validarProduto(form, exigeEan)
    for (const a of ABAS) {
      const primeiro = CAMPOS_POR_ABA[a.value].find(c => errosAtuais[c])
      if (primeiro) {
        setAba(a.value)
        // Espera a aba ficar visível antes de focar.
        setTimeout(() => document.getElementById(`produto-${primeiro}`)?.focus())
        return
      }
    }
    salvar.mutate(form)
  }

  // Navegação por setas entre as abas (padrão WAI-ARIA de tabs).
  function onTeclaAba(e: React.KeyboardEvent, indice: number) {
    if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return
    const proxima = ABAS[(indice + (e.key === 'ArrowRight' ? 1 : -1) + ABAS.length) % ABAS.length].value
    setAba(proxima)
    document.getElementById(`aba-${proxima}`)?.focus()
  }

  function voltar() {
    if (alterado && !salvar.isPending) setConfirmandoSaida(true)
    else navigate('/produtos')
  }

  const inputClass = (campo: keyof FormState) =>
    `${baseInputClass} ${erros[campo] ? '!border-danger-500' : ''}`

  if (isEdicao && !produto) {
    return (
      <div className="p-6">
        <PageHeader title="Editar Produto" />
        <Card padding="none"><LoadingState /></Card>
      </div>
    )
  }

  return (
    <div className="p-6">
      <PageHeader
        title={isEdicao ? 'Editar Produto' : 'Novo Produto'}
        subtitle={isEdicao ? `${produto?.codigoInterno} · ${produto?.nome}` : 'Cadastre um produto para venda, estoque e balança'}
        actions={
          <>
            <Button variant="secondary" onClick={voltar}>
              <ArrowLeft size={16} aria-hidden="true" /> Cancelar
            </Button>
            <Button type="submit" form="produto-cadastro-form" variant="primary" loading={salvar.isPending}>
              Salvar Produto
            </Button>
          </>
        }
      />

      <form id="produto-cadastro-form" onSubmit={onSubmit} noValidate>
        <Card padding="none">
          {/* Abas */}
          <div role="tablist" aria-label="Seções do cadastro" className="flex gap-5 border-b border-gray-100 px-6">
            {ABAS.map((a, i) => (
              <button
                key={a.value}
                type="button"
                role="tab"
                id={`aba-${a.value}`}
                aria-selected={aba === a.value}
                aria-controls={`painel-${a.value}`}
                tabIndex={aba === a.value ? 0 : -1}
                onClick={() => setAba(a.value)}
                onKeyDown={e => onTeclaAba(e, i)}
                className={`-mb-px flex items-center gap-1.5 whitespace-nowrap px-1 py-3 text-sm font-medium border-b-2 transition-colors
                  focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 rounded-t
                  ${aba === a.value
                    ? 'border-primary-600 text-primary-700'
                    : 'border-transparent text-gray-500 hover:text-gray-700'}`}
              >
                {a.label}
                {abaTemErro(a.value) && (
                  <>
                    <span className="h-1.5 w-1.5 rounded-full bg-danger-500" aria-hidden="true" />
                    <span className="sr-only">(contém erros)</span>
                  </>
                )}
              </button>
            ))}
          </div>

          {/* Informações Básicas */}
          <div role="tabpanel" id="painel-basico" aria-labelledby="aba-basico" hidden={aba !== 'basico'} className="p-6">
            <div className="grid grid-cols-1 md:grid-cols-12 gap-x-4 gap-y-4">
              <Field label="Código Interno" error={erros.codigoInterno} hint={isEdicao ? 'Não pode ser alterado.' : 'Em branco = automático.'} className="md:col-span-2">
                <input
                  id="produto-codigoInterno"
                  name="codigoInterno"
                  value={form.codigoInterno}
                  onChange={e => set('codigoInterno', e.target.value)}
                  maxLength={20}
                  placeholder="P000123"
                  disabled={isEdicao}
                  className={`${isEdicao ? disabledInputClass : inputClass('codigoInterno')} font-mono`}
                />
              </Field>
              <Field label={exigeEan ? 'Código de Barras *' : 'Código de Barras'} error={erros.ean13} hint="Até 13 dígitos." className="md:col-span-3">
                <input
                  id="produto-ean13"
                  name="ean13"
                  autoFocus={!isEdicao}
                  value={form.ean13}
                  onChange={e => set('ean13', soDigitos(e.target.value))}
                  inputMode="numeric"
                  maxLength={13}
                  placeholder="7890000000000"
                  className={`${inputClass('ean13')} font-mono tabular-nums`}
                />
              </Field>
              <Field label="Descrição Resumida *" error={erros.nome} className="md:col-span-7">
                <input
                  id="produto-nome"
                  name="nome"
                  value={form.nome}
                  onChange={e => set('nome', e.target.value)}
                  maxLength={150}
                  placeholder="Ex: Picanha bovina maturada"
                  className={inputClass('nome')}
                />
              </Field>

              <Field label="Unidade *" className="md:col-span-3">
                <select
                  id="produto-unidadeMedida"
                  name="unidadeMedida"
                  value={form.unidadeMedida}
                  onChange={e => trocarUnidade(e.target.value as FormState['unidadeMedida'])}
                  className={baseInputClass}
                >
                  <option value="KG">KG — Quilo</option>
                  <option value="G">G — Grama</option>
                  <option value="UN">UN — Unidade</option>
                  <option value="CX">CX — Caixa</option>
                </select>
              </Field>
              <Field className="md:col-span-3"
                label="Marca"
                hint={balanca ? 'Só para produtos em UN/CX.' : undefined}
              >
                <input
                  id="produto-marca"
                  name="marca"
                  value={form.marca}
                  onChange={e => set('marca', e.target.value)}
                  disabled={balanca}
                  maxLength={100}
                  placeholder="Ex: Sadia, Perdigão..."
                  className={balanca ? disabledInputClass : baseInputClass}
                />
              </Field>
              <Field label="Tipo *" className="md:col-span-3">
                <select
                  id="produto-tipoProduto"
                  name="tipoProduto"
                  value={form.tipoProduto}
                  onChange={e => set('tipoProduto', e.target.value as FormState['tipoProduto'])}
                  className={baseInputClass}
                >
                  {TIPOS_PRODUTO.map(t => <option key={t.valor} value={t.valor}>{t.nome}</option>)}
                </select>
              </Field>
              <Field className="md:col-span-3"
                label="PLU Balança"
                hint={balanca ? undefined : 'Só para produtos em KG/G.'}
              >
                <input
                  id="produto-codigoBalanca"
                  name="codigoBalanca"
                  value={form.codigoBalanca}
                  onChange={e => set('codigoBalanca', soDigitos(e.target.value))}
                  disabled={!balanca}
                  inputMode="numeric"
                  maxLength={6}
                  placeholder="00001"
                  className={balanca ? `${baseInputClass} font-mono tabular-nums` : disabledInputClass}
                />
              </Field>

              <Field label="Preço de Custo" hint="Recalculado pelas entradas." className="md:col-span-3">
                <CurrencyInput
                  id="produto-precoCusto"
                  name="precoCusto"
                  value={form.precoCusto}
                  onChange={v => set('precoCusto', v)}
                  disabled={isEdicao}
                />
              </Field>
              <Field label="Preço de Venda *" error={erros.precoVenda} className="md:col-span-3">
                <CurrencyInput
                  id="produto-precoVenda"
                  name="precoVenda"
                  value={form.precoVenda}
                  onChange={v => set('precoVenda', v)}
                  aria-invalid={Boolean(erros.precoVenda)}
                />
              </Field>
              <Field label="Margem" error={erroMargem} className="md:col-span-3"
                hint={form.precoCusto > 0 ? 'Sobre o preço de venda. Digite para calcular o preço.' : 'Informe o preço de custo para usar a margem.'}>
                <div className="relative">
                  <input
                    id="produto-margem"
                    name="margem"
                    inputMode="decimal"
                    value={margemTexto ?? (margem === null ? '' : margem.toFixed(1).replace('.', ','))}
                    onChange={e => digitarMargem(e.target.value)}
                    onBlur={() => setMargemTexto(null)}
                    disabled={!(form.precoCusto > 0)}
                    placeholder="—"
                    aria-invalid={Boolean(erroMargem)}
                    className={`${form.precoCusto > 0 ? baseInputClass : disabledInputClass} text-right tabular-nums pr-8 font-semibold ${
                      margem !== null && margemTexto === null ? corMargem(margem) : ''}`}
                  />
                  <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-gray-400 pointer-events-none">%</span>
                </div>
              </Field>
              <Field className="md:col-span-3"
                label="Fornecedor"
                hint={fornecedores.length === 0 && !carregandoFornecedores ? 'Nenhum fornecedor cadastrado ainda.' : undefined}
              >
                <select
                  id="produto-fornecedorId"
                  name="fornecedorId"
                  value={form.fornecedorId ?? ''}
                  onChange={e => set('fornecedorId', e.target.value ? Number(e.target.value) : null)}
                  className={baseInputClass}
                >
                  <option value="">{carregandoFornecedores ? 'Carregando...' : 'Sem fornecedor'}</option>
                  {fornecedorInativo && <option value={fornecedorInativo.id}>{fornecedorInativo.nome}</option>}
                  {fornecedores.map(f => <option key={f.id} value={f.id}>{f.nome}</option>)}
                </select>
                {podeVer('FORNECEDORES') && (
                  <Link to="/estoque/fornecedores" className="text-[11px] text-gray-500 underline hover:text-gray-700 mt-1 inline-block">
                    Não encontrou? Cadastre em Fornecedores
                  </Link>
                )}
              </Field>

              <Field label="Estoque Mín." hint="Gera alerta de reposição." className="md:col-span-3">
                <WeightInput
                  id="produto-estoqueMinimo"
                  name="estoqueMinimo"
                  value={form.estoqueMinimo}
                  onChange={v => set('estoqueMinimo', v)}
                  unit={unidade}
                  decimals={casas}
                />
              </Field>
              <Field label="Estoque Atual" hint={isEdicao ? 'Movimentado pelas entradas e saídas.' : 'Saldo inicial.'} className="md:col-span-3">
                <WeightInput
                  id="produto-estoqueAtual"
                  name="estoqueAtual"
                  value={form.estoqueAtual}
                  onChange={v => set('estoqueAtual', v)}
                  disabled={isEdicao}
                  unit={unidade}
                  decimals={casas}
                />
              </Field>
              <Field label="Estoque Máx." error={erros.estoqueMaximo} className="md:col-span-3">
                <WeightInput
                  id="produto-estoqueMaximo"
                  name="estoqueMaximo"
                  value={form.estoqueMaximo}
                  onChange={v => set('estoqueMaximo', v)}
                  unit={unidade}
                  decimals={casas}
                  aria-invalid={Boolean(erros.estoqueMaximo)}
                />
              </Field>
              <Field className="md:col-span-3"
                label="Validade (dias)"
                error={erros.validadePadraoDias}
                hint="Prazo do lote automático."
              >
                <input
                  id="produto-validadePadraoDias"
                  name="validadePadraoDias"
                  value={form.validadePadraoDias}
                  onChange={e => set('validadePadraoDias', soDigitos(e.target.value))}
                  inputMode="numeric"
                  maxLength={4}
                  placeholder="Ex: 5"
                  className={`${inputClass('validadePadraoDias')} tabular-nums`}
                />
              </Field>
            </div>
            {form.estoqueAtual > 0 && form.estoqueMinimo > 0 && form.estoqueAtual <= form.estoqueMinimo && (
              <p className="flex items-center gap-1.5 text-xs text-warning-700 mt-4">
                <Info size={14} aria-hidden="true" />
                O estoque atual já está no mínimo — o produto entrará nos alertas de reposição.
              </p>
            )}
          </div>

          {/* Informações Fiscais */}
          <div role="tabpanel" id="painel-fiscal" aria-labelledby="aba-fiscal" hidden={aba !== 'fiscal'} className="p-6">
            <div className="grid grid-cols-1 md:grid-cols-12 gap-x-4 gap-y-4">
              <Field label="NCM" error={erros.ncm} hint="8 dígitos. Ex: 02013000." className="md:col-span-4">
                <input
                  id="produto-ncm"
                  name="ncm"
                  value={form.ncm}
                  onChange={e => set('ncm', soDigitos(e.target.value))}
                  inputMode="numeric"
                  maxLength={8}
                  placeholder="00000000"
                  className={`${inputClass('ncm')} font-mono tabular-nums`}
                />
              </Field>
              <Field label="CEST" error={erros.cest} hint="7 dígitos, se houver ST." className="md:col-span-4">
                <input
                  id="produto-cest"
                  name="cest"
                  value={form.cest}
                  onChange={e => set('cest', soDigitos(e.target.value))}
                  inputMode="numeric"
                  maxLength={7}
                  placeholder="0000000"
                  className={`${inputClass('cest')} font-mono tabular-nums`}
                />
              </Field>
              <Field label="CFOP" error={erros.cfop} hint="Venda no estado: 5102." className="md:col-span-4">
                <input
                  id="produto-cfop"
                  name="cfop"
                  value={form.cfop}
                  onChange={e => set('cfop', soDigitos(e.target.value))}
                  inputMode="numeric"
                  maxLength={4}
                  placeholder="5102"
                  className={`${inputClass('cfop')} font-mono tabular-nums`}
                />
              </Field>

              <Field label="Origem da Mercadoria" className="md:col-span-6">
                <select
                  id="produto-origem"
                  name="origem"
                  value={form.origem}
                  onChange={e => set('origem', e.target.value)}
                  className={baseInputClass}
                >
                  {ORIGENS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
              </Field>
              <Field label="CSOSN" hint="Simples Nacional." className="md:col-span-6">
                <select
                  id="produto-csosn"
                  name="csosn"
                  value={form.csosn}
                  onChange={e => set('csosn', e.target.value)}
                  className={baseInputClass}
                >
                  <option value="">Selecione</option>
                  {CSOSNS.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
                </select>
              </Field>
            </div>
          </div>
        </Card>
      </form>

      {confirmandoSaida && (
        <ConfirmDialog
          title="Descartar cadastro?"
          message="Os dados preenchidos neste produto serão perdidos."
          confirmLabel="Descartar"
          onConfirm={() => navigate('/produtos')}
          onCancel={() => setConfirmandoSaida(false)}
        />
      )}
    </div>
  )
}