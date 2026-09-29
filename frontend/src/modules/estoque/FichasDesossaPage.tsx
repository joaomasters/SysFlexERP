import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Scissors, Plus, X, ChevronDown, ChevronUp,
  Pencil, Power, AlertTriangle, CheckCircle2,
} from 'lucide-react'
import { api } from '@/shared/api/axios'
import toast from 'react-hot-toast'
import type { Produto, FichaDesossa } from '@/types/produto'
import { PageHeader, Card, Button, Modal, Field, baseInputClass, ConfirmDialog } from '@/shared/components/ui'

// ─── tipos locais ────────────────────────────────────────────────────────────
interface ItemForm {
  produtoFilhoId: string
  percentualRendimento: string
  sequencia: number
}

const PERC_TOTAL_MAX = 100

// Paleta categórica p/ distinguir cortes na mesma ficha visualmente — não é
// cor de status, então não usa os tokens semânticos.
const CORES_CORTE = [
  'bg-primary-500', 'bg-orange-500', 'bg-mostarda-500', 'bg-yellow-500',
  'bg-lime-500', 'bg-success-500', 'bg-teal-500', 'bg-cyan-500',
  'bg-info-500', 'bg-violet-500', 'bg-purple-500', 'bg-pink-500',
]

// ─── helpers ─────────────────────────────────────────────────────────────────
const perc  = (v: number)  => `${v.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`
const parseP = (s: string) => parseFloat(s.replace(',', '.')) || 0

function corPerda(perda: number) {
  if (perda < 0)  return 'text-danger-600'
  if (perda === 0) return 'text-success-600'
  return 'text-warning-600'
}

// ─── componente de barra de percentual ───────────────────────────────────────
function BarraPercentual({ itens }: { itens: ItemForm[] }) {
  const total = itens.reduce((s, it) => s + parseP(it.percentualRendimento), 0)
  const perda  = PERC_TOTAL_MAX - total
  const excesso = total > PERC_TOTAL_MAX

  return (
    <div className="space-y-1.5">
      <div className="flex justify-between text-xs">
        <span className="text-gray-500">Rendimento total</span>
        <span className={excesso ? 'text-danger-600 font-bold' : 'text-gray-700 font-medium'}>
          {perc(Math.min(total, 100))}
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
            <AlertTriangle size={11} /> Excede 100% — reduza os percentuais
          </span>
        ) : perda > 0 ? (
          <span className="text-warning-600">Perda/sebo/osso: {perc(perda)}</span>
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
interface FormModalProps {
  ficha?: FichaDesossa | null
  produtos: Produto[]
  onClose: () => void
  onSaved: () => void
}

function FormModal({ ficha, produtos, onClose, onSaved }: FormModalProps) {
  const editando = !!ficha

  const [nome,       setNome]       = useState(ficha?.nome ?? '')
  const [descricao,  setDescricao]  = useState(ficha?.descricao ?? '')
  const [prodPaiId,  setProdPaiId]  = useState(ficha?.produtoPai?.id?.toString() ?? '')
  const [itens, setItens] = useState<ItemForm[]>(
    ficha?.itens?.map((it, i) => ({
      produtoFilhoId:      it.produtoFilho.id.toString(),
      percentualRendimento: it.percentualRendimento.toString().replace('.', ','),
      sequencia:            i,
    })) ?? [{ produtoFilhoId: '', percentualRendimento: '', sequencia: 0 }]
  )

  const totalPerc  = itens.reduce((s, it) => s + parseP(it.percentualRendimento), 0)
  const invalido   = totalPerc > PERC_TOTAL_MAX || !nome.trim() || !prodPaiId
    || itens.some(it => !it.produtoFilhoId || !it.percentualRendimento)

  // Produtos disponíveis para produto pai (KG ou CORTE)
  const produtosPai   = produtos.filter(p => p.unidadeMedida === 'KG')
  // Produto pai selecionado
  const paiSel        = produtos.find(p => p.id === parseInt(prodPaiId))
  // Produtos filhos: exclui o produto pai
  const produtosFilhos = produtos.filter(p => p.id !== parseInt(prodPaiId))

  const qc = useQueryClient()
  const salvar = useMutation({
    mutationFn: () => {
      const payload = {
        nome:        nome.trim(),
        descricao:   descricao.trim() || null,
        produtoPaiId: parseInt(prodPaiId),
        itens: itens.map((it, i) => ({
          produtoFilhoId:       parseInt(it.produtoFilhoId),
          percentualRendimento: parseP(it.percentualRendimento),
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
    setItens(prev => [...prev, { produtoFilhoId: '', percentualRendimento: '', sequencia: prev.length }])
  }

  function removeItem(idx: number) {
    setItens(prev => prev.filter((_, i) => i !== idx))
  }

  function updateItem(idx: number, field: keyof ItemForm, val: string) {
    setItens(prev => prev.map((it, i) => i === idx ? { ...it, [field]: val } : it))
  }

  // Distribuir percentual igualmente entre os itens
  function distribuirIgual() {
    if (itens.length === 0) return
    const val = (PERC_TOTAL_MAX / itens.length).toFixed(2).replace('.', ',')
    setItens(prev => prev.map(it => ({ ...it, percentualRendimento: val })))
  }

  return (
    <Modal
      title={editando ? 'Editar Ficha de Desossa' : 'Nova Ficha de Desossa'}
      onClose={onClose}
      maxWidth="lg"
      footer={
        <>
          <Button variant="secondary" fullWidth onClick={onClose}>Cancelar</Button>
          <Button variant="primary" fullWidth disabled={invalido} loading={salvar.isPending} onClick={() => salvar.mutate()}>
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

      {/* produto pai */}
      <Field label="Produto de Entrada (Pai) *" hint={paiSel ? `Estoque atual: ${paiSel.estoqueAtual} ${paiSel.unidadeMedida}` : undefined}>
        <select value={prodPaiId} onChange={e => setProdPaiId(e.target.value)} className={baseInputClass}>
          <option value="">Selecione o produto que entra...</option>
          {produtosPai.map(p => (
            <option key={p.id} value={p.id}>{p.nome} ({p.unidadeMedida})</option>
          ))}
        </select>
      </Field>

      {/* itens / cortes */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <label className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
            Cortes (saídas) *
          </label>
          <div className="flex gap-3">
            <button onClick={distribuirIgual} className="text-xs text-info-600 hover:text-info-700 font-medium">
              Distribuir igualmente
            </button>
            <button onClick={addItem} className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 font-medium">
              <Plus size={12} /> Adicionar corte
            </button>
          </div>
        </div>

        <div className="space-y-2 max-h-64 overflow-y-auto pr-1">
          {itens.map((item, idx) => (
            <div key={idx} className="grid grid-cols-12 gap-2 items-center bg-gray-50 border border-gray-100 rounded-lg p-2.5">
              <div className="col-span-1 text-center text-xs text-gray-400 font-mono">{idx + 1}</div>
              <div className="col-span-7">
                <select
                  value={item.produtoFilhoId}
                  onChange={e => updateItem(idx, 'produtoFilhoId', e.target.value)}
                  className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm bg-white
                             focus:outline-none focus:ring-2 focus:ring-primary-500"
                >
                  <option value="">Produto filho...</option>
                  {produtosFilhos.map(p => (
                    <option key={p.id} value={p.id}>{p.nome}</option>
                  ))}
                </select>
              </div>
              <div className="col-span-3 relative">
                <input
                  type="text"
                  inputMode="decimal"
                  value={item.percentualRendimento}
                  onChange={e => updateItem(idx, 'percentualRendimento', e.target.value)}
                  placeholder="0,00"
                  className="w-full border border-gray-300 rounded px-2 py-1.5 pr-6 bg-white
                             text-sm text-right focus:outline-none focus:ring-2 focus:ring-primary-500"
                />
                <span className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 text-xs">%</span>
              </div>
              <div className="col-span-1 flex justify-center">
                <button
                  onClick={() => removeItem(idx)}
                  disabled={itens.length === 1}
                  className="text-gray-400 hover:text-danger-500 disabled:opacity-30"
                >
                  <X size={14} />
                </button>
              </div>
            </div>
          ))}
        </div>

        <div className="mt-3">
          <BarraPercentual itens={itens} />
        </div>
      </div>
    </Modal>
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

  return (
    <div className="p-6">
      <PageHeader
        title="Fichas de Desossa"
        subtitle="Configure os cortes e percentuais de rendimento de cada peça"
        actions={
          <Button variant="primary" onClick={() => { setEditFicha(null); setShowForm(true) }}>
            <Plus size={16} /> Nova Ficha
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

        {fichas.map(ficha => {
          const totalPerc = ficha.itens.reduce((s, it) => s + it.percentualRendimento, 0)
          const perda     = 100 - totalPerc
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
                  <Button variant="ghost" size="sm" onClick={e => { e.stopPropagation(); abrirEditar(ficha) }} title="Editar">
                    <Pencil size={15} />
                  </Button>
                  <Button
                    variant="ghost" size="sm"
                    className={ficha.ativo ? 'hover:!text-danger-600' : 'hover:!text-success-600'}
                    onClick={e => {
                      e.stopPropagation()
                      ficha.ativo ? setConfirmando(ficha) : reativar.mutate(ficha.id)
                    }}
                    title={ficha.ativo ? 'Inativar' : 'Reativar'}
                  >
                    <Power size={15} />
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
      </div>

      {showForm && (
        <FormModal
          ficha={editFicha}
          produtos={produtos}
          onClose={fecharForm}
          onSaved={fecharForm}
        />
      )}

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