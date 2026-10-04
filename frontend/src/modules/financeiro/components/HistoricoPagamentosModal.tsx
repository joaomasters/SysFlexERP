import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { X, Printer, Download } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { formatBRL } from '@/shared/utils/mask'
import type { Cliente } from '@/types/venda'
import { Button, StatusBadge, baseInputClass } from '@/shared/components/ui'

export type TipoHistorico = 'receber' | 'pagar'

interface PagamentoHistorico {
  id: number
  data: string
  valor: number
  saldoAnterior: number
  saldoPosterior: number
  usuarioNome: string | null
  origem: 'PAGAMENTO' | 'MIGRACAO' | 'TRANSFERENCIA'
}

interface HistoricoConta {
  contaId: number
  descricao: string | null
  contraparte: string | null
  valor: number
  totalPago: number
  saldo: number
  status: string
  pagamentos: PagamentoHistorico[]
}

interface Props {
  tipo: TipoHistorico
  /** Quando informado, mostra só o histórico dessa conta (sem filtros). */
  contaId?: number
  onClose: () => void
}

const brl = formatBRL
const dataHora = (iso: string) => new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

const TEXTOS = {
  receber: { titulo: 'Histórico de Recebimentos', acao: 'Recebido', usuario: 'Recebido por', total: 'Total recebido', contraparte: 'Cliente', endpoint: '/financeiro/contas-receber/historico' },
  pagar:   { titulo: 'Histórico de Pagamentos',   acao: 'Pago',     usuario: 'Pago por',     total: 'Total pago',     contraparte: 'Fornecedor', endpoint: '/financeiro/contas-pagar/historico' },
} as const

// Origens que não foram um usuário registrando um pagamento na tela
const ROTULO_ORIGEM: Record<string, string> = {
  MIGRACAO: 'Anterior ao histórico',
  TRANSFERENCIA: 'Transferido de contas agrupadas',
}

function quemRegistrou(p: PagamentoHistorico) {
  return p.origem === 'PAGAMENTO' ? (p.usuarioNome ?? '—') : (ROTULO_ORIGEM[p.origem] ?? '—')
}

const csvNum = (v: number) => v.toFixed(2).replace('.', ',')
const csvTxt = (v: string) => `"${v.replace(/"/g, '""')}"`

/**
 * Histórico detalhado de pagamentos parciais (data, valor, usuário, saldo antes e
 * depois), agrupado por conta — e relatório imprimível/exportável desse histórico.
 *
 * Restrito a administradores: quem abre este modal já passou por usePermissao().podeVerIdentificacao,
 * e o backend devolve 403 para quem não for administrador, de qualquer forma.
 *
 * Dois modos:
 *  - com `contaId`: histórico daquela conta (aberto pela linha da tabela);
 *  - sem `contaId`: relatório com filtros (cliente/fornecedor + período).
 *
 * Impressão: técnica de "print isolado" (igual ao RelatorioEstoqueModal) — só o
 * conteúdo de #relatorio-historico-print vai pro papel.
 */
export default function HistoricoPagamentosModal({ tipo, contaId, onClose }: Props) {
  const t = TEXTOS[tipo]
  const [clienteId, setClienteId]   = useState('')
  const [fornecedor, setFornecedor] = useState('')
  const [inicio, setInicio]         = useState('')
  const [fim, setFim]               = useState('')

  const { data: clientes = [] } = useQuery<Cliente[]>({
    queryKey: ['clientes-faturaveis'],
    queryFn: () => api.get('/clientes/faturaveis').then(r => r.data),
    enabled: tipo === 'receber' && !contaId,
  })

  const { data: contas = [], isLoading } = useQuery<HistoricoConta[]>({
    queryKey: ['historico-pagamentos', tipo, contaId, clienteId, fornecedor, inicio, fim],
    queryFn: () => api.get(t.endpoint, {
      params: {
        contaId: contaId || undefined,
        clienteId: tipo === 'receber' ? (clienteId || undefined) : undefined,
        fornecedor: tipo === 'pagar' ? (fornecedor || undefined) : undefined,
        inicio: inicio || undefined,
        fim: fim || undefined,
      },
    }).then(r => r.data),
  })

  const totalListado = contas.reduce((s, c) => s + c.pagamentos.reduce((a, p) => a + p.valor, 0), 0)

  function exportarCsv() {
    const linhas: string[] = [
      ['Conta', t.contraparte, 'Descrição', 'Valor da conta', 'Data', `Valor ${t.acao.toLowerCase()}`, 'Saldo anterior', 'Saldo após', t.usuario].join(';'),
    ]
    contas.forEach(c => c.pagamentos.forEach(p => linhas.push([
      c.contaId, csvTxt(c.contraparte ?? ''), csvTxt(c.descricao ?? ''), csvNum(c.valor),
      csvTxt(dataHora(p.data)), csvNum(p.valor), csvNum(p.saldoAnterior), csvNum(p.saldoPosterior),
      csvTxt(quemRegistrou(p)),
    ].join(';'))))
    // BOM + ';' para o Excel em pt-BR abrir com acentos e colunas corretos
    const blob = new Blob(['\uFEFF' + linhas.join('\r\n')], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `historico-${tipo === 'receber' ? 'recebimentos' : 'pagamentos'}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4 print:static print:bg-white print:p-0">
      <style>{`
        @media print {
          body * { visibility: hidden; }
          #relatorio-historico-print, #relatorio-historico-print * { visibility: visible; }
          #relatorio-historico-print { position: absolute; top: 0; left: 0; width: 100%; }
        }
      `}</style>

      <div className="bg-white rounded-2xl w-full max-w-4xl h-[85vh] flex flex-col shadow-xl print:h-auto print:shadow-none print:rounded-none print:max-w-full">
        {/* Cabeçalho — some na impressão */}
        <div className="flex items-center justify-between px-6 py-4 border-b print:hidden">
          <div>
            <h2 className="font-bold text-gray-900">{t.titulo}{contaId ? ` — conta #${contaId}` : ''}</h2>
            <p className="text-xs text-gray-400">Visível somente para administradores</p>
          </div>
          <button onClick={onClose} aria-label="Fechar">
            <X size={20} className="text-gray-400 hover:text-gray-600" />
          </button>
        </div>

        {/* Filtros (só no modo relatório) + ações — somem na impressão */}
        <div className="flex flex-wrap items-end gap-3 px-6 py-3 border-b bg-gray-50 print:hidden">
          {!contaId && (
            <>
              {tipo === 'receber' ? (
                <div className="min-w-[200px]">
                  <label className="text-[11px] text-gray-500 block mb-1">{t.contraparte}</label>
                  <select value={clienteId} onChange={e => setClienteId(e.target.value)}
                    className="w-full border border-gray-300 rounded-lg px-2 py-1.5 text-sm bg-white">
                    <option value="">Todos</option>
                    {clientes.map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
                  </select>
                </div>
              ) : (
                <div className="min-w-[200px]">
                  <label className="text-[11px] text-gray-500 block mb-1">{t.contraparte}</label>
                  <input type="text" value={fornecedor} onChange={e => setFornecedor(e.target.value)}
                    placeholder="Buscar por nome..." className={`${baseInputClass} py-1.5 text-sm`} />
                </div>
              )}
              <div>
                <label className="text-[11px] text-gray-500 block mb-1">De</label>
                <input type="date" value={inicio} onChange={e => setInicio(e.target.value)}
                  className="border border-gray-300 rounded-lg px-2 py-1.5 text-sm" />
              </div>
              <div>
                <label className="text-[11px] text-gray-500 block mb-1">Até</label>
                <input type="date" value={fim} onChange={e => setFim(e.target.value)}
                  className="border border-gray-300 rounded-lg px-2 py-1.5 text-sm" />
              </div>
            </>
          )}
          <div className="ml-auto flex gap-2">
            <Button variant="secondary" size="sm" onClick={exportarCsv} disabled={contas.length === 0}>
              <Download size={14} /> Exportar CSV
            </Button>
            <Button variant="primary" size="sm" onClick={() => window.print()} disabled={contas.length === 0}>
              <Printer size={14} /> Imprimir relatório
            </Button>
          </div>
        </div>

        {/* Área impressa */}
        <div id="relatorio-historico-print" className="flex-1 overflow-y-auto p-6 space-y-6 print:overflow-visible print:p-8">
          <div className="hidden print:block">
            <h1 className="text-xl font-bold text-gray-900">{t.titulo}</h1>
            <p className="text-xs text-gray-500">
              Gerado em {new Date().toLocaleString('pt-BR')}
              {(inicio || fim) && ` — período: ${inicio || 'início'} a ${fim || 'hoje'}`}
            </p>
          </div>

          {isLoading && <p className="text-sm text-gray-400">Carregando...</p>}

          {contas.map(c => (
            <section key={c.contaId} className="break-inside-avoid">
              <div className="flex flex-wrap items-center gap-x-6 gap-y-1 mb-2">
                <p className="font-semibold text-gray-800">
                  {c.contraparte ?? '—'} <span className="font-normal text-gray-500">— {c.descricao ?? `Conta #${c.contaId}`}</span>
                </p>
                <StatusBadge tone={c.status === 'PAGO' ? 'success' : c.status === 'CANCELADO' ? 'neutral' : 'warning'}>{c.status}</StatusBadge>
              </div>
              <div className="grid grid-cols-3 gap-3 text-sm mb-2">
                <div><span className="text-xs text-gray-400 block">Valor da conta</span><span className="tabular-nums font-medium">{brl(c.valor)}</span></div>
                <div><span className="text-xs text-gray-400 block">{t.total}</span><span className="tabular-nums font-medium text-success-600">{brl(c.totalPago)}</span></div>
                <div><span className="text-xs text-gray-400 block">Saldo</span><span className="tabular-nums font-bold text-danger-600">{brl(c.saldo)}</span></div>
              </div>

              <table className="w-full text-sm">
                <thead>
                  <tr className="text-xs text-gray-500 uppercase tracking-wide border-b-2 border-gray-300">
                    <th className="text-left py-2 pr-3">#</th>
                    <th className="text-left py-2 pr-3">Data</th>
                    <th className="text-right py-2 pr-3">{t.acao}</th>
                    <th className="text-right py-2 pr-3">Saldo anterior</th>
                    <th className="text-right py-2 pr-3">Saldo após</th>
                    <th className="text-left py-2">{t.usuario}</th>
                  </tr>
                </thead>
                <tbody>
                  {c.pagamentos.map((p, i) => (
                    <tr key={p.id} className="border-b border-gray-100">
                      <td className="py-2 pr-3 text-gray-400">{i + 1}º</td>
                      <td className="py-2 pr-3 text-gray-600">{dataHora(p.data)}</td>
                      <td className="py-2 pr-3 text-right tabular-nums font-medium">{brl(p.valor)}</td>
                      <td className="py-2 pr-3 text-right tabular-nums text-gray-500">{brl(p.saldoAnterior)}</td>
                      <td className="py-2 pr-3 text-right tabular-nums text-gray-700">{brl(p.saldoPosterior)}</td>
                      <td className={`py-2 ${p.origem === 'PAGAMENTO' ? 'text-gray-700' : 'text-gray-400 italic'}`}>{quemRegistrou(p)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          ))}

          {!isLoading && contas.length === 0 && (
            <p className="text-center text-gray-400 text-sm py-8">Nenhum pagamento registrado para os filtros selecionados.</p>
          )}

          {contas.length > 0 && (
            <p className="text-right text-sm text-gray-600 border-t pt-3">
              {t.total} nas linhas listadas: <span className="font-bold tabular-nums">{brl(totalListado)}</span>
            </p>
          )}
        </div>
      </div>
    </div>
  )
}
