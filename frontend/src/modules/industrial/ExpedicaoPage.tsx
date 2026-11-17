import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Truck, Plus, Trash2, Printer, ScanLine } from 'lucide-react'
import { api } from '@/shared/api/axios'
import {
  PageHeader, Modal, Button, StatusBadge, Field, baseInputClass, BuscaProduto, FilterTabs,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState, Paginacao,
} from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { usePaginacao } from '@/shared/hooks/usePaginacao'
import { hojeISO } from '@/shared/utils/data'
import {
  SITUACAO_LOTE, STATUS_EXPEDICAO,
  type Expedicao, type ExpedicaoResumo, type ItemExpedicao, type LoteProduto, type StatusExpedicao,
} from '@/types/industrial'
import { dataBR, dataHoraBR, num, useProdutosIndustriais } from './comum'

const MODULO = 'IND_EXPEDICAO'
type Filtro = 'ativas' | StatusExpedicao | 'todas'
interface Opcao { id: number; nome: string; documento?: string | null }

const escapar = (s?: string | null) => (s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]!))

/**
 * Expedição: pedido do cliente, reserva de lotes liberados (manual ou FIFO), conferência lendo o código do lote,
 * carregamento (baixa estoque e lotes uma vez só) e NF de saída pendente ligada à expedição.
 */
export default function ExpedicaoPage() {
  const { podeCriar } = usePermissao()
  const [filtro, setFiltro] = useState<Filtro>('ativas')
  const [nova, setNova] = useState(false)
  const [aberta, setAberta] = useState<number | null>(null)
  const status = filtro === 'ativas' || filtro === 'todas' ? undefined : filtro
  const { data = [], isLoading } = useQuery<ExpedicaoResumo[]>({
    queryKey: ['ind-expedicoes', status ?? 'todas'],
    queryFn: () => api.get('/industrial/expedicoes', { params: status ? { status } : {} }).then(r => r.data),
  })
  const lista = data.filter(e => filtro !== 'ativas' || e.status === 'ABERTA' || e.status === 'CONFERIDA')
  const pag = usePaginacao(lista, filtro)

  return (
    <div className="p-4 sm:p-6">
      <PageHeader title="Expedição" icon={<Truck size={22} />} subtitle="Pedidos, reserva de lotes, conferência, carregamento e romaneio" />
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div className="flex flex-wrap gap-2">
          <FilterTabs options={[{ value: 'ativas', label: 'Em andamento' }, { value: 'CARREGADA', label: 'Carregadas' }, { value: 'CANCELADA', label: 'Canceladas' }, { value: 'todas', label: 'Todas' }]}
            value={filtro} onChange={v => setFiltro(v as Filtro)} />
        </div>
        {podeCriar(MODULO) && <Button onClick={() => setNova(true)}>Nova expedição</Button>}
      </div>
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>Expedição</TH><TH>Cliente</TH><TH>Pedido</TH><TH>Previsão</TH><TH>Situação</TH><TH /></tr></THead>
            <TBody>
              {pag.itens.map(e => (
                <TR key={e.id}>
                  <TD className="font-mono font-semibold">{e.numero}</TD>
                  <TD className="font-medium">{e.clienteNome}</TD>
                  <TD className="text-sm text-gray-600">{e.pedidoReferencia ?? '—'}</TD>
                  <TD className="tabular-nums text-sm">{dataBR(e.dataPrevista)}</TD>
                  <TD><StatusBadge tone={STATUS_EXPEDICAO[e.status].tom}>{STATUS_EXPEDICAO[e.status].rotulo}</StatusBadge>{e.notaFiscalId && <span className="ml-2 text-xs text-gray-500">NF #{e.notaFiscalId}</span>}</TD>
                  <TD align="right"><Button size="sm" variant="secondary" onClick={() => setAberta(e.id)}>Abrir</Button></TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && lista.length === 0 && <EmptyState>Nenhuma expedição neste filtro.</EmptyState>}
        {!isLoading && <Paginacao paginacao={pag} rotulo="expedições" />}
      </div>
      {nova && <FormExpedicao onClose={() => setNova(false)} onSalva={id => { setNova(false); setAberta(id) }} />}
      {aberta != null && <ExpedicaoModal key={aberta} id={aberta} onClose={() => setAberta(null)} />}
    </div>
  )
}

interface FormItem { produtoId: number; quantidade: string }

function FormExpedicao({ atual, onClose, onSalva }: { atual?: Expedicao; onClose: () => void; onSalva: (id: number) => void }) {
  const qc = useQueryClient()
  const { data: produtos = [] } = useProdutosIndustriais()
  const { data: clientes = [] } = useQuery<Opcao[]>({ queryKey: ['ind-exp-clientes'], queryFn: () => api.get('/industrial/expedicoes/clientes').then(r => r.data) })
  const [f, setF] = useState({
    clienteId: atual ? String(atual.clienteId) : '', pedidoReferencia: atual?.pedidoReferencia ?? '', dataPrevista: atual?.dataPrevista ?? hojeISO(),
    transportadora: atual?.transportadora ?? '', placa: atual?.placa ?? '', motorista: atual?.motorista ?? '', observacao: atual?.observacao ?? '',
  })
  const [itens, setItens] = useState<FormItem[]>(atual ? atual.itens.map(i => ({ produtoId: i.produtoId, quantidade: String(i.quantidade) })) : [{ produtoId: 0, quantidade: '' }])
  const salvar = useMutation({
    mutationFn: () => {
      const corpo = {
        clienteId: Number(f.clienteId), pedidoReferencia: f.pedidoReferencia || null, dataPrevista: f.dataPrevista,
        transportadora: f.transportadora || null, placa: f.placa || null, motorista: f.motorista || null, observacao: f.observacao || null,
        itens: itens.filter(i => i.produtoId).map(i => ({ produtoId: i.produtoId, quantidade: Number(i.quantidade) })), versao: atual?.versao ?? null,
      }
      return (atual ? api.put(`/industrial/expedicoes/${atual.id}`, corpo) : api.post('/industrial/expedicoes', corpo)).then(r => r.data as Expedicao)
    },
    onSuccess: e => {
      toast.success(atual ? 'Expedição atualizada.' : `${e.numero} criada.`)
      qc.invalidateQueries({ queryKey: ['ind-expedicoes'] })
      qc.setQueryData(['ind-expedicao', e.id], e)
      onSalva(e.id)
    },
  })
  function tentar() {
    if (!f.clienteId) return toast.error('Escolha o cliente.')
    if (!itens.some(i => i.produtoId)) return toast.error('Inclua ao menos um produto.')
    if (itens.some(i => i.produtoId && !(Number(i.quantidade) > 0))) return toast.error('Informe a quantidade de cada produto.')
    salvar.mutate()
  }
  const mudarItem = (i: number, c: Partial<FormItem>) => setItens(l => l.map((x, j) => (j === i ? { ...x, ...c } : x)))

  return (
    <Modal title={atual ? `Editar ${atual.numero}` : 'Nova expedição'} onClose={onClose} maxWidth="xl"
      footer={<>
        <Button variant="secondary" fullWidth onClick={onClose}>Cancelar</Button>
        <Button fullWidth loading={salvar.isPending} onClick={tentar}>Salvar</Button>
      </>}>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div className="sm:col-span-2">
          <Field label="Cliente *" htmlFor="exp-cliente">
            <select id="exp-cliente" value={f.clienteId} onChange={e => setF({ ...f, clienteId: e.target.value })} className={baseInputClass}>
              <option value="">Selecione...</option>
              {clientes.map(c => <option key={c.id} value={c.id}>{c.nome}{c.documento ? ` · ${c.documento}` : ''}</option>)}
            </select>
          </Field>
        </div>
        <Field label="Pedido do cliente" htmlFor="exp-pedido"><input id="exp-pedido" value={f.pedidoReferencia} maxLength={40} onChange={e => setF({ ...f, pedidoReferencia: e.target.value })} className={baseInputClass} /></Field>
        <Field label="Data prevista *" htmlFor="exp-data"><input id="exp-data" type="date" value={f.dataPrevista} onChange={e => setF({ ...f, dataPrevista: e.target.value })} className={baseInputClass} /></Field>
        <Field label="Transportadora" htmlFor="exp-transp"><input id="exp-transp" value={f.transportadora} maxLength={120} onChange={e => setF({ ...f, transportadora: e.target.value })} className={baseInputClass} /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Placa" htmlFor="exp-placa"><input id="exp-placa" value={f.placa} maxLength={10} onChange={e => setF({ ...f, placa: e.target.value })} className={baseInputClass} /></Field>
          <Field label="Motorista" htmlFor="exp-motorista"><input id="exp-motorista" value={f.motorista} maxLength={120} onChange={e => setF({ ...f, motorista: e.target.value })} className={baseInputClass} /></Field>
        </div>
      </div>
      <div>
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-sm font-semibold text-gray-700">Itens</h3>
          <Button size="sm" variant="secondary" onClick={() => setItens([...itens, { produtoId: 0, quantidade: '' }])}><Plus size={14} /> Item</Button>
        </div>
        <div className="space-y-2">
          {itens.map((it, i) => {
            const p = produtos.find(x => x.id === it.produtoId)
            return (
              <div key={i} className="grid grid-cols-12 gap-2 items-center">
                <div className="col-span-12 sm:col-span-7"><BuscaProduto produtos={produtos} value={it.produtoId} onSelect={x => mudarItem(i, { produtoId: x.id })} placeholder={`Produto ${i + 1}...`} /></div>
                <input aria-label={`Quantidade do item ${i + 1}`} type="number" step="any" min="0" value={it.quantidade} placeholder="Qtd."
                  onChange={e => mudarItem(i, { quantidade: e.target.value })} className={`${baseInputClass} col-span-7 sm:col-span-3`} />
                <span className="col-span-3 sm:col-span-1 text-xs text-gray-500">{p?.unidadeMedida ?? ''}</span>
                <button type="button" aria-label={`Remover item ${i + 1}`} className="col-span-2 sm:col-span-1 p-2 text-gray-400 hover:text-danger-600"
                  onClick={() => setItens(itens.length > 1 ? itens.filter((_, j) => j !== i) : [{ produtoId: 0, quantidade: '' }])}><Trash2 size={15} /></button>
              </div>
            )
          })}
        </div>
      </div>
    </Modal>
  )
}

function ExpedicaoModal({ id, onClose }: { id: number; onClose: () => void }) {
  const qc = useQueryClient()
  const { podeCriar, podeEditar, podeExcluir } = usePermissao()
  const [editando, setEditando] = useState(false)
  const [reservando, setReservando] = useState<ItemExpedicao | null>(null)
  const [leitura, setLeitura] = useState('')
  const campoLeitura = useRef<HTMLInputElement>(null)
  const { data: e } = useQuery<Expedicao>({ queryKey: ['ind-expedicao', id], queryFn: () => api.get(`/industrial/expedicoes/${id}`).then(r => r.data) })

  const atualizar = (x: Expedicao) => { qc.setQueryData(['ind-expedicao', id], x); qc.invalidateQueries({ queryKey: ['ind-expedicoes'] }) }
  const acao = useMutation({
    mutationFn: ({ url, corpo, metodo }: { url: string; corpo?: unknown; metodo?: 'delete' }) =>
      (metodo === 'delete' ? api.delete(`/industrial/expedicoes/${id}${url}`) : api.post(`/industrial/expedicoes/${id}${url}`, corpo)).then(r => r.data as Expedicao),
    onSuccess: atualizar,
    onError: () => qc.invalidateQueries({ queryKey: ['ind-expedicao', id] }),
  })
  const conferir = useMutation({
    mutationFn: (codigo: string) => api.post(`/industrial/expedicoes/${id}/conferencia`, { codigoLote: codigo }).then(r => r.data as Expedicao),
    onSuccess: (x, codigo) => { toast.success(`Lote ${codigo.toUpperCase()} conferido.`); atualizar(x); setLeitura(''); campoLeitura.current?.focus() },
    onError: () => { setLeitura(''); campoLeitura.current?.focus() },
  })
  const nota = useMutation({
    mutationFn: () => api.post(`/industrial/expedicoes/${id}/nota-fiscal`).then(r => r.data as { notaFiscalId: number; status: string }),
    onSuccess: n => { toast.success(`NF de saída #${n.notaFiscalId} criada como pendente. Emita pela tela de NF de saída.`); qc.invalidateQueries({ queryKey: ['ind-expedicao', id] }); qc.invalidateQueries({ queryKey: ['ind-expedicoes'] }) },
  })

  if (editando && e) return <FormExpedicao atual={e} onClose={() => setEditando(false)} onSalva={() => setEditando(false)} />

  function romaneio() {
    if (!e) return
    const janela = window.open('', '_blank', 'width=800,height=900')
    if (!janela) return
    const linhas = e.itens.map(i => `<tr><td>${escapar(i.produtoNome)}</td><td>${i.reservas.map(r => `${escapar(r.loteCodigo)} (${num(r.quantidade)})`).join('<br>')}</td>
      <td style="text-align:right">${num(i.quantidade)} ${escapar(i.unidade)}</td><td style="text-align:right">${i.pesoKg != null ? `${num(i.pesoKg, 2)} kg` : '—'}</td></tr>`).join('')
    janela.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>Romaneio ${escapar(e.numero)}</title>
      <style>body{font-family:Arial,sans-serif;margin:12mm;font-size:11pt}table{width:100%;border-collapse:collapse;margin-top:6mm}
      td,th{border:1px solid #444;padding:2mm;vertical-align:top}th{background:#eee;text-align:left}.ass{margin-top:18mm;display:flex;gap:20mm}
      .ass div{flex:1;border-top:1px solid #000;text-align:center;padding-top:1mm;font-size:9pt}</style></head><body>
      <h2>Romaneio de carga ${escapar(e.numero)}</h2>
      <p><b>Cliente:</b> ${escapar(e.clienteNome)}${e.clienteDocumento ? ` · ${escapar(e.clienteDocumento)}` : ''}<br>
      ${e.clienteEndereco ? `<b>Endereço:</b> ${escapar(e.clienteEndereco)}<br>` : ''}
      <b>Pedido:</b> ${escapar(e.pedidoReferencia) || '—'} · <b>Previsão:</b> ${dataBR(e.dataPrevista)}<br>
      <b>Transportadora:</b> ${escapar(e.transportadora) || '—'} · <b>Placa:</b> ${escapar(e.placa) || '—'} · <b>Motorista:</b> ${escapar(e.motorista) || '—'}</p>
      <table><thead><tr><th>Produto</th><th>Lotes</th><th>Quantidade</th><th>Peso</th></tr></thead><tbody>${linhas}</tbody></table>
      <p><b>Peso total:</b> ${num(e.pesoTotalKg, 2)} kg${e.pesoCompleto ? '' : ' (há itens sem peso cadastrado na ficha técnica)'}</p>
      <div class="ass"><div>Conferente</div><div>Motorista</div><div>Recebedor</div></div>
      <script>window.onload=()=>{window.print()}</script></body></html>`)
    janela.document.close()
  }

  return (
    <Modal title={e ? `${e.numero} · ${e.clienteNome}` : 'Expedição'} onClose={onClose} maxWidth="2xl"
      footer={e && <>
        <Button variant="secondary" onClick={onClose}>Fechar</Button>
        {e.status === 'ABERTA' && podeEditar(MODULO) && <Button variant="secondary" onClick={() => setEditando(true)}>Editar</Button>}
        {['ABERTA', 'CONFERIDA'].includes(e.status) && podeExcluir(MODULO) && (
          <Button variant="outline-danger" onClick={() => { const m = window.prompt('Motivo do cancelamento:'); if (m?.trim()) acao.mutate({ url: '/cancelar', corpo: { versao: e.versao, motivo: m.trim() } }) }}>Cancelar expedição</Button>
        )}
        <Button variant="secondary" onClick={romaneio}><Printer size={15} /> Romaneio</Button>
        {e.status === 'ABERTA' && podeEditar(MODULO) && <Button disabled={e.pendencias.length > 0} loading={acao.isPending} onClick={() => acao.mutate({ url: '/concluir-conferencia', corpo: { versao: e.versao } })}>Concluir conferência</Button>}
        {e.status === 'CONFERIDA' && podeEditar(MODULO) && <Button variant="ghost" onClick={() => acao.mutate({ url: '/reabrir', corpo: { versao: e.versao } })}>Reabrir</Button>}
        {e.status === 'CONFERIDA' && podeEditar(MODULO) && (
          <Button variant="success" loading={acao.isPending}
            onClick={() => window.confirm('Carregar? O estoque e o saldo dos lotes são baixados agora.') && acao.mutate({ url: '/carregar', corpo: { versao: e.versao } })}>Carregar</Button>
        )}
        {e.status === 'CARREGADA' && !e.notaFiscalId && podeEditar(MODULO) && <Button loading={nota.isPending} onClick={() => nota.mutate()}>Gerar NF de saída</Button>}
      </>}>
      {!e ? <LoadingState /> : (
        <div className="space-y-4">
          <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm">
            <StatusBadge tone={STATUS_EXPEDICAO[e.status].tom}>{STATUS_EXPEDICAO[e.status].rotulo}</StatusBadge>
            <span>Previsão: <b className="tabular-nums">{dataBR(e.dataPrevista)}</b></span>
            {e.pedidoReferencia && <span>Pedido: <b>{e.pedidoReferencia}</b></span>}
            <span>Peso: <b className="tabular-nums">{num(e.pesoTotalKg, 1)} kg</b>{!e.pesoCompleto && ' (parcial)'}</span>
            {e.carregadaEm && <span>Carregada em {dataHoraBR(e.carregadaEm)}{e.carregadaPorNome ? ` por ${e.carregadaPorNome}` : ''}</span>}
            {e.notaFiscalId && <span>NF de saída #{e.notaFiscalId}</span>}
          </div>
          {e.motivoCancelamento && <p className="text-sm text-gray-600">Cancelada: {e.motivoCancelamento}</p>}

          {e.status === 'ABERTA' && podeEditar(MODULO) && (
            <form className="flex gap-2 items-end" onSubmit={ev => { ev.preventDefault(); if (leitura.trim()) conferir.mutate(leitura.trim()) }}>
              <Field label="Conferência: leia o QR ou digite o lote" htmlFor="exp-leitura">
                <input id="exp-leitura" ref={campoLeitura} value={leitura} onChange={ev => setLeitura(ev.target.value)} autoComplete="off"
                  placeholder="Ex.: OP000001-L01" className={`${baseInputClass} font-mono`} />
              </Field>
              <Button type="submit" loading={conferir.isPending}><ScanLine size={15} /> Conferir</Button>
            </form>
          )}

          {e.itens.map(i => (
            <div key={i.id} className="rounded-lg border border-gray-200">
              <div className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 bg-gray-50 rounded-t-lg">
                <div><span className="font-semibold">{i.produtoNome}</span> <span className="tabular-nums text-sm text-gray-600">· reservado {num(i.reservado)} de {num(i.quantidade)} {i.unidade}</span></div>
                {e.status === 'ABERTA' && podeCriar(MODULO) && i.pendente > 0 && <Button size="sm" variant="secondary" onClick={() => setReservando(i)}>Reservar lote</Button>}
              </div>
              {i.reservas.length === 0 ? <p className="px-3 py-2 text-sm text-gray-500">Nenhum lote reservado.</p> : (
                <Table>
                  <THead><tr><TH>Lote</TH><TH>Situação</TH><TH align="right">Quantidade</TH><TH>Conferência</TH><TH /></tr></THead>
                  <TBody>{i.reservas.map(r => (
                    <TR key={r.id}>
                      <TD className="font-mono">{r.loteCodigo}</TD>
                      <TD><StatusBadge tone={SITUACAO_LOTE[r.loteSituacao].tom}>{SITUACAO_LOTE[r.loteSituacao].rotulo}</StatusBadge></TD>
                      <TD align="right" className="tabular-nums">{num(r.quantidade)}</TD>
                      <TD>{r.conferida ? <StatusBadge tone="success">Conferido</StatusBadge> : <span className="text-sm text-gray-500">Pendente</span>}</TD>
                      <TD align="right">{e.status === 'ABERTA' && podeCriar(MODULO) && (
                        <Button size="sm" variant="ghost" onClick={() => acao.mutate({ url: `/reservas/${r.id}`, metodo: 'delete' })}>Tirar</Button>
                      )}</TD>
                    </TR>
                  ))}</TBody>
                </Table>
              )}
            </div>
          ))}

          {e.status === 'ABERTA' && podeCriar(MODULO) && e.itens.some(i => i.pendente > 0) && (
            <Button variant="secondary" loading={acao.isPending} onClick={() => acao.mutate({ url: '/reservar-automatico' })}>Reservar automático (lotes mais antigos primeiro)</Button>
          )}
          {e.pendencias.length > 0 && (
            <ul className="text-sm text-warning-800 bg-warning-50 border border-warning-200 rounded-lg p-3 list-disc pl-6">{e.pendencias.map(p => <li key={p}>{p}</li>)}</ul>
          )}
        </div>
      )}
      {reservando && e && (
        <ReservaModal item={reservando} onClose={() => setReservando(null)}
          onReservar={(loteId, quantidade) => acao.mutate({ url: '/reservas', corpo: { itemId: reservando.id, loteId, quantidade } }, { onSuccess: () => setReservando(null) })} />
      )}
    </Modal>
  )
}

function ReservaModal({ item, onClose, onReservar }: { item: ItemExpedicao; onClose: () => void; onReservar: (loteId: number, q: number) => void }) {
  const { data: lotes } = useQuery<LoteProduto[]>({
    queryKey: ['ind-lotes-liberados', item.produtoId],
    queryFn: () => api.get('/industrial/lotes', { params: { situacao: 'LIBERADO', produtoId: item.produtoId } }).then(r => r.data),
  })
  const [loteId, setLoteId] = useState(0)
  const [quantidade, setQuantidade] = useState(String(item.pendente))
  const disponiveis = (lotes ?? []).filter(l => l.disponivel > 0).sort((a, b) => a.createdAt.localeCompare(b.createdAt))
  return (
    <Modal title={`Reservar lote · ${item.produtoNome}`} onClose={onClose} maxWidth="lg"
      footer={<>
        <Button variant="secondary" fullWidth onClick={onClose}>Voltar</Button>
        <Button fullWidth disabled={!loteId} onClick={() => onReservar(loteId, Number(quantidade))}>Reservar</Button>
      </>}>
      {!lotes ? <LoadingState /> : disponiveis.length === 0 ? <p className="text-sm text-gray-600">Nenhum lote liberado com saldo para este produto.</p> : (
        <>
          <div className="grid gap-2 max-h-72 overflow-y-auto">
            {disponiveis.map(l => (
              <button key={l.id} type="button" onClick={() => { setLoteId(l.id); setQuantidade(String(Math.min(l.disponivel, item.pendente))) }}
                className={`text-left rounded-lg border-2 p-2 ${loteId === l.id ? 'border-primary-500 bg-primary-50' : 'border-gray-200'}`}>
                <span className="font-mono font-semibold">{l.codigo}</span> <span className="text-sm text-gray-600 tabular-nums">· disponível {num(l.disponivel)} {l.unidade} · {dataBR(l.createdAt)}</span>
              </button>
            ))}
          </div>
          <Field label={`Quantidade (${item.unidade})`} htmlFor="reserva-qtd" hint={`Faltam ${num(item.pendente)} para completar o item`}>
            <input id="reserva-qtd" type="number" step="any" min="0" value={quantidade} onChange={e => setQuantidade(e.target.value)} className={baseInputClass} />
          </Field>
        </>
      )}
    </Modal>
  )
}
