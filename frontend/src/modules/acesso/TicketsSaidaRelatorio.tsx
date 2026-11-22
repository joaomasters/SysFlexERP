import { useState, Fragment } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ChevronDown, ChevronRight, Download } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { formatBRL } from '@/shared/utils/mask'
import { Card, Button, StatusBadge, Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState, Paginacao, CampoBusca, casaBusca } from '@/shared/components/ui'
import type { BadgeTone } from '@/shared/components/ui'
import { usePaginacao } from '@/shared/hooks/usePaginacao'
import PainelRelatorio, { usePeriodo } from '../financeiro/relatorios/PainelRelatorio'
import type { Usuario } from '@/types/acesso'

interface ItemTicket {
  produto: string; unidade: string; quantidade: number; precoUnitario: number; desconto: number; total: number
}
interface Ticket {
  id: number; numeroCupom: string | null; dataVenda: string; status: 'ABERTA' | 'FECHADA' | 'CANCELADA'
  operadorId: number | null; operadorNome: string | null; clienteNome: string | null
  subtotal: number; desconto: number; total: number; formasPagamento: string; observacao: string | null
  itens: ItemTicket[]
}

// FECHADA = concluída; CANCELADA = problema/negativo; ABERTA = ainda em andamento.
const statusConfig: Record<string, { label: string; tone: BadgeTone }> = {
  FECHADA:   { label: 'Fechado',   tone: 'success' },
  CANCELADA: { label: 'Cancelado', tone: 'danger' },
  ABERTA:    { label: 'Em aberto', tone: 'info' },
}

const selectClass = 'border border-gray-300 rounded-lg px-2 py-1.5 text-sm bg-white'
const qtd = (n: number) => n.toLocaleString('pt-BR', { maximumFractionDigits: 3 })
const quando = (iso: string) =>
  new Date(iso).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit' })

/** Aspas e ponto e vírgula: o Excel em português abre o CSV direto. */
const csvCampo = (v: string | number | null | undefined) => `"${String(v ?? '').replace(/"/g, '""')}"`

function baixarCsv(tickets: Ticket[], periodo: string) {
  const linhas = [['Ticket', 'Cupom', 'Data/hora', 'Status', 'Operador', 'Cliente', 'Pagamento', 'Produto', 'Qtd', 'Unid.', 'Preço unit.', 'Desconto item', 'Total item', 'Desconto venda', 'Total ticket', 'Observação']]
  const num = (n: number) => n.toFixed(2).replace('.', ',')
  for (const t of tickets) {
    const base = [t.id, t.numeroCupom ?? '', quando(t.dataVenda), statusConfig[t.status]?.label ?? t.status,
      t.operadorNome ?? '', t.clienteNome ?? '', t.formasPagamento]
    const fim = [num(t.desconto), num(t.total), t.observacao ?? '']
    if (t.itens.length === 0) {
      linhas.push([...base, '', '', '', '', '', '', ...fim] as never)
    } else {
      t.itens.forEach(i => linhas.push([...base, i.produto, qtd(i.quantidade), i.unidade, num(i.precoUnitario), num(i.desconto), num(i.total), ...fim] as never))
    }
  }
  const csv = '﻿' + linhas.map(l => l.map(csvCampo).join(';')).join('\r\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = `tickets-de-saida-${periodo}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

/** Relatório de todos os tickets de saída do PDV, inclusive os cancelados, com os itens de cada um. */
export default function TicketsSaidaRelatorio() {
  const p = usePeriodo()
  const [status, setStatus] = useState('')
  const [operadorId, setOperadorId] = useState('')
  const [busca, setBusca] = useState('')
  const [abertos, setAbertos] = useState<Set<number>>(new Set())

  const { data: usuarios = [] } = useQuery<Usuario[]>({
    queryKey: ['usuarios'],
    queryFn: () => api.get('/usuarios').then(r => r.data),
  })

  const { data: tickets = [], isLoading, isError } = useQuery<Ticket[]>({
    queryKey: ['auditoria-tickets', p.inicio, p.fim, status, operadorId],
    queryFn: () => api.get('/auditoria/tickets', {
      params: { inicio: p.inicio, fim: p.fim, status: status || undefined, operadorId: operadorId || undefined },
    }).then(r => r.data),
    enabled: p.valido,
  })

  const filtrados = tickets.filter(t =>
    casaBusca(busca, t.id, t.numeroCupom, t.operadorNome, t.clienteNome, ...t.itens.map(i => i.produto)))
  const pag = usePaginacao(filtrados, `${p.inicio}|${p.fim}|${status}|${operadorId}|${busca}`)

  const fechados   = filtrados.filter(t => t.status === 'FECHADA')
  const cancelados = filtrados.filter(t => t.status === 'CANCELADA')
  const soma = (l: Ticket[]) => l.reduce((s, t) => s + t.total, 0)

  const alternar = (id: number) =>
    setAbertos(prev => { const n = new Set(prev); n.has(id) ? n.delete(id) : n.add(id); return n })

  const filtros = (
    <>
      {p.filtros}
      <div>
        <label className="text-[11px] text-gray-500 block mb-1">Status</label>
        <select value={status} onChange={e => setStatus(e.target.value)} className={selectClass}>
          <option value="">Todos</option>
          <option value="FECHADA">Fechados</option>
          <option value="CANCELADA">Cancelados</option>
          <option value="ABERTA">Em aberto</option>
        </select>
      </div>
      <div>
        <label className="text-[11px] text-gray-500 block mb-1">Operador</label>
        <select value={operadorId} onChange={e => setOperadorId(e.target.value)} className={`${selectClass} min-w-[150px]`}>
          <option value="">Todos</option>
          {usuarios.map(u => <option key={u.id} value={u.id}>{u.nome}</option>)}
        </select>
      </div>
      <Button variant="secondary" size="sm" disabled={filtrados.length === 0}
        onClick={() => baixarCsv(filtrados, `${p.inicio}_a_${p.fim}`)}>
        <Download size={14} /> Exportar CSV
      </Button>
    </>
  )

  return (
    <PainelRelatorio id="rel-tickets-saida-print" titulo="Tickets de saída" descricaoImpressao={p.descricao} filtros={filtros}>
      <div className="print:hidden"><CampoBusca onBuscar={setBusca} className=""
        placeholder="Buscar por ticket, cupom, operador, cliente ou produto..." ariaLabel="Buscar tickets de saída" /></div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
        <div><p className="text-xs text-gray-500">Tickets</p><p className="text-lg font-bold tabular-nums">{filtrados.length}</p></div>
        <div><p className="text-xs text-gray-500">Fechados</p><p className="text-lg font-bold tabular-nums text-success-700">{fechados.length} · {formatBRL(soma(fechados))}</p></div>
        <div><p className="text-xs text-gray-500">Cancelados</p><p className="text-lg font-bold tabular-nums text-danger-600">{cancelados.length} · {formatBRL(soma(cancelados))}</p></div>
        <div><p className="text-xs text-gray-500">Ticket médio (fechados)</p>
          <p className="text-lg font-bold tabular-nums">{formatBRL(fechados.length ? soma(fechados) / fechados.length : 0)}</p></div>
      </div>

      <Card padding="none" className="overflow-hidden print:border print:shadow-none">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead>
              <tr>
                <TH />
                <TH>Ticket</TH>
                <TH>Data / hora</TH>
                <TH>Operador</TH>
                <TH>Cliente</TH>
                <TH>Pagamento</TH>
                <TH align="right">Total</TH>
                <TH align="center">Status</TH>
              </tr>
            </THead>
            <TBody>
              {pag.itens.map(t => {
                const cfg = statusConfig[t.status] ?? { label: t.status, tone: 'neutral' as BadgeTone }
                const aberto = abertos.has(t.id)
                const cancelado = t.status === 'CANCELADA'
                return (
                  <Fragment key={t.id}>
                    <TR className={cancelado ? 'bg-danger-50/40' : ''}>
                      <TD>
                        <button type="button" onClick={() => alternar(t.id)} className="text-gray-400 hover:text-gray-700 print:hidden"
                          aria-label={aberto ? 'Recolher itens' : 'Ver itens'} aria-expanded={aberto}>
                          {aberto ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                        </button>
                      </TD>
                      <TD className="font-medium tabular-nums">#{t.id}{t.numeroCupom ? <span className="text-xs text-gray-400 ml-1">cupom {t.numeroCupom}</span> : null}</TD>
                      <TD className="text-xs text-gray-500 whitespace-nowrap">{quando(t.dataVenda)}</TD>
                      <TD>{t.operadorNome ?? '—'}</TD>
                      <TD className="text-gray-600">{t.clienteNome ?? '—'}</TD>
                      <TD className="text-xs text-gray-500">{t.formasPagamento || '—'}</TD>
                      <TD align="right" className={`tabular-nums font-medium ${cancelado ? 'line-through text-gray-400' : ''}`}>{formatBRL(t.total)}</TD>
                      <TD align="center"><StatusBadge tone={cfg.tone}>{cfg.label}</StatusBadge></TD>
                    </TR>
                    {(aberto || cancelado) && (
                      <tr className={`${cancelado ? 'bg-danger-50/40' : 'bg-gray-50'} ${aberto ? '' : 'hidden print:table-row'}`}>
                        <td />
                        <td colSpan={7} className="px-4 pb-3 pt-1">
                          {cancelado && t.observacao && <p className="text-xs text-danger-700 mb-1">{t.observacao}</p>}
                          {t.itens.length === 0 ? (
                            <p className="text-xs text-gray-400">Sem itens.</p>
                          ) : (
                            <table className="w-full text-xs">
                              <tbody>
                                {t.itens.map((i, idx) => (
                                  <tr key={idx} className="border-t border-gray-200/70">
                                    <td className="py-1 text-gray-700">{i.produto}</td>
                                    <td className="py-1 text-right tabular-nums text-gray-500">{qtd(i.quantidade)} {i.unidade} × {formatBRL(i.precoUnitario)}</td>
                                    <td className="py-1 text-right tabular-nums text-warning-700">{i.desconto > 0 ? `− ${formatBRL(i.desconto)}` : ''}</td>
                                    <td className="py-1 text-right tabular-nums font-medium text-gray-700">{formatBRL(i.total)}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          )}
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </TBody>
          </Table>
        )}
        {!isLoading && !isError && filtrados.length === 0 && <EmptyState>Nenhum ticket no período.</EmptyState>}
        {isError && <EmptyState>Não foi possível carregar os tickets (o período pode ter no máximo 93 dias).</EmptyState>}
        {!isLoading && <Paginacao paginacao={pag} rotulo="tickets" />}
      </Card>
    </PainelRelatorio>
  )
}
