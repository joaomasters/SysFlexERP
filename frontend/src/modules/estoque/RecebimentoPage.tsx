import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, FileText, X, ChevronDown, ChevronUp, Upload, Filter, Ban, AlertTriangle } from 'lucide-react'
import { api } from '@/shared/api/axios'
import toast from 'react-hot-toast'
import { formatBRL, formatQuantidade, casasDaUnidade } from '@/shared/utils/mask'
import { PageHeader, Card, Modal, Button, CurrencyInput, WeightInput, Field, baseInputClass } from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { limiteValidadeISO, validadeAcimaDoLimite, formatarDataValidade, MAX_ANOS_VALIDADE } from './validadeUtils'

interface Produto { id: number; nome: string; unidadeMedida: string }
interface RecItem { produtoId: number; quantidade: number; custoUnitario: number; dataValidade: string }

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
  canceladoEm?: string | null
  motivoCancelamento?: string | null
  /** Até quando pode ser cancelada (vem do servidor; ausente se já cancelada) */
  cancelavelAte?: string | null
  itens: { id: number; produto: { id: number; nome: string; unidadeMedida: string }; quantidade: number; custoUnitario: number; custoTotal: number; dataValidade?: string | null }[]
}

const brl = (v?: number) => formatBRL(v ?? 0)

const hoje = new Date().toISOString().slice(0, 10)
const trintaDiasAtras = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)

export default function RecebimentoPage() {
  const qc = useQueryClient()
  const fileRef = useRef<HTMLInputElement>(null)
  const { podeVerIdentificacao, podeExcluir } = usePermissao()
  const [cancelando, setCancelando] = useState<Recebimento | null>(null)

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
  const [itens, setItens]             = useState<RecItem[]>([{ produtoId: 0, quantidade: 0, custoUnitario: 0, dataValidade: '' }])
  // Só mostra os erros depois da primeira tentativa de registrar — evita o
  // formulário abrir já todo vermelho.
  const [tentouSalvar, setTentouSalvar] = useState(false)

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
        dataValidade: i.dataValidade || null,
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

  // Cancelamento (até 7 dias): estorna o estoque. Opcionalmente reabre o formulário
  // com os mesmos itens para lançar de novo já com a validade.
  function relancar(r: Recebimento) {
    setFornecedor(r.fornecedor ?? ''); setNumeroNf(r.numeroNf ?? ''); setSerieNf(r.serieNf ?? '1')
    setChaveNf(r.chaveNf ?? ''); setDataEmissao(r.dataEmissao ?? ''); setValorTotal(r.valorTotal ?? 0)
    setObservacao(r.observacao ?? ''); setXmlNf(r.xmlNf ?? '')
    setTentouSalvar(false)
    setItens(r.itens.map(it => ({
      produtoId: it.produto.id, quantidade: it.quantidade, custoUnitario: it.custoUnitario,
      dataValidade: it.dataValidade ?? '',
    })))
    setShowForm(true)
  }

  function resetForm() {
    setShowForm(false)
    setFornecedor(''); setNumeroNf(''); setSerieNf('1')
    setChaveNf(''); setDataEmissao(''); setValorTotal(0)
    setObservacao(''); setXmlNf('')
    setTentouSalvar(false)
    setItens([{ produtoId: 0, quantidade: 0, custoUnitario: 0, dataValidade: '' }])
  }

  function addItem() {
    setItens(prev => [...prev, { produtoId: 0, quantidade: 0, custoUnitario: 0, dataValidade: '' }])
  }

  function removeItem(idx: number) {
    setItens(prev => prev.filter((_, i) => i !== idx))
  }

  function updateItem(idx: number, field: keyof RecItem, val: number | string) {
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

  // Todos os campos da entrada são obrigatórios.
  const erros = {
    fornecedor:  fornecedor.trim() ? '' : 'Informe o fornecedor.',
    numeroNf:    numeroNf.trim() ? '' : 'Informe o número da NF.',
    serieNf:     serieNf.trim() ? '' : 'Informe a série.',
    chaveNf:     /^\d{44}$/.test(chaveNf) ? '' : `A chave deve ter 44 dígitos (${chaveNf.length}/44).`,
    dataEmissao: dataEmissao ? '' : 'Informe a data de emissão.',
    valorTotal:  valorTotal > 0 ? '' : 'Informe o valor total da NF.',
  }
  // Validade não pode passar de 3 anos da data de entrada (hoje): barra datas digitadas por engano.
  const limiteValidade = limiteValidadeISO()
  const errosItens = itens.map(i => ({
    produto:    i.produtoId <= 0,
    quantidade: i.quantidade <= 0,
    custo:      i.custoUnitario <= 0,
    validade:   !i.dataValidade,
    validadeAlta: validadeAcimaDoLimite(i.dataValidade, limiteValidade),
  }))
  const itensInvalidos = errosItens.some(e => e.produto || e.quantidade || e.custo || e.validade || e.validadeAlta)
  const validadeAlta = errosItens.some(e => e.validadeAlta)
  const formInvalido = Object.values(erros).some(Boolean) || itensInvalidos

  const mostrar = (msg: string) => (tentouSalvar && msg ? msg : undefined)
  const campoErro = (msg: string) => (tentouSalvar && msg ? '!border-danger-500' : '')

  function tentarRegistrar() {
    setTentouSalvar(true)
    if (formInvalido) {
      toast.error('Preencha todos os campos obrigatórios.')
      return
    }
    registrar.mutate()
  }

  const rec = recebimentos.find(r => r.id === xmlViewId)

  return (
    <div className="p-6">
      <PageHeader
        title="Recebimento de Mercadoria"
        subtitle="Entrada de Nota fiscal"
        actions={
          <Button variant="primary" onClick={() => setShowForm(true)}>
            Nova Entrada
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
          <div key={r.id} className={`bg-white rounded-xl shadow border border-gray-100 overflow-hidden ${r.status === 'CANCELADO' ? 'opacity-70' : ''}`}>
            <div
              className="flex items-center gap-4 px-5 py-4 cursor-pointer hover:bg-gray-50"
              onClick={() => setExpandId(expandId === r.id ? null : r.id)}
            >
              <div className={`flex-1 grid gap-4 ${podeVerIdentificacao ? 'grid-cols-5' : 'grid-cols-4'}`}>
                <div>
                  <p className="text-xs text-gray-400 font-medium">Fornecedor</p>
                  <p className="font-semibold text-gray-800 text-sm flex items-center gap-2">
                    {r.fornecedor}
                    {r.status === 'CANCELADO' && (
                      <span className="text-[10px] font-semibold uppercase bg-red-100 text-red-700 px-1.5 py-0.5 rounded">Cancelada</span>
                    )}
                  </p>
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
                      <th className="text-right pb-2">Validade</th>
                    </tr>
                  </thead>
                  <tbody>
                    {r.itens.map(it => (
                      <tr key={it.id} className="border-t border-gray-200">
                        <td className="py-2 font-medium">{it.produto.nome}</td>
                        <td className="py-2 text-right tabular-nums">{formatQuantidade(it.quantidade, it.produto.unidadeMedida)} {it.produto.unidadeMedida}</td>
                        <td className="py-2 text-right tabular-nums">{brl(it.custoUnitario)}</td>
                        <td className="py-2 text-right font-medium tabular-nums">{brl(it.custoTotal)}</td>
                        <td className="py-2 text-right tabular-nums text-gray-600">
                          {it.dataValidade
                            ? new Date(it.dataValidade + 'T00:00:00').toLocaleDateString('pt-BR')
                            : <span className="text-amber-600 font-medium">sem validade</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {r.observacao && (
                  <p className="text-xs text-gray-500 mt-3">Obs: {r.observacao}</p>
                )}
                {r.status === 'CANCELADO' ? (
                  <div className="mt-3 text-xs bg-red-50 border border-red-100 text-red-700 rounded-lg px-3 py-2">
                    Entrada cancelada em {r.canceladoEm ? new Date(r.canceladoEm).toLocaleString('pt-BR') : '—'} — estoque estornado.
                    {r.motivoCancelamento && <> Motivo: <span className="font-medium">{r.motivoCancelamento}</span></>}
                  </div>
                ) : r.cancelavelAte && podeExcluir('RECEBIMENTO') && new Date(r.cancelavelAte) > new Date() ? (
                  <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
                    <p className="text-xs text-gray-500">
                      Pode ser cancelada até {new Date(r.cancelavelAte).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })}
                      {' '}({Math.max(0, Math.ceil((new Date(r.cancelavelAte).getTime() - Date.now()) / 86_400_000))} dia(s) restante(s)).
                    </p>
                    <Button variant="outline-danger" size="sm" onClick={() => setCancelando(r)}>
                      <Ban size={13} /> Cancelar entrada
                    </Button>
                  </div>
                ) : null}
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

      {cancelando && (
        <CancelarEntradaModal
          recebimento={cancelando}
          onClose={() => setCancelando(null)}
          onCancelada={(relancarDepois) => {
            const r = cancelando
            setCancelando(null)
            qc.invalidateQueries({ queryKey: ['recebimentos'] })
            qc.invalidateQueries({ queryKey: ['produtos'] })
            if (relancarDepois) relancar(r)
          }}
        />
      )}

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
          maxWidth="2xl"
          footer={
            <>
              <Button variant="secondary" fullWidth onClick={resetForm}>Cancelar</Button>
              <Button variant="primary" fullWidth loading={registrar.isPending} onClick={tentarRegistrar}>
                Registrar Entrada
              </Button>
            </>
          }
        >
          <div className="grid grid-cols-2 gap-3">
            <Field label="Fornecedor *" className="col-span-2" error={mostrar(erros.fornecedor)}>
              <input value={fornecedor} onChange={e => setFornecedor(e.target.value)}
                className={`${baseInputClass} ${campoErro(erros.fornecedor)}`} placeholder="Nome do fornecedor" />
            </Field>
            <Field label="Número NF *" error={mostrar(erros.numeroNf)}>
              <input value={numeroNf} onChange={e => setNumeroNf(e.target.value)}
                className={`${baseInputClass} ${campoErro(erros.numeroNf)}`} placeholder="000001" />
            </Field>
            <Field label="Série *" error={mostrar(erros.serieNf)}>
              <input value={serieNf} onChange={e => setSerieNf(e.target.value)}
                className={`${baseInputClass} ${campoErro(erros.serieNf)}`} placeholder="1" />
            </Field>
            <Field label="Chave de Acesso (44 dígitos) *" className="col-span-2" error={mostrar(erros.chaveNf)}>
              <input value={chaveNf} onChange={e => setChaveNf(e.target.value.replace(/\D/g, ''))}
                inputMode="numeric"
                className={`${baseInputClass} font-mono ${campoErro(erros.chaveNf)}`}
                placeholder="00000000000000000000000000000000000000000000" maxLength={44} />
            </Field>
            <Field label="Data de Emissão *" error={mostrar(erros.dataEmissao)}>
              <input type="date" value={dataEmissao} onChange={e => setDataEmissao(e.target.value)}
                className={`${baseInputClass} ${campoErro(erros.dataEmissao)}`} />
            </Field>
            <Field label="Valor Total NF *" error={mostrar(erros.valorTotal)}>
              <CurrencyInput value={valorTotal} onChange={setValorTotal}
                className={tentouSalvar && erros.valorTotal ? '[&_input]:!border-danger-500' : ''} />
            </Field>
            <Field label="XML da NF (opcional)" className="col-span-2">
              <div className="flex items-center gap-2">
                <label className={`flex items-center gap-2 px-3 py-2 border border-dashed rounded-lg text-sm text-gray-500 cursor-pointer hover:bg-gray-50 flex-1 border-gray-300`}>
                  <Upload size={14} />
                  {xmlNf ? '✓ XML carregado' : 'Selecionar arquivo .xml'}
                  <input ref={fileRef} type="file" accept=".xml" className="hidden" onChange={e => handleXmlFile(e)} />
                </label>
                {xmlNf && (
                  <button onClick={() => setXmlNf('')} className="text-danger-500 hover:text-danger-600 text-xs font-medium">Remover</button>
                )}
              </div>
            </Field>
            <Field label="Observação (opcional)" className="col-span-2">
              <input value={observacao} onChange={e => setObservacao(e.target.value)}
                className={baseInputClass} />
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
                const ei = errosItens[idx]
                const bad = (v: boolean) => (tentouSalvar && v ? '!border-danger-500' : '')
                return (
                  <div key={idx} className="grid grid-cols-6 sm:grid-cols-12 gap-2 items-end bg-gray-50 border border-gray-100 rounded-lg p-3">
                    <div className="col-span-6 sm:col-span-3">
                      <label className={`text-xs text-gray-500 block mb-1 ${idx === 0 ? '' : 'sm:hidden'}`}>Produto *</label>
                      <select
                        value={item.produtoId}
                        onChange={e => updateItem(idx, 'produtoId', parseInt(e.target.value))}
                        className={`w-full border border-gray-300 rounded px-2 py-1.5 text-sm bg-white ${bad(ei.produto)}`}
                      >
                        <option value={0}>Selecione...</option>
                        {produtos.map(p => (
                          <option key={p.id} value={p.id}>{p.nome}</option>
                        ))}
                      </select>
                    </div>
                    <div className="col-span-3 sm:col-span-3">
                      <label className={`text-xs text-gray-500 block mb-1 ${idx === 0 ? '' : 'sm:hidden'}`}>Quantidade *</label>
                      <WeightInput
                        value={item.quantidade}
                        onChange={v => updateItem(idx, 'quantidade', v)}
                        unit={(produtoSel?.unidadeMedida ?? 'kg').toLowerCase()}
                        decimals={casasDaUnidade(produtoSel?.unidadeMedida)}
                        size="sm"
                        className={tentouSalvar && ei.quantidade ? '[&_input]:!border-danger-500' : ''}
                      />
                    </div>
                    <div className="col-span-3 sm:col-span-2">
                      <label className={`text-xs text-gray-500 block mb-1 ${idx === 0 ? '' : 'sm:hidden'}`}>Custo/un *</label>
                      <CurrencyInput
                        value={item.custoUnitario}
                        onChange={v => updateItem(idx, 'custoUnitario', v)}
                        size="sm"
                        className={tentouSalvar && ei.custo ? '[&_input]:!border-danger-500' : ''}
                      />
                    </div>
                    <div className="col-span-5 sm:col-span-3">
                      <label className={`text-xs text-gray-500 block mb-1 ${idx === 0 ? '' : 'sm:hidden'}`}>Validade *</label>
                      <input
                        type="date"
                        value={item.dataValidade}
                        onChange={e => updateItem(idx, 'dataValidade', e.target.value)}
                        title="Validade do lote recebido"
                        max={limiteValidade}
                        className={`w-full min-w-[9rem] border border-gray-300 rounded px-2 py-1.5 text-sm bg-white ${ei.validadeAlta ? '!border-danger-500' : bad(ei.validade)}`}
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
            {tentouSalvar && itensInvalidos && (
              <p className="text-danger-600 text-xs mt-2">
                Preencha produto, quantidade, custo e validade em todos os itens (ou remova os itens em branco).
              </p>
            )}
            {validadeAlta && (
              <p className="text-danger-600 text-xs mt-1">
                A validade não pode passar de {MAX_ANOS_VALIDADE} anos da data de entrada
                (limite: {formatarDataValidade(limiteValidade)}). Confira o ano digitado.
              </p>
            )}
          </div>
        </Modal>
      )}
    </div>
  )
}
// ─── Cancelar entrada ───────────────────────────────────────────────────────

function CancelarEntradaModal({ recebimento: r, onClose, onCancelada }: {
  recebimento: Recebimento
  onClose: () => void
  onCancelada: (relancar: boolean) => void
}) {
  const semValidade = r.itens.some(it => !it.dataValidade)
  const [motivo, setMotivo] = useState(semValidade ? 'Entrada lançada sem data de validade' : '')
  const [relancar, setRelancar] = useState(true)

  const cancelar = useMutation({
    mutationFn: () => api.post(`/estoque/recebimentos/${r.id}/cancelar`, { motivo: motivo.trim() }),
    onSuccess: () => {
      toast.success(relancar ? 'Entrada cancelada. Complete a nova entrada e registre.' : 'Entrada cancelada e estoque estornado.')
      onCancelada(relancar)
    },
  })

  return (
    <Modal
      title="Cancelar entrada de mercadoria"
      maxWidth="md"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" fullWidth onClick={onClose}>Voltar</Button>
          <Button variant="danger" fullWidth disabled={motivo.trim().length < 5} loading={cancelar.isPending}
                  onClick={() => cancelar.mutate()}>
            Cancelar entrada
          </Button>
        </>
      }
    >
      <div className="bg-red-50 border border-red-100 rounded-lg p-3 flex gap-2 text-sm text-red-700">
        <AlertTriangle size={16} className="shrink-0 mt-0.5" />
        <p>
          A quantidade de <strong>{r.itens.length} item(ns)</strong> de <strong>{r.fornecedor}</strong>
          {r.numeroNf ? ` (NF ${r.numeroNf})` : ''} sai do estoque. A entrada fica registrada como cancelada.
        </p>
      </div>
      <Field label="Motivo *">
        <textarea rows={2} value={motivo} onChange={e => setMotivo(e.target.value)} className={baseInputClass} />
      </Field>
      <label className="flex items-start gap-2 text-sm text-gray-700">
        <input type="checkbox" checked={relancar} onChange={e => setRelancar(e.target.checked)} className="mt-1" />
        <span>Abrir uma nova entrada com os mesmos itens para corrigir (ex.: preencher a validade)</span>
      </label>
    </Modal>
  )
}