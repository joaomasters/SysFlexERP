import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { CheckCircle, Layers, History, FileText } from 'lucide-react'
import { api } from '@/shared/api/axios'
import toast from 'react-hot-toast'
import type { ContasAReceber, Cliente } from '@/types/venda'
import { formatBRL } from '@/shared/utils/mask'
import {
  PageHeader, Card, FilterTabs, Modal, Button, StatusBadge, CurrencyInput,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState,
} from '@/shared/components/ui'
import type { BadgeTone } from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import HistoricoPagamentosModal from './components/HistoricoPagamentosModal'

const brl = formatBRL

// ABERTO/PARCIAL = dívida ainda pendente (atenção); PAGO = concluído;
// CANCELADO = neutro; AGRUPADO = absorvido por um fechamento (informativo).
const statusTom: Record<string, BadgeTone> = {
  ABERTO:    'warning',
  PARCIAL:   'warning',
  PAGO:      'success',
  CANCELADO: 'neutral',
  AGRUPADO:  'info',
}

export default function ContasReceberPage() {
  const qc = useQueryClient()
  const { podeVerIdentificacao } = usePermissao()
  const [searchParams] = useSearchParams()
  const [clienteId, setClienteId] = useState(searchParams.get('clienteId') ?? '')
  const [statusFiltro, setStatusFiltro] = useState('ABERTO')
  const [pagarId, setPagarId]     = useState<number | null>(null)
  const [valorPag, setValorPag]   = useState('')
  // Histórico de recebimentos (somente administradores): de uma conta, ou o relatório geral
  const [historicoContaId, setHistoricoContaId] = useState<number | null>(null)
  const [mostrarRelatorio, setMostrarRelatorio] = useState(false)

  // Seletor de cliente — antes era um campo numérico livre pro ID, agora
  // busca da tela de Clientes (só quem pode ser faturado/fiado).
  const { data: clientes = [] } = useQuery<Cliente[]>({
    queryKey: ['clientes-faturaveis'],
    queryFn: () => api.get('/clientes/faturaveis').then(r => r.data),
  })

  const { data: contas = [], isLoading } = useQuery<ContasAReceber[]>({
    queryKey: ['contas-receber', statusFiltro],
    queryFn: () =>
      api.get('/financeiro/contas-receber', { params: { status: statusFiltro } }).then(r => r.data),
  })

  const pagar = useMutation({
    mutationFn: ({ contaId, valor }: { contaId: number; valor: number }) =>
      api.post(`/financeiro/contas-receber/${contaId}/pagar`, null, { params: { valor } }),
    onSuccess: () => {
      toast.success('Pagamento registrado!')
      qc.invalidateQueries({ queryKey: ['contas-receber'] })
      qc.invalidateQueries({ queryKey: ['historico-pagamentos'] })
      setPagarId(null)
      setValorPag('')
    },
  })

  // AGRUPADO é um estado "resolvido" (absorvido por um fechamento) — só existe
  // quando o filtro de status é PAGO, então não precisa de tratamento especial
  // aqui; o back já devolve pelo status pedido.
  const contasFiltradas = clienteId
    ? contas.filter(c => String(c.cliente.id) === clienteId)
    : contas

  const contaDoPagamento = pagarId ? contas.find(c => c.id === pagarId) : undefined

  const totalFiltrado = contasFiltradas.reduce((s, c) => s + c.valor - c.valorPago, 0)

  return (
    <div className="p-6">
      <PageHeader
        title="Contas a Receber"
        subtitle="Fiado, caderneta e faturamento"
        actions={podeVerIdentificacao ? (
          <Button variant="secondary" onClick={() => setMostrarRelatorio(true)}>
            <FileText size={16} /> Histórico de recebimentos
          </Button>
        ) : undefined}
      />

      {/* Filtros — mesmo esquema da tela de Contas a Pagar: status como abas
          e cliente como um filtro opcional a mais, não mais um portão */}
      <Card padding="sm" className="flex items-center gap-3 mb-4 flex-wrap">
        <FilterTabs
          options={[
            { value: 'ABERTO', label: 'ABERTO' },
            { value: 'PARCIAL', label: 'PARCIAL' },
            { value: 'PAGO', label: 'PAGO' },
            { value: 'CANCELADO', label: 'CANCELADO' },
          ]}
          value={statusFiltro}
          onChange={setStatusFiltro}
        />
        <div className="ml-auto text-right">
          <p className="text-xs text-gray-400">Saldo devedor</p>
          <p className="text-lg font-bold text-danger-600 tabular-nums">{brl(totalFiltrado)}</p>
        </div>
      </Card>

      <Card padding="none" className="overflow-hidden">
        {isLoading ? (
          <LoadingState />
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>Cliente</TH>
                <TH>Descrição</TH>
                <TH align="right">Valor</TH>
                <TH align="right">Pago</TH>
                <TH align="right">Saldo</TH>
                <TH align="center">Status</TH>
                <TH>Venc.</TH>
                <TH />
              </tr>
            </THead>
            <TBody>
              {contasFiltradas.map(c => (
                <TR key={c.id} className={c.status === 'AGRUPADO' ? 'opacity-60' : ''}>
                  <TD className="font-medium">{c.cliente?.nome ?? '—'}</TD>
                  <TD>
                    {c.descricao ?? '—'}
                    {c.status === 'AGRUPADO' && c.absorvidoPorFaturamento && (
                      <span className="flex items-center gap-1 text-[11px] text-info-600 mt-0.5">
                        <Layers size={11} /> Agrupado no Fechamento #{c.absorvidoPorFaturamento.id}
                      </span>
                    )}
                  </TD>
                  <TD align="right" className="tabular-nums">{brl(c.valor)}</TD>
                  <TD align="right" className="tabular-nums text-success-600">{brl(c.valorPago)}</TD>
                  <TD align="right" className="tabular-nums font-bold text-danger-600">
                    {brl(c.valor - c.valorPago)}
                  </TD>
                  <TD align="center">
                    <StatusBadge tone={statusTom[c.status] ?? 'neutral'}>{c.status}</StatusBadge>
                  </TD>
                  <TD className="text-xs text-gray-500">{c.dataVencimento ?? '—'}</TD>
                  <TD>
                    <div className="flex items-center justify-end gap-1">
                      {podeVerIdentificacao && c.valorPago > 0 && (
                        <Button variant="ghost" size="sm" onClick={() => setHistoricoContaId(c.id)}
                          title="Histórico de recebimentos">
                          <History size={16} />
                        </Button>
                      )}
                      {(c.status === 'ABERTO' || c.status === 'PARCIAL') && (
                        <Button variant="ghost" size="sm" onClick={() => setPagarId(c.id)}
                          title="Registrar recebimento">
                          <CheckCircle size={16} />
                        </Button>
                      )}
                    </div>
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && contasFiltradas.length === 0 && (
          <EmptyState>Nenhuma conta com esse status.</EmptyState>
        )}
      </Card>

      {/* Histórico de recebimentos (administradores) */}
      {podeVerIdentificacao && historicoContaId !== null && (
        <HistoricoPagamentosModal tipo="receber" contaId={historicoContaId} onClose={() => setHistoricoContaId(null)} />
      )}
      {podeVerIdentificacao && mostrarRelatorio && (
        <HistoricoPagamentosModal tipo="receber" onClose={() => setMostrarRelatorio(false)} />
      )}

      {/* Modal pagamento */}
      {pagarId && (
        <Modal
          title="Registrar Pagamento"
          onClose={() => setPagarId(null)}
          footer={
            <>
              <Button variant="secondary" fullWidth onClick={() => setPagarId(null)}>Cancelar</Button>
              <Button
                variant="success"
                fullWidth
                loading={pagar.isPending}
                disabled={!valorPag}
                onClick={() => pagar.mutate({ contaId: pagarId, valor: parseFloat(valorPag) })}
              >
                Confirmar
              </Button>
            </>
          }
        >
          {contaDoPagamento && (
            <p className="text-xs text-gray-500">
              Saldo da conta: <span className="font-semibold text-danger-600 tabular-nums">
                {brl(contaDoPagamento.valor - contaDoPagamento.valorPago)}
              </span>
            </p>
          )}
          <div>
            <label className="text-xs font-medium text-gray-600 block mb-1">Valor Recebido</label>
            <CurrencyInput
              value={parseFloat(valorPag) || 0}
              onChange={(v) => setValorPag(String(v))}
              autoFocus
            />
          </div>
        </Modal>
      )}
    </div>
  )
}