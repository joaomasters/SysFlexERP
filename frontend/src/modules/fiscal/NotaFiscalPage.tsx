import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { FileText, Plus, X, ChevronDown, ChevronUp, Upload, Download, Printer, CheckCircle, XCircle, Filter } from 'lucide-react'
import { api } from '@/shared/api/axios'
import toast from 'react-hot-toast'
import { formatBRL, formatWeightDisplay } from '@/shared/utils/mask'
import { PageHeader, Card, Modal, Button, StatusBadge, CurrencyInput, WeightInput, Field, baseInputClass, ConfirmDialog } from '@/shared/components/ui'
import type { BadgeTone } from '@/shared/components/ui'
import ImpressaoNFModal from './components/ImpressaoNFModal'
import { usePermissao } from '@/shared/hooks/usePermissao'

interface Produto  { id: number; nome: string; precoVenda: number }
interface Cliente  { id: number; nome: string }
interface NfItem   { produtoId: number | null; descricao: string; quantidade: number; valorUnitario: number }

interface NotaFiscal {
  id: number
  numeroNf: string | null
  serieNf: string
  cliente: { id: number; nome: string } | null
  naturezaOperacao: string
  dataEmissao: string
  valorProdutos: number
  valorDesconto: number
  valorTotal: number
  status: string
  xmlNf: string | null
  usuarioCriacaoNome?: string   // quem lançou a nota — só vem para administradores
  usuarioEmissaoNome?: string   // quem efetivou a saída (EMITIDA) — só vem para administradores
  itens: { id: number; produto: { nome: string } | null; descricao: string; quantidade: number; valorUnitario: number; valorTotal: number }[]
}

const brl = (v?: number) => formatBRL(v ?? 0)
const qtd3 = formatWeightDisplay

// PENDENTE = aguardando emissão (atenção); EMITIDA = concluída; CANCELADA = negativa.
const statusTom: Record<string, BadgeTone> = {
  PENDENTE:  'warning',
  EMITIDA:   'success',
  CANCELADA: 'danger',
}

// Mesmos padrões de filtro da tela de Recebimento de Mercadoria: por padrão,
// últimos 30 dias, pra não carregar o histórico inteiro de notas de uma vez.
const hoje = new Date().toISOString().slice(0, 10)
const trintaDiasAtras = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)

export default function NotaFiscalPage() {
  const qc = useQueryClient()
  const { podeVerIdentificacao } = usePermissao()

  const [showForm, setShowForm]       = useState(false)
  const [expandId, setExpandId]       = useState<number | null>(null)
  const [xmlViewId, setXmlViewId]     = useState<number | null>(null)
  const [confirmandoCancelar, setConfirmandoCancelar] = useState<NotaFiscal | null>(null)
  const [imprimindoNf, setImprimindoNf] = useState<NotaFiscal | null>(null)

  // Filtros da listagem (mesmos da tela de Recebimento: período + busca por nome;
  // aqui o nome é o do cliente, no lugar do fornecedor)
  const [filtroInicio, setFiltroInicio]   = useState(trintaDiasAtras)
  const [filtroFim, setFiltroFim]         = useState(hoje)
  const [filtroCliente, setFiltroCliente] = useState('')

  // Form
  const [clienteId, setClienteId]     = useState('')
  const [numeroNf, setNumeroNf]       = useState('')
  const [serieNf, setSerieNf]         = useState('1')
  const [natureza, setNatureza]       = useState('VENDA DE MERCADORIAS')
  const [observacao, setObservacao]   = useState('')
  const [itens, setItens]             = useState<NfItem[]>([{ produtoId: null, descricao: '', quantidade: 0, valorUnitario: 0 }])

  const { data: produtos = [] } = useQuery<Produto[]>({
    queryKey: ['produtos'],
    queryFn: () => api.get('/estoque/produtos').then(r => r.data),
  })

  const { data: clientes = [] } = useQuery<Cliente[]>({
    queryKey: ['clientes-todos'],
    // NF de Saída pode ser emitida pra qualquer cliente (inclusive varejo),
    // por isso usa /clientes (lista completa) e não /financeiro/clientes
    // (que é filtrado só pra atacado/convênio, usado na tela de Faturamento).
    queryFn: () => api.get('/clientes').then(r => r.data),
  })

  const { data: notas = [], isLoading } = useQuery<NotaFiscal[]>({
    queryKey: ['notas-fiscais', filtroInicio, filtroFim, filtroCliente],
    queryFn: () => api.get('/fiscal/notas', {
      params: { inicio: filtroInicio, fim: filtroFim, cliente: filtroCliente || undefined },
    }).then(r => r.data),
  })

  const criar = useMutation({
    mutationFn: () => api.post('/fiscal/notas', {
      clienteId: clienteId ? parseInt(clienteId) : null,
      numeroNf: numeroNf || null,
      serieNf,
      naturezaOperacao: natureza,
      observacao: observacao || null,
      itens: itens.filter(i => i.quantidade && i.valorUnitario).map(i => ({
        produtoId: i.produtoId,
        descricao: i.descricao || null,
        quantidade: i.quantidade,
        valorUnitario: i.valorUnitario,
      })),
    }),
    onSuccess: () => {
      toast.success('NF criada!')
      qc.invalidateQueries({ queryKey: ['notas-fiscais'] })
      resetForm()
    },
  })

  const atualizarStatus = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      api.put(`/fiscal/notas/${id}/status`, { status }),
    onSuccess: () => {
      toast.success('Status atualizado!')
      qc.invalidateQueries({ queryKey: ['notas-fiscais'] })
      setConfirmandoCancelar(null)
    },
  })

  const uploadXml = useMutation({
    mutationFn: ({ id, xml }: { id: number; xml: string }) =>
      api.put(`/fiscal/notas/${id}/xml`, { xml }),
    onSuccess: () => {
      toast.success('XML vinculado e status atualizado para EMITIDA!')
      qc.invalidateQueries({ queryKey: ['notas-fiscais'] })
    },
  })

  function resetForm() {
    setShowForm(false)
    setClienteId(''); setNumeroNf(''); setSerieNf('1')
    setNatureza('VENDA DE MERCADORIAS'); setObservacao('')
    setItens([{ produtoId: null, descricao: '', quantidade: 0, valorUnitario: 0 }])
  }

  function addItem() {
    setItens(prev => [...prev, { produtoId: null, descricao: '', quantidade: 0, valorUnitario: 0 }])
  }

  function removeItem(idx: number) {
    setItens(prev => prev.filter((_, i) => i !== idx))
  }

  function updateItem(idx: number, field: keyof NfItem, val: string | number | null) {
    setItens(prev => prev.map((item, i) => {
      if (i !== idx) return item
      if (field === 'produtoId' && typeof val === 'number') {
        const p = produtos.find(p => p.id === val)
        return { ...item, produtoId: val, descricao: p?.nome ?? '', valorUnitario: p ? p.precoVenda : 0 }
      }
      return { ...item, [field]: val }
    }))
  }

  // "Gerar o arquivo" da NF emitida: se já tem XML vinculado, baixa ele.
  // Se não tem (foi marcada como Emitida manualmente, sem upload de XML),
  // gera um resumo em texto com os dados da nota — não é um XML de NF-e
  // válido pra SEFAZ, só um arquivo pra guardar/anexar.
  function gerarArquivo(nf: NotaFiscal) {
    let conteudo: string
    let nomeArquivo: string
    let tipo: string

    if (nf.xmlNf) {
      conteudo = nf.xmlNf
      nomeArquivo = `NF_${nf.numeroNf ?? nf.id}.xml`
      tipo = 'application/xml'
    } else {
      const linhas = nf.itens.map(it =>
        `${(it.produto?.nome ?? it.descricao).padEnd(40)} ${qtd3(it.quantidade).padStart(10)}  ${brl(it.valorUnitario).padStart(12)}  ${brl(it.valorTotal).padStart(12)}`
      )
      conteudo = [
        `NOTA FISCAL DE SAÍDA — RESUMO GERENCIAL (não é XML de NF-e válido para SEFAZ)`,
        `NF: ${nf.numeroNf ?? 'S/N'}  Série: ${nf.serieNf}`,
        `Cliente: ${nf.cliente?.nome ?? 'Consumidor final'}`,
        `Natureza: ${nf.naturezaOperacao}`,
        `Emissão: ${new Date(nf.dataEmissao).toLocaleDateString('pt-BR')}`,
        `Status: ${nf.status}`,
        '',
        'ITENS',
        ...linhas,
        '',
        `TOTAL: ${brl(nf.valorTotal)}`,
      ].join('\n')
      nomeArquivo = `NF_${nf.numeroNf ?? nf.id}_resumo.txt`
      tipo = 'text/plain'
    }

    const url = URL.createObjectURL(new Blob([conteudo], { type: tipo }))
    const a = document.createElement('a')
    a.href = url
    a.download = nomeArquivo
    a.click()
    URL.revokeObjectURL(url)
  }

  function handleXmlFile(e: React.ChangeEvent<HTMLInputElement>, id: number) {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = ev => uploadXml.mutate({ id, xml: ev.target?.result as string })
    reader.readAsText(file)
    e.target.value = ''
  }

  const totalForm = itens.reduce((s, i) => s + i.quantidade * i.valorUnitario, 0)
  const canSave = itens.some(i => i.quantidade > 0 && i.valorUnitario > 0)
  const nfXml = notas.find(n => n.id === xmlViewId)

  return (
    <div className="p-6">
      <PageHeader
        title="Notas Fiscais de Saída"
        subtitle="Emissão de Nota fiscal"
        actions={
          <Button variant="primary" onClick={() => setShowForm(true)}>
            <Plus size={16} /> Nova NF
          </Button>
        }
      />

      {/* Filtros */}
      <Card padding="sm" className="mb-4 flex flex-wrap items-end gap-3">
        <Filter size={16} className="text-gray-400 mb-2" />
        <Field label="Emissão de">
          <input type="date" value={filtroInicio} onChange={e => setFiltroInicio(e.target.value)}
            className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm" />
        </Field>
        <Field label="até">
          <input type="date" value={filtroFim} onChange={e => setFiltroFim(e.target.value)}
            className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm" />
        </Field>
        <Field label="Cliente" className="flex-1 min-w-[180px]">
          <input type="text" value={filtroCliente} onChange={e => setFiltroCliente(e.target.value)}
            placeholder="Buscar por nome..."
            className="w-full border border-gray-300 rounded-lg px-3 py-1.5 text-sm" />
        </Field>
        {(filtroCliente || filtroInicio !== trintaDiasAtras || filtroFim !== hoje) && (
          <button
            onClick={() => { setFiltroInicio(trintaDiasAtras); setFiltroFim(hoje); setFiltroCliente('') }}
            className="text-xs text-primary-600 hover:text-primary-700 font-medium pb-2"
          >
            Limpar filtros
          </button>
        )}
      </Card>

      {/* Lista */}
      <div className="space-y-2">
        {isLoading && <p className="text-gray-400 text-sm">Carregando...</p>}
        {notas.map(nf => (
          <div key={nf.id} className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
            <div
              className="flex items-center gap-4 px-5 py-4 cursor-pointer hover:bg-gray-50"
              onClick={() => setExpandId(expandId === nf.id ? null : nf.id)}
            >
              <div className={`flex-1 grid gap-3 ${podeVerIdentificacao ? 'grid-cols-6' : 'grid-cols-5'}`}>
                <div>
                  <p className="text-xs text-gray-400">NF / Série</p>
                  <p className="font-semibold text-sm text-gray-800">{nf.numeroNf ? `${nf.numeroNf}/${nf.serieNf}` : 'S/N'}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-400">Cliente</p>
                  <p className="text-sm text-gray-700">{nf.cliente?.nome ?? '—'}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-400">Emissão</p>
                  <p className="text-sm text-gray-700">{new Date(nf.dataEmissao).toLocaleDateString('pt-BR')}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-400">Total</p>
                  <p className="text-sm font-medium text-gray-800 tabular-nums">{brl(nf.valorTotal)}</p>
                </div>
                <div className="flex items-center">
                  <StatusBadge tone={statusTom[nf.status] ?? 'neutral'}>{nf.status}</StatusBadge>
                </div>
                {podeVerIdentificacao && (
                  <div>
                    <p className="text-xs text-gray-400">Saída por</p>
                    <p className="text-sm text-gray-700">{nf.usuarioEmissaoNome ?? '—'}</p>
                  </div>
                )}
              </div>
              <div className="flex items-center gap-2">
                {nf.xmlNf ? (
                  <Button variant="ghost" size="sm" className="!bg-info-50 !text-info-700 hover:!bg-info-100"
                    onClick={e => { e.stopPropagation(); setXmlViewId(nf.id) }}>
                    <FileText size={12} /> XML
                  </Button>
                ) : (
                  <label
                    onClick={e => e.stopPropagation()}
                    className="flex items-center gap-1 px-3 py-1.5 text-xs bg-gray-100 text-gray-600 rounded-lg hover:bg-gray-200 cursor-pointer font-medium"
                  >
                    <Upload size={12} /> XML
                    <input type="file" accept=".xml" className="hidden" onChange={e => handleXmlFile(e, nf.id)} />
                  </label>
                )}
                {nf.status === 'PENDENTE' && (
                  <Button variant="ghost" size="sm" className="hover:!text-success-600"
                    onClick={e => { e.stopPropagation(); atualizarStatus.mutate({ id: nf.id, status: 'EMITIDA' }) }}
                    title="Marcar como Emitida">
                    <CheckCircle size={16} />
                  </Button>
                )}
                {nf.status === 'EMITIDA' && (
                  <>
                    <Button variant="ghost" size="sm"
                      onClick={e => { e.stopPropagation(); gerarArquivo(nf) }}
                      title="Gerar arquivo da NF">
                      <Download size={16} />
                    </Button>
                    <Button variant="ghost" size="sm"
                      onClick={e => { e.stopPropagation(); setImprimindoNf(nf) }}
                      title="Imprimir NF">
                      <Printer size={16} />
                    </Button>
                  </>
                )}
                {nf.status !== 'CANCELADA' && (
                  <Button variant="ghost" size="sm" className="hover:!text-danger-600"
                    onClick={e => { e.stopPropagation(); setConfirmandoCancelar(nf) }}
                    title="Cancelar NF">
                    <XCircle size={16} />
                  </Button>
                )}
                {expandId === nf.id ? <ChevronUp size={16} className="text-gray-400" /> : <ChevronDown size={16} className="text-gray-400" />}
              </div>
            </div>

            {expandId === nf.id && (
              <div className="border-t bg-gray-50 px-5 py-4">
                <p className="text-xs text-gray-500 mb-1">{nf.naturezaOperacao}</p>
                {podeVerIdentificacao && (
                  <p className="text-xs text-gray-500 mb-3">
                    Lançada por <span className="font-medium">{nf.usuarioCriacaoNome ?? '—'}</span>
                    {' · '}Saída efetivada por <span className="font-medium">{nf.usuarioEmissaoNome ?? '—'}</span>
                  </p>
                )}
                {!podeVerIdentificacao && <div className="mb-2" />}
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-xs text-gray-500 uppercase tracking-wide">
                      <th className="text-left pb-2">Descrição</th>
                      <th className="text-right pb-2">Qtd</th>
                      <th className="text-right pb-2">Unit.</th>
                      <th className="text-right pb-2">Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {nf.itens.map(it => (
                      <tr key={it.id} className="border-t border-gray-200">
                        <td className="py-2">{it.descricao}</td>
                        <td className="py-2 text-right tabular-nums">{qtd3(it.quantidade)}</td>
                        <td className="py-2 text-right tabular-nums">{brl(it.valorUnitario)}</td>
                        <td className="py-2 text-right font-medium tabular-nums">{brl(it.valorTotal)}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr className="border-t-2 border-gray-300 font-semibold">
                      <td colSpan={3} className="pt-2 text-right text-xs uppercase text-gray-500">Total NF</td>
                      <td className="pt-2 text-right tabular-nums">{brl(nf.valorTotal)}</td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            )}
          </div>
        ))}
        {!isLoading && notas.length === 0 && (
          <div className="bg-white rounded-xl shadow border border-gray-100 p-10 text-center text-gray-400">
            Nenhuma nota fiscal encontrada para os filtros selecionados
          </div>
        )}
      </div>

      {/* XML Viewer — tema "console" proposital, ver nota em RecebimentoPage.tsx */}
      {xmlViewId && nfXml && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="bg-gray-900 rounded-2xl w-full max-w-4xl h-[80vh] flex flex-col">
            <div className="flex items-center justify-between px-5 py-4 border-b border-gray-700">
              <div>
                <p className="font-semibold text-white">XML da NF-e</p>
                <p className="text-xs text-gray-400">NF {nfXml.numeroNf ?? 'S/N'} — {nfXml.cliente?.nome ?? 'Sem cliente'}</p>
              </div>
              <div className="flex items-center gap-3">
                <label className="flex items-center gap-1 px-3 py-1.5 text-xs bg-primary-600 text-white rounded-lg cursor-pointer hover:bg-primary-700 font-medium">
                  <Upload size={12} /> Atualizar
                  <input type="file" accept=".xml" className="hidden" onChange={e => handleXmlFile(e, xmlViewId)} />
                </label>
                <button onClick={() => setXmlViewId(null)} className="text-gray-400 hover:text-white">
                  <X size={20} />
                </button>
              </div>
            </div>
            <pre className="flex-1 overflow-auto p-5 text-xs text-success-400 font-mono whitespace-pre-wrap">
              {nfXml.xmlNf}
            </pre>
          </div>
        </div>
      )}

      {/* Modal Nova NF */}
      {showForm && (
        <Modal
          title="Nova Nota Fiscal de Saída"
          onClose={resetForm}
          maxWidth="lg"
          footer={
            <>
              <Button variant="secondary" fullWidth onClick={resetForm}>Cancelar</Button>
              <Button variant="primary" fullWidth disabled={!canSave} loading={criar.isPending} onClick={() => criar.mutate()}>
                Criar NF
              </Button>
            </>
          }
        >
          <div className="grid grid-cols-2 gap-3">
            <Field label="Cliente" className="col-span-2">
              <select value={clienteId} onChange={e => setClienteId(e.target.value)} className={baseInputClass}>
                <option value="">Sem cliente (consumidor final)</option>
                {clientes.map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
              </select>
            </Field>
            <Field label="Número NF">
              <input value={numeroNf} onChange={e => setNumeroNf(e.target.value)} className={baseInputClass} placeholder="000001" />
            </Field>
            <Field label="Série">
              <input value={serieNf} onChange={e => setSerieNf(e.target.value)} className={baseInputClass} placeholder="1" />
            </Field>
            <Field label="Natureza da Operação" className="col-span-2">
              <input value={natureza} onChange={e => setNatureza(e.target.value)} className={baseInputClass} />
            </Field>
            <Field label="Observação" className="col-span-2">
              <input value={observacao} onChange={e => setObservacao(e.target.value)} className={baseInputClass} />
            </Field>
          </div>

          {/* Itens */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Itens</label>
              <button onClick={addItem} className="flex items-center gap-1 text-xs text-primary-600 hover:text-primary-700 font-medium">
                <Plus size={12} /> Adicionar
              </button>
            </div>
            <div className="space-y-2">
              {itens.map((item, idx) => (
                <div key={idx} className="grid grid-cols-12 gap-2 items-end bg-gray-50 border border-gray-100 rounded-lg p-3">
                  <div className="col-span-4">
                    {idx === 0 && <label className="text-xs text-gray-500 block mb-1">Produto</label>}
                    <select
                      value={item.produtoId ?? ''}
                      onChange={e => updateItem(idx, 'produtoId', e.target.value ? parseInt(e.target.value) : null)}
                      className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm bg-white"
                    >
                      <option value="">Outro</option>
                      {produtos.map(p => <option key={p.id} value={p.id}>{p.nome}</option>)}
                    </select>
                  </div>
                  <div className="col-span-3">
                    {idx === 0 && <label className="text-xs text-gray-500 block mb-1">Descrição</label>}
                    <input
                      value={item.descricao}
                      onChange={e => updateItem(idx, 'descricao', e.target.value)}
                      placeholder="Descrição"
                      className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
                    />
                  </div>
                  <div className="col-span-2">
                    {idx === 0 && <label className="text-xs text-gray-500 block mb-1">Qtd</label>}
                    <WeightInput
                      value={item.quantidade}
                      onChange={v => updateItem(idx, 'quantidade', v)}
                      size="sm"
                      unit=""
                    />
                  </div>
                  <div className="col-span-2">
                    {idx === 0 && <label className="text-xs text-gray-500 block mb-1">R$/un</label>}
                    <CurrencyInput
                      value={item.valorUnitario}
                      onChange={v => updateItem(idx, 'valorUnitario', v)}
                      size="sm"
                    />
                  </div>
                  <div className="col-span-1 flex justify-center">
                    {itens.length > 1 && (
                      <button onClick={() => removeItem(idx)} className="text-danger-400 hover:text-danger-600">
                        <X size={14} />
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
            {totalForm > 0 && (
              <p className="text-right text-sm font-semibold text-gray-700 mt-2 tabular-nums">
                Total: {brl(totalForm)}
              </p>
            )}
          </div>
        </Modal>
      )}

      {/* Confirmação de cancelamento */}
      {confirmandoCancelar && (
        <ConfirmDialog
          title="Cancelar nota fiscal?"
          message={
            confirmandoCancelar.xmlNf
              ? `A NF ${confirmandoCancelar.numeroNf ?? 'S/N'} já tem XML vinculado (foi emitida). Cancelar aqui só atualiza o status no sistema — se ela já foi transmitida à SEFAZ, o cancelamento fiscal de verdade precisa ser feito à parte.`
              : `A NF ${confirmandoCancelar.numeroNf ?? 'S/N'} será marcada como cancelada. Essa ação não pode ser desfeita.`
          }
          confirmLabel="Cancelar NF"
          loading={atualizarStatus.isPending}
          onConfirm={() => atualizarStatus.mutate({ id: confirmandoCancelar.id, status: 'CANCELADA' })}
          onCancel={() => setConfirmandoCancelar(null)}
        />
      )}

      {/* Impressão */}
      {imprimindoNf && (
        <ImpressaoNFModal nf={imprimindoNf} onClose={() => setImprimindoNf(null)} />
      )}
    </div>
  )
}