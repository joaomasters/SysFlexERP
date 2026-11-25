import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Scissors, Plus, X, ChevronDown, ChevronUp,
  Pencil, Power, AlertTriangle, CheckCircle2,
} from 'lucide-react'
import { api } from '@/shared/api/axios'
import toast from 'react-hot-toast'
import type { Produto, FichaDesossa } from '@/types/produto'
import { PageHeader, Card, Button, Field, baseInputClass, ConfirmDialog, Paginacao, FormularioPagina, BuscaProduto, WeightInput } from '@/shared/components/ui'
import { usePaginacao } from '@/shared/hooks/usePaginacao'
import { formatWeight } from '@/shared/utils/mask'

// ─── tipos locais ────────────────────────────────────────────────────────────
interface ItemForm {
  produtoFilhoId: string
  /** Peso do corte em kg: é o que se digita. O % é calculado a partir dele e do peso da peça. */
  kg: number
  sequencia: number
}

const PERC_TOTAL_MAX = 100
/** A ficha guarda só percentuais. Ao editar uma ficha já salva, parte-se de 100 kg (kg = %). */
const PESO_BASE_FICHA_SALVA = 100

// Paleta categórica p/ distinguir cortes na mesma ficha visualmente — não é
// cor de status, então usa matizes distintos (inclusive âmbar/amarelo crus) em vez dos tokens semânticos.
const CORES_CORTE = [
  'bg-primary-500', 'bg-orange-500', 'bg-amber-500', 'bg-yellow-400',
  'bg-lime-500', 'bg-success-500', 'bg-teal-500', 'bg-cyan-500',
  'bg-info-500', 'bg-violet-500', 'bg-purple-500', 'bg-pink-500',
]

// ─── helpers ─────────────────────────────────────────────────────────────────
const perc  = (v: number)  => `${v.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`
const arred2 = (v: number) => Math.round(v * 100) / 100
const kgDePercentual = (pct: number, pesoPeca: number) => Math.round((pct * pesoPeca) / 100 * 1000) / 1000

/**
 * % de cada corte a partir do kg e do peso da peça, arredondado a 2 casas (como o banco guarda,
 * NUMERIC(5,2)). Se o arredondamento empurrar o total acima de 100% sem que os kg excedam o peso
 * (ex.: 6 cortes iguais), tira centavos dos maiores para a soma nunca passar de 100.
 */
function calcularPercentuais(kgs: number[], pesoPeca: number): number[] {
  if (pesoPeca <= 0) return kgs.map(() => 0)
  const exatos = kgs.map(k => (k / pesoPeca) * 100)
  const out = exatos.map(arred2)
  const somaExata = exatos.reduce((s, v) => s + v, 0)
  let centavos = Math.round((out.reduce((s, v) => s + v, 0) - PERC_TOTAL_MAX) * 100)
  if (somaExata <= PERC_TOTAL_MAX + 1e-9 && centavos > 0) {
    const ordem = out.map((v, i) => [v, i] as const).sort((a, b) => b[0] - a[0]).map(([, i]) => i)
    for (let n = 0; centavos > 0 && n < ordem.length * 2; n++, centavos--) {
      const i = ordem[n % ordem.length]
      out[i] = arred2(out[i] - 0.01)
    }
  }
  return out
}

function corPerda(perda: number) {
  if (perda < 0)  return 'text-danger-600'
  if (perda === 0) return 'text-success-600'
  return 'text-warning-600'
}

// ─── componente de barra de percentual ───────────────────────────────────────
function BarraPercentual({ total, excesso, totalKg, pesoPeca }: { total: number; excesso: boolean; totalKg: number; pesoPeca: number }) {
  const perda = Math.max(PERC_TOTAL_MAX - total, 0)
  const perdaKg = Math.max(pesoPeca - totalKg, 0)
  const comPeso = pesoPeca > 0

  return (
    <div className="space-y-1.5">
      <div className="flex justify-between text-xs">
        <span className="text-gray-500">Rendimento total</span>
        <span className={excesso ? 'text-danger-600 font-bold' : 'text-gray-700 font-medium'}>
          {perc(Math.min(total, 100))}
          {comPeso && <span className="font-normal text-gray-500"> · {formatWeight(totalKg)} de {formatWeight(pesoPeca)}</span>}
        </span>
      </div>
      <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
        <div
          className={`h-full rounded-full transition-all ${excesso ? 'bg-danger-500' : total >= 100 ? 'bg-success-500' : 'bg-info-500'}`}
          style={{ width: `${Math.min(total, 100)}%` }}
        />
      </div>
      <div className="flex justify-between text-xs">
        {excesso ? (
          <span className="text-danger-600 flex items-center gap-1">
            <AlertTriangle size={11} /> Os cortes somam mais que o peso da peça — reduza os kg
          </span>
        ) : perda > 0 ? (
          <span className="text-warning-600">
            Perda/sebo/osso: {perc(perda)}{comPeso && ` (${formatWeight(perdaKg)})`}
          </span>
        ) : (
          <span className="text-success-600 flex items-center gap-1">
            <CheckCircle2 size={11} /> Rendimento completo (sem perda)
          </span>
        )}
      </div>
    </div>
  )
}

// ════════════════════════════════════════════════════════════════════════════
//  MODAL DE FORMULÁRIO
// ════════════════════════════════════════════════════════════════════════════
interface FormFichaProps {
  ficha?: FichaDesossa | null
  produtos: Produto[]
  onClose: () => void
  onSaved: () => void
}

function FormFicha({ ficha, produtos, onClose, onSaved }: FormFichaProps) {
  const editando = !!ficha

  const [nome,       setNome]       = useState(ficha?.nome ?? '')
  const [descricao,  setDescricao]  = useState(ficha?.descricao ?? '')
  const [prodPaiId,  setProdPaiId]  = useState(ficha?.produtoPai?.id?.toString() ?? '')
  const [itens, setItens] = useState<ItemForm[]>(
    ficha?.itens?.map((it, i) => ({
      produtoFilhoId:      it.produtoFilho.id.toString(),
      kg:                   kgDePercentual(it.percentualRendimento, PESO_BASE_FICHA_SALVA),
      sequencia:            i,
    })) ?? [{ produtoFilhoId: '', kg: 0, sequencia: 0 }]
  )
  // Peso da peça de entrada: base para converter kg em %. Não é gravado (a ficha guarda os %).
  const [pesoPeca, setPesoPeca] = useState(editando ? PESO_BASE_FICHA_SALVA : 0)

  const totalKg      = itens.reduce((s, it) => s + it.kg, 0)
  const percentuais  = calcularPercentuais(itens.map(it => it.kg), pesoPeca)
  const totalPerc    = percentuais.reduce((s, v) => s + v, 0)
  const excedePeso   = pesoPeca > 0 && totalKg > pesoPeca + 1e-9
  const invalido     = !nome.trim() || !prodPaiId || pesoPeca <= 0 || excedePeso
    || itens.some((it, i) => !it.produtoFilhoId || !(it.kg > 0) || percentuais[i] <= 0)

  // Produtos disponíveis para produto pai (KG ou CORTE)
  const produtosPai   = produtos.filter(p => p.unidadeMedida === 'KG')
  // Produto pai selecionado
  const paiSel        = produtos.find(p => p.id === parseInt(prodPaiId))
  // Cortes que podem sair da peça: só produtos do tipo "Corte de Carne" (e nunca o próprio pai)
  const cortesDisponiveis = produtos.filter(p => p.tipoProduto === 'CORTE' && p.id !== parseInt(prodPaiId))

  const qc = useQueryClient()
  const salvar = useMutation({
    mutationFn: () => {
      const payload = {
        nome:        nome.trim(),
        descricao:   descricao.trim() || null,
        produtoPaiId: parseInt(prodPaiId),
        itens: itens.map((it, i) => ({
          produtoFilhoId:       parseInt(it.produtoFilhoId),
          percentualRendimento: percentuais[i],
          sequencia:            i,
        })),
      }
      return editando
        ? api.put(`/estoque/fichas-desossa/${ficha!.id}`, payload)
        : api.post('/estoque/fichas-desossa', payload)
    },
    onSuccess: () => {
      toast.success(editando ? 'Ficha atualizada!' : 'Ficha criada!')
      qc.invalidateQueries({ queryKey: ['fichas-desossa'] })
      onSaved()
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Erro ao salvar'
      toast.error(msg)
    },
  })

  function addItem() {
    setItens(prev => [...prev, { produtoFilhoId: '', kg: 0, sequencia: prev.length }])
  }

  function removeItem(idx: number) {
    setItens(prev => prev.filter((_, i) => i !== idx))
  }

  function updateItem(idx: number, patch: Partial<ItemForm>) {
    setItens(prev => prev.map((it, i) => i === idx ? { ...it, ...patch } : it))
  }

  // Divide o peso da peça igualmente entre os cortes (trunca em 3 casas: a soma nunca passa do peso)
  function distribuirIgual() {
    if (itens.length === 0 || pesoPeca <= 0) return
    const kg = Math.floor((pesoPeca / itens.length) * 1000) / 1000
    setItens(prev => prev.map(it => ({ ...it, kg })))
  }

  return (
    <FormularioPagina
      title={editando ? 'Editar Ficha de Desossa' : 'Nova Ficha de Desossa'}
      subtitle="Cortes e percentuais de rendimento da peça"
      onClose={onClose}
      actions={
        <>
          <Button variant="primary" disabled={invalido} loading={salvar.isPending} onClick={() => salvar.mutate()}>
            {editando ? 'Salvar alterações' : 'Criar ficha'}
          </Button>
        </>
      }
    >
      <Field label="Nome da ficha *">
        <input
          value={nome}
          onChange={e => setNome(e.target.value)}
          placeholder="Ex: Desossa Boi Nelore"
          className={baseInputClass}
        />
      </Field>
      <Field label="Descrição">
        <input
          value={descricao}
          onChange={e => setDescricao(e.target.value)}
          placeholder="Observações opcionais"
          className={baseInputClass}
        />
      </Field>

      {/* produto pai + peso da peça */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-12">
        <div className="sm:col-span-8">
          <Field label="Produto de Entrada (Pai) *" hint={paiSel ? `Estoque atual: ${paiSel.estoqueAtual} ${paiSel.unidadeMedida}` : undefined}>
            <select value={prodPaiId} onChange={e => setProdPaiId(e.target.value)} className={baseInputClass}>
              <option value="">Selecione o produto que entra...</option>
              {produtosPai.map(p => (
                <option key={p.id} value={p.id}>{p.nome} ({p.unidadeMedida})</option>
              ))}
            </select>
          </Field>
        </div>
        <div className="sm:col-span-4">
          <Field
            label="Peso da peça (kg) *"
            hint={editando
              ? 'Ficha salva em %: parte de 100 kg (kg = %). Alterar o peso recalcula os %.'
              : 'Base para converter os kg dos cortes em %. A ficha guarda só os percentuais.'}
          >
            <WeightInput value={pesoPeca} onChange={setPesoPeca} />
          </Field>
        </div>
      </div>

      {/* itens / cortes */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <label className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
            Cortes (saídas) *
          </label>
          <div className="flex gap-3">
            <button
              onClick={distribuirIgual}
              disabled={pesoPeca <= 0}
              title={pesoPeca <= 0 ? 'Informe o peso da peça' : 'Divide o peso da peça igualmente entre os cortes'}
              className="text-xs text-info-600 hover:text-info-700 font-medium disabled:opacity-40 disabled:cursor-not-allowed"
            >
              Distribuir igualmente
            </button>
            <button onClick={addItem} className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 font-medium">
              <Plus size={12} /> Adicionar corte
            </button>
          </div>
        </div>

        {cortesDisponiveis.length === 0 && (
          <p className="mb-2 text-xs text-warning-700">
            Nenhum produto cadastrado como "Corte de Carne". Cadastre em Produtos (tipo "Corte de Carne") para usá-lo aqui.
          </p>
        )}

        {/* Títulos das colunas (telas médias em diante) */}
        <div className="hidden sm:grid grid-cols-12 gap-2 px-2.5 mb-1 text-[11px] font-medium text-gray-500">
          <div className="col-span-1 text-center">#</div>
          <div className="col-span-5">Corte</div>
          <div className="col-span-3 text-right pr-3">Peso (kg)</div>
          <div className="col-span-2 text-right pr-3">% do rendimento</div>
          <div className="col-span-1" />
        </div>

        <div className="space-y-2">
          {itens.map((item, idx) => {
            // Ficha antiga pode ter um corte que não é "Corte de Carne" (ex.: sebo/osso): continua
            // visível nesta linha para não aparecer vazio, com um aviso. Novas escolhas são só cortes.
            const idSel = parseInt(item.produtoFilhoId) || 0
            const selecionado = produtos.find(p => p.id === idSel)
            const foraDoTipo = !!selecionado && selecionado.tipoProduto !== 'CORTE'
            const opcoes = foraDoTipo ? [...cortesDisponiveis, selecionado] : cortesDisponiveis
            return (
            <div key={idx} className="grid grid-cols-12 gap-2 items-start bg-gray-50 border border-gray-100 rounded-lg p-2.5">
              <div className="col-span-1 pt-2 text-center text-xs text-gray-400 font-mono">{idx + 1}</div>
              <div className="col-span-11 sm:col-span-5">
                <BuscaProduto
                  produtos={opcoes}
                  value={idSel}
                  onSelect={p => updateItem(idx, { produtoFilhoId: String(p.id) })}
                  placeholder="Buscar corte por nome ou código..."
                />
                {foraDoTipo && (
                  <p className="mt-1 text-[11px] text-warning-700">
                    Este produto não é do tipo "Corte de Carne". Troque por um corte para manter a ficha padronizada.
                  </p>
                )}
              </div>
              <div className="col-span-5 col-start-2 sm:col-span-3 sm:col-start-auto">
                <WeightInput value={item.kg} onChange={kg => updateItem(idx, { kg })} size="sm" aria-label={`Peso do corte ${idx + 1} em kg`} />
              </div>
              <div className="col-span-4 pt-1.5 pr-3 text-right text-sm tabular-nums text-gray-700 sm:col-span-2" title="Calculado a partir do kg e do peso da peça">
                {pesoPeca > 0 ? perc(percentuais[idx]) : '—'}
              </div>
              <div className="col-span-2 flex justify-center pt-2 sm:col-span-1">
                <button
                  onClick={() => removeItem(idx)}
                  disabled={itens.length === 1}
                  className="text-gray-400 hover:text-danger-500 disabled:opacity-30"
                >
                  <X size={14} />
                </button>
              </div>
            </div>
            )
          })}
        </div>

        <div className="mt-3">
          <BarraPercentual total={totalPerc} excesso={excedePeso} totalKg={totalKg} pesoPeca={pesoPeca} />
        </div>
      </div>
    </FormularioPagina>
  )
}

// ════════════════════════════════════════════════════════════════════════════
//  PÁGINA PRINCIPAL
// ════════════════════════════════════════════════════════════════════════════
export default function FichasDesossaPage() {
  const qc = useQueryClient()

  const [showForm,   setShowForm]   = useState(false)
  const [editFicha,  setEditFicha]  = useState<FichaDesossa | null>(null)
  const [expandId,   setExpandId]   = useState<number | null>(null)
  const [mostrarInativas, setMostrarInativas] = useState(false)
  const [confirmando, setConfirmando] = useState<FichaDesossa | null>(null)

  const { data: fichas = [], isLoading } = useQuery<FichaDesossa[]>({
    queryKey: ['fichas-desossa', mostrarInativas],
    queryFn: () => api.get(`/estoque/fichas-desossa?todas=${mostrarInativas}`).then(r => r.data),
  })
  const pag = usePaginacao(fichas, String(mostrarInativas))

  const { data: produtos = [] } = useQuery<Produto[]>({
    queryKey: ['produtos'],
    queryFn: () => api.get('/estoque/produtos').then(r => r.data),
  })

  const inativar = useMutation({
    mutationFn: (id: number) => api.delete(`/estoque/fichas-desossa/${id}`),
    onSuccess: () => {
      toast.success('Ficha inativada')
      qc.invalidateQueries({ queryKey: ['fichas-desossa'] })
      setConfirmando(null)
    },
  })

  const reativar = useMutation({
    mutationFn: (id: number) => api.patch(`/estoque/fichas-desossa/${id}/reativar`),
    onSuccess: () => { toast.success('Ficha reativada'); qc.invalidateQueries({ queryKey: ['fichas-desossa'] }) },
  })

  const stats = useMemo(() => ({
    total:   fichas.length,
    ativas:  fichas.filter(f => f.ativo).length,
    totalCortes: fichas.reduce((s, f) => s + f.itens.length, 0),
  }), [fichas])

  function abrirEditar(ficha: FichaDesossa) {
    setEditFicha(ficha)
    setShowForm(true)
  }

  function fecharForm() {
    setShowForm(false)
    setEditFicha(null)
  }

  // Formulário em tela cheia (padrão da tela de Produto): enquanto aberto, substitui a listagem.
  if (showForm) {
    return (
      <FormFicha
        ficha={editFicha}
        produtos={produtos}
        onClose={fecharForm}
        onSaved={fecharForm}
      />
    )
  }

  return (
    <div className="p-4 sm:p-6">
      <PageHeader
        title="Fichas de Desossa"
        subtitle="Configure os cortes e percentuais de rendimento de cada peça"
        actions={
          <Button variant="primary" onClick={() => { setEditFicha(null); setShowForm(true) }}>
            Nova Ficha
          </Button>
        }
      />

      {/* Stats */}
      <div className="grid grid-cols-3 gap-3 mb-5">
        {[
          { label: 'Fichas ativas',    value: stats.ativas },
          { label: 'Total de fichas',  value: stats.total },
          { label: 'Total de cortes',  value: stats.totalCortes },
        ].map(s => (
          <Card key={s.label} padding="sm" className="text-center">
            <p className="text-2xl font-black text-gray-800">{s.value}</p>
            <p className="text-xs text-gray-400 mt-0.5">{s.label}</p>
          </Card>
        ))}
      </div>

      {/* Filtro */}
      <div className="flex items-center gap-2 mb-4">
        <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer select-none">
          <input
            type="checkbox"
            checked={mostrarInativas}
            onChange={e => setMostrarInativas(e.target.checked)}
            className="rounded"
          />
          Mostrar fichas inativas
        </label>
      </div>

      {/* Lista */}
      <div className="space-y-3">
        {isLoading && <p className="text-gray-400 text-sm">Carregando...</p>}

        {pag.itens.map(ficha => {
          const totalPerc = ficha.itens.reduce((s, it) => s + it.percentualRendimento, 0)
          const perda     = 100 - totalPerc
          const mediaPerc = ficha.itens.length ? totalPerc / ficha.itens.length : 0
          const expanded  = expandId === ficha.id

          return (
            <div
              key={ficha.id}
              className={`bg-white rounded-xl shadow border border-gray-100 overflow-hidden transition-opacity
                ${!ficha.ativo ? 'opacity-60' : ''}`}
            >
              <div
                className="flex items-center gap-4 px-5 py-4 cursor-pointer hover:bg-gray-50"
                onClick={() => setExpandId(expanded ? null : ficha.id)}
              >
                <div className="flex-1 grid grid-cols-4 gap-2">
                  <div className="col-span-2">
                    <div className="flex items-center gap-2">
                      <p className="font-semibold text-gray-800">{ficha.nome}</p>
                      {!ficha.ativo && (
                        <span className="text-xs bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded">Inativa</span>
                      )}
                    </div>
                    <p className="text-xs text-gray-400 mt-0.5">
                      Entrada: <span className="font-medium text-gray-600">{ficha.produtoPai.nome}</span>
                    </p>
                  </div>
                  <div className="text-center">
                    <p className="text-sm font-bold text-gray-700">{ficha.itens.length}</p>
                    <p className="text-xs text-gray-400">cortes</p>
                  </div>
                  <div className="text-center">
                    <p className={`text-sm font-bold ${corPerda(perda)}`}>
                      {perda > 0 ? perc(perda) : '0%'}
                    </p>
                    <p className="text-xs text-gray-400">perda</p>
                  </div>
                </div>

                <div className="flex items-center gap-1 shrink-0">
                  <Button variant="secondary" size="sm" onClick={e => { e.stopPropagation(); abrirEditar(ficha) }} title="Editar">
                    Editar
                  </Button>
                  <Button
                    variant="outline-danger" size="sm"
                    className={ficha.ativo ? 'hover:!text-danger-600' : 'hover:!text-success-600'}
                    onClick={e => {
                      e.stopPropagation()
                      ficha.ativo ? setConfirmando(ficha) : reativar.mutate(ficha.id)
                    }}
                    title={ficha.ativo ? 'Inativar' : 'Reativar'}
                  >
                    Inativar
                  </Button>
                  {expanded
                    ? <ChevronUp size={16} className="text-gray-400" />
                    : <ChevronDown size={16} className="text-gray-400" />}
                </div>
              </div>

              {/* detalhes expandidos */}
              {expanded && (
                <div className="border-t bg-gray-50 px-5 py-4">
                  {ficha.descricao && (
                    <p className="text-xs text-gray-500 mb-3 italic">{ficha.descricao}</p>
                  )}

                  {/* barra visual de rendimento */}
                  <div className="mb-4">
                    <div className="flex gap-0.5 h-5 rounded-lg overflow-hidden">
                      {ficha.itens.map((item, i) => (
                        <div
                          key={item.id}
                          className={`${CORES_CORTE[i % CORES_CORTE.length]} flex items-center justify-center`}
                          style={{ width: `${item.percentualRendimento}%` }}
                          title={`${item.produtoFilho.nome}: ${perc(item.percentualRendimento)}`}
                        />
                      ))}
                      {perda > 0 && (
                        <div
                          className="bg-gray-300 flex items-center justify-center"
                          style={{ width: `${perda}%` }}
                          title={`Perda/osso/sebo: ${perc(perda)}`}
                        />
                      )}
                    </div>
                    <div className="flex items-center gap-3 mt-1.5 flex-wrap">
                      {ficha.itens.map((item, i) => (
                        <span key={item.id} className="flex items-center gap-1 text-xs text-gray-500">
                          <span className={`inline-block w-2 h-2 rounded-full ${CORES_CORTE[i % CORES_CORTE.length]}`} />
                          {item.produtoFilho.nome}
                        </span>
                      ))}
                      {perda > 0 && (
                        <span className="flex items-center gap-1 text-xs text-gray-400">
                          <span className="inline-block w-2 h-2 rounded-full bg-gray-300" />
                          Perda
                        </span>
                      )}
                    </div>
                  </div>

                  {/* tabela de cortes */}
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-xs text-gray-500 uppercase">
                        <th className="text-left pb-2">#</th>
                        <th className="text-left pb-2">Corte (produto filho)</th>
                        <th className="text-right pb-2">% Rendimento</th>
                        <th className="text-right pb-2">Unidade</th>
                      </tr>
                    </thead>
                    <tbody>
                      {ficha.itens.map((item, idx) => (
                        <tr key={item.id} className="border-t border-gray-200">
                          <td className="py-2 text-gray-400 text-xs">{idx + 1}</td>
                          <td className="py-2 font-medium text-gray-700">{item.produtoFilho.nome}</td>
                          <td className="py-2 text-right tabular-nums">{perc(item.percentualRendimento)}</td>
                          <td className="py-2 text-right text-gray-400">{item.produtoFilho.unidadeMedida}</td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr className="border-t-2 border-gray-300">
                        <td colSpan={2} className="pt-2 text-xs text-gray-500 uppercase font-semibold">Total rendimento</td>
                        <td className={`pt-2 text-right font-bold tabular-nums ${corPerda(perda)}`}>
                          {perc(totalPerc)}
                        </td>
                        <td />
                      </tr>
                      <tr>
                        <td colSpan={2} className="pt-1 text-xs text-gray-500 uppercase font-semibold">
                          Média por corte
                          <span className="ml-1 normal-case font-normal text-gray-400">({ficha.itens.length} {ficha.itens.length === 1 ? 'corte' : 'cortes'})</span>
                        </td>
                        <td className="pt-1 text-right font-semibold tabular-nums text-gray-700">{perc(mediaPerc)}</td>
                        <td />
                      </tr>
                      {perda > 0 && (
                        <tr>
                          <td colSpan={2} className="text-xs text-gray-400">Perda / osso / sebo</td>
                          <td className="text-right text-xs text-warning-600 tabular-nums">{perc(perda)}</td>
                          <td />
                        </tr>
                      )}
                    </tfoot>
                  </table>
                </div>
              )}
            </div>
          )
        })}

        {!isLoading && fichas.length === 0 && (
          <div className="bg-white rounded-xl border p-12 text-center">
            <Scissors size={40} className="mx-auto text-gray-300 mb-3" />
            <p className="text-gray-500 font-medium">Nenhuma ficha de desossa cadastrada</p>
            <p className="text-gray-400 text-sm mt-1">Crie a primeira ficha para começar</p>
          </div>
        )}
        {!isLoading && <Paginacao solta paginacao={pag} rotulo="fichas" />}
      </div>


      {/* Confirmação de inativação */}
      {confirmando && (
        <ConfirmDialog
          title="Inativar ficha de desossa?"
          message={`"${confirmando.nome}" deixará de aparecer na tela de Desossa para novas execuções.`}
          confirmLabel="Inativar"
          loading={inativar.isPending}
          onConfirm={() => inativar.mutate(confirmando.id)}
          onCancel={() => setConfirmando(null)}
        />
      )}
    </div>
  )
}