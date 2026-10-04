import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, FileText, X, ChevronDown, ChevronUp, Upload, Filter } from 'lucide-react'
import { api } from '@/shared/api/axios'
import toast from 'react-hot-toast'
import { formatBRL, formatWeightDisplay } from '@/shared/utils/mask'
import { PageHeader, Card, Modal, Button, CurrencyInput, WeightInput, Field, baseInputClass } from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'

interface Produto { id: number; nome: string; unidadeMedida: string }
interface RecItem { produtoId: number; quantidade: number; custoUnitario: number }

interface Recebimento {
  id: number
  fornecedor: string
  numeroNf: string
  serieNf: string
  chaveNf: string
  dataEmissao: string
  dataRecebimento: string
  valorTotal: number
  status: string
  xmlNf: string | null
  observacao: string | null
  usuarioNome?: string   // só vem para administradores
  itens: { id: number; produto: { nome: string; unidadeMedida: string }; quantidade: number; custoUnitario: number; custoTotal: number }[]
}

const brl = (v?: number) => formatBRL(v ?? 0)
const kg3 = formatWeightDisplay

const hoje = new Date().toISOString().slice(0, 10)
const trintaDiasAtras = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)

export default function RecebimentoPage() {
  const qc = useQueryClient()
  const fileRef = useRef<HTMLInputElement>(null)
  const { podeVerIdentificacao } = usePermissao()

  const [showForm, setShowForm]       = useState(false)
  const [expandId, setExpandId]       = useState<number | null>(null)
  const [xmlViewId, setXmlViewId]     = useState<number | null>(null)

  // Filtros da listagem — por padrão, últimos 30 dias, pra não carregar
  // o histórico inteiro de recebimentos de uma vez.
  const [filtroInicio, setFiltroInicio]         = useState(trintaDiasAtras)
  const [filtroFim, setFiltroFim]               = useState(hoje)
  const [filtroFornecedor, setFiltroFornecedor] = useState('')

  // Form state
  const [fornecedor, setFornecedor]   = useState('')
  const [numeroNf, setNumeroNf]       = useState('')
  const [serieNf, setSerieNf]         = useState('1')
  const [chaveNf, setChaveNf]         = useState('')
  const [dataEmissao, setDataEmissao] = useState('')
  const [valorTotal, setValorTotal]   = useState(0)
  const [observacao, setObservacao]   = useState('')
  const [xmlNf, setXmlNf]             = useState('')
  const [itens, setItens]             = useState<RecItem[]>([{ produtoId: 0, quantidade: 0, custoUnitario: 0 }])

  const { data: produtos = [] } = useQuery<Produto[]>({
    queryKey: ['produtos'],
    queryFn: () => api.get('/estoque/produtos').then(r => r.data),
  })

  const { data: recebimentos = [], isLoading } = useQuery<Recebimento[]>({
    queryKey: ['recebimentos', filtroInicio, filtroFim, filtroFornecedor],
    queryFn: () => api.get('/estoque/recebimentos', {
      params: { inicio: filtroInicio, fim: filtroFim, fornecedor: filtroFornecedor || undefined },
    }).then(r => r.data),
  })

  const registrar = useMutation({
    mutationFn: () => api.post('/estoque/recebimentos', {
      fornecedor,
      numeroNf: numeroNf || null,
      serieNf,
      chaveNf: chaveNf || null,
      dataEmissao: dataEmissao || null,
      valorTotal: valorTotal || null,
      observacao: observacao || null,
      xmlNf: xmlNf || null,
      itens: itens.filter(i => i.produtoId > 0 && i.quantidade > 0).map(i => ({
        produtoId: i.produtoId,
        quantidade: i.quantidade,
        custoUnitario: i.custoUnitario || null,
      })),
    }),
    onSuccess: () => {
      toast.success('Recebimento registrado e estoque atualizado!')
      qc.invalidateQueries({ queryKey: ['recebimentos'] })
      qc.invalidateQueries({ queryKey: ['produtos'] })
      resetForm()
    },
  })

  const uploadXml = useMutation({
    mutationFn: ({ id, xml }: { id: number; xml: string }) =>
      api.put(`/estoque/recebimentos/${id}/xml`, { xml }),
    onSuccess: () => {
      toast.success('XML vinculado!')
      qc.invalidateQueries({ queryKey: ['recebimentos'] })
    },
  })

  function resetForm() {
    setShowForm(false)
    setFornecedor(''); setNumeroNf(''); setSerieNf('1')
    setChaveNf(''); setDataEmissao(''); setValorTotal(0)
    setObservacao(''); setXmlNf('')
    setItens([{ produtoId: 0, quantidade: 0, custoUnitario: 0 }])
  }

  function addItem() {
    setItens(prev => [...prev, { produtoId: 0, quantidade: 0, custoUnitario: 0 }])
  }

  function removeItem(idx: number) {
    setItens(prev => prev.filter((_, i) => i !== idx))
  }

  function updateItem(idx: number, field: keyof RecItem, val: number) {
    setItens(prev => prev.map((item, i) => i === idx ? { ...item, [field]: val } : item))
  }

  function handleXmlFile(e: React.ChangeEvent<HTMLInputElement>, forId?: number) {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = ev => {
      const content = ev.target?.result as string
      if (forId) {
        uploadXml.mutate({ id: forId, xml: content })
      } else {
        setXmlNf(content)
        toast.success('XML carregado no formulário')
      }
    }
    reader.readAsText(file)
    e.target.value = ''
  }

  const canSave = fornecedor.trim() !== '' && itens.some(i => i.produtoId > 0 && i.quantidade > 0)

  const rec = recebimentos.find(r => r.id === xmlViewId)

  return (
    <div className="p-6">
      <PageHeader
        title="Recebimento de Mercadoria"
        subtitle="Entrada de Nota fiscal"
        actions={
          <Button variant="primary" onClick={() => setShowForm(true)}>
            <Plus size={16} /> Nova Entrada
          </Button>
        }
      />

      {/* Filtros */}
      <Card padding="sm" className="mb-4 flex flex-wrap items-end gap-3">
        <Filter size={16} className="text-gray-400 mb-2" />
        <Field label="Entrada de">
          <input type="date" value={filtroInicio} onChange={e => setFiltroInicio(e.target.value)}
            className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm" />
        </Field>
        <Field label="até">
          <input type="date" value={filtroFim} onChange={e => setFiltroFim(e.target.value)}
            className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm" />
        </Field>
        <Field label="Fornecedor" className="flex-1 min-w-[180px]">
          <input type="text" value={filtroFornecedor} onChange={e => setFiltroFornecedor(e.target.value)}
            placeholder="Buscar por nome..."
            className="w-full border border-gray-300 rounded-lg px-3 py-1.5 text-sm" />
        </Field>
        {(filtroFornecedor || filtroInicio !== trintaDiasAtras || filtroFim !== hoje) && (
          <button
            onClick={() => { setFiltroInicio(trintaDiasAtras); setFiltroFim(hoje); setFiltroFornecedor('') }}
            className="text-xs text-primary-600 hover:text-primary-700 font-medium pb-2"
          >
            Limpar filtros
          </button>
        )}
      </Card>

      {/* Lista */}
      <div className="space-y-2">
        {isLoading && <p className="text-gray-400 text-sm">Carregando...</p>}
        {recebimentos.map(r => (
          <div key={r.id} className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
            <div
              className="flex items-center gap-4 px-5 py-4 cursor-pointer hover:bg-gray-50"
              onClick={() => setExpandId(expandId === r.id ? null : r.id)}
            >
              <div className={`flex-1 grid gap-4 ${podeVerIdentificacao ? 'grid-cols-5' : 'grid-cols-4'}`}>
                <div>
                  <p className="text-xs text-gray-400 font-medium">Fornecedor</p>
                  <p className="font-semibold text-gray-800 text-sm">{r.fornecedor}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-400 font-medium">NF</p>
                  <p className="text-sm text-gray-700">{r.numeroNf ? `${r.numeroNf}-${r.serieNf}` : '—'}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-400 font-medium">Recebimento</p>
                  <p className="text-sm text-gray-700">
                    {r.dataRecebimento ? new Date(r.dataRecebimento).toLocaleDateString('pt-BR') : '—'}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-gray-400 font-medium">Valor Total</p>
                  <p className="text-sm font-medium text-gray-800 tabular-nums">{brl(r.valorTotal)}</p>
                </div>
                {podeVerIdentificacao && (
                  <div>
                    <p className="text-xs text-gray-400 font-medium">Entrada por</p>
                    <p className="text-sm text-gray-700">{r.usuarioNome ?? '—'}</p>
                  </div>
                )}
              </div>
              <div className="flex items-center gap-2">
                {r.xmlNf ? (
                  <Button variant="ghost" size="sm" className="!bg-info-50 !text-info-700 hover:!bg-info-100"
                    onClick={e => { e.stopPropagation(); setXmlViewId(r.id) }}>
                    <FileText size={12} /> XML
                  </Button>
                ) : (
                  <label className="flex items-center gap-1 px-3 py-1.5 text-xs bg-gray-100 text-gray-600 rounded-lg hover:bg-gray-200 cursor-pointer font-medium">
                    <Upload size={12} /> XML
                    <input
                      type="file" accept=".xml" className="hidden"
                      onChange={e => handleXmlFile(e, r.id)}
                    />
                  </label>
                )}
                {expandId === r.id ? <ChevronUp size={16} className="text-gray-400" /> : <ChevronDown size={16} className="text-gray-400" />}
              </div>
            </div>

            {expandId === r.id && (
              <div className="border-t bg-gray-50 px-5 py-4">
                {r.chaveNf && (
                  <p className="text-xs text-gray-500 mb-3 font-mono break-all">Chave: {r.chaveNf}</p>
                )}
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-xs text-gray-500 uppercase">
                      <th className="text-left pb-2">Produto</th>
                      <th className="text-right pb-2">Quantidade</th>
                      <th className="text-right pb-2">Custo Unit.</th>
                      <th className="text-right pb-2">Custo Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {r.itens.map(it => (
                      <tr key={it.id} className="border-t border-gray-200">
                        <td className="py-2 font-medium">{it.produto.nome}</td>
                        <td className="py-2 text-right tabular-nums">{kg3(it.quantidade)} {it.produto.unidadeMedida}</td>
                        <td className="py-2 text-right tabular-nums">{brl(it.custoUnitario)}</td>
                        <td className="py-2 text-right font-medium tabular-nums">{brl(it.custoTotal)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {r.observacao && (
                  <p className="text-xs text-gray-500 mt-3">Obs: {r.observacao}</p>
                )}
              </div>
            )}
          </div>
        ))}

        {!isLoading && recebimentos.length === 0 && (
          <div className="bg-white rounded-xl shadow border border-gray-100 p-10 text-center text-gray-400">
            Nenhum recebimento encontrado para os filtros selecionados
          </div>
        )}
      </div>

      {/* Modal XML Viewer — visual de "console" propositalmente diferente dos
          demais modais, por ser um leitor de dado bruto (XML da NF-e). */}
      {xmlViewId && rec && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="bg-gray-900 rounded-2xl w-full max-w-4xl h-[80vh] flex flex-col">
            <div className="flex items-center justify-between px-5 py-4 border-b border-gray-700">
              <div>
                <p className="font-semibold text-white">XML da NF</p>
                <p className="text-xs text-gray-400">{rec.fornecedor} — NF {rec.numeroNf || 'S/N'}</p>
              </div>
              <div className="flex items-center gap-3">
                <label className="flex items-center gap-1 px-3 py-1.5 text-xs bg-primary-600 text-white rounded-lg cursor-pointer hover:bg-primary-700 font-medium">
                  <Upload size={12} /> Atualizar XML
                  <input type="file" accept=".xml" className="hidden" onChange={e => handleXmlFile(e, xmlViewId)} />
                </label>
                <button onClick={() => setXmlViewId(null)} className="text-gray-400 hover:text-white">
                  <X size={20} />
                </button>
              </div>
            </div>
            <pre className="flex-1 overflow-auto p-5 text-xs text-success-400 font-mono whitespace-pre-wrap">
              {rec.xmlNf}
            </pre>
          </div>
        </div>
      )}

      {/* Modal Nova Entrada */}
      {showForm && (
        <Modal
          title="Nova Entrada de Mercadoria"
          onClose={resetForm}
          maxWidth="lg"
          footer={
            <>
              <Button variant="secondary" fullWidth onClick={resetForm}>Cancelar</Button>
              <Button variant="primary" fullWidth disabled={!canSave} loading={registrar.isPending} onClick={() => registrar.mutate()}>
                Registrar Entrada
              </Button>
            </>
          }
        >
          <div className="grid grid-cols-2 gap-3">
            <Field label="Fornecedor *" className="col-span-2">
              <input value={fornecedor} onChange={e => setFornecedor(e.target.value)}
                className={baseInputClass} placeholder="Nome do fornecedor" />
            </Field>
            <Field label="Número NF">
              <input value={numeroNf} onChange={e => setNumeroNf(e.target.value)}
                className={baseInputClass} placeholder="000001" />
            </Field>
            <Field label="Série">
              <input value={serieNf} onChange={e => setSerieNf(e.target.value)}
                className={baseInputClass} placeholder="1" />
            </Field>
            <Field label="Chave de Acesso (44 dígitos)" className="col-span-2">
              <input value={chaveNf} onChange={e => setChaveNf(e.target.value)}
                className={`${baseInputClass} font-mono`}
                placeholder="00000000000000000000000000000000000000000000" maxLength={44} />
            </Field>
            <Field label="Data de Emissão">
              <input type="date" value={dataEmissao} onChange={e => setDataEmissao(e.target.value)}
                className={baseInputClass} />
            </Field>
            <Field label="Valor Total NF">
              <CurrencyInput value={valorTotal} onChange={setValorTotal} />
            </Field>
            <Field label="XML da NF" className="col-span-2">
              <div className="flex items-center gap-2">
                <label className="flex items-center gap-2 px-3 py-2 border border-dashed border-gray-300 rounded-lg text-sm text-gray-500 cursor-pointer hover:bg-gray-50 flex-1">
                  <Upload size={14} />
                  {xmlNf ? '✓ XML carregado' : 'Selecionar arquivo .xml'}
                  <input ref={fileRef} type="file" accept=".xml" className="hidden" onChange={e => handleXmlFile(e)} />
                </label>
                {xmlNf && (
                  <button onClick={() => setXmlNf('')} className="text-danger-500 hover:text-danger-600 text-xs font-medium">Remover</button>
                )}
              </div>
            </Field>
            <Field label="Observação" className="col-span-2">
              <input value={observacao} onChange={e => setObservacao(e.target.value)} className={baseInputClass} />
            </Field>
          </div>

          {/* Itens */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Produtos Recebidos *</label>
              <button onClick={addItem} className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 font-medium">
                <Plus size={12} /> Adicionar item
              </button>
            </div>
            <div className="space-y-2">
              {itens.map((item, idx) => {
                const produtoSel = produtos.find(p => p.id === item.produtoId)
                return (
                  <div key={idx} className="grid grid-cols-12 gap-2 items-end bg-gray-50 border border-gray-100 rounded-lg p-3">
                    <div className="col-span-5">
                      {idx === 0 && <label className="text-xs text-gray-500 block mb-1">Produto</label>}
                      <select
                        value={item.produtoId}
                        onChange={e => updateItem(idx, 'produtoId', parseInt(e.target.value))}
                        className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm bg-white"
                      >
                        <option value={0}>Selecione...</option>
                        {produtos.map(p => (
                          <option key={p.id} value={p.id}>{p.nome}</option>
                        ))}
                      </select>
                    </div>
                    <div className="col-span-3">
                      {idx === 0 && <label className="text-xs text-gray-500 block mb-1">Quantidade</label>}
                      <WeightInput
                        value={item.quantidade}
                        onChange={v => updateItem(idx, 'quantidade', v)}
                        unit={(produtoSel?.unidadeMedida ?? 'kg').toLowerCase()}
                        size="sm"
                      />
                    </div>
                    <div className="col-span-3">
                      {idx === 0 && <label className="text-xs text-gray-500 block mb-1">Custo/un</label>}
                      <CurrencyInput
                        value={item.custoUnitario}
                        onChange={v => updateItem(idx, 'custoUnitario', v)}
                        size="sm"
                      />
                    </div>
                    <div className="col-span-1 flex justify-center">
                      {itens.length > 1 && (
                        <button onClick={() => removeItem(idx)} className="text-danger-400 hover:text-danger-600 mb-2">
                          <X size={14} />
                        </button>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
