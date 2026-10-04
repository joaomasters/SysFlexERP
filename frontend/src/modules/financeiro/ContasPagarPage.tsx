import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../shared/api/axios'
import { Plus, AlertCircle, History, FileText } from 'lucide-react'
import { formatBRL } from '@/shared/utils/mask'
import {
  PageHeader, Card, FilterTabs, Modal, Button, StatusBadge, CurrencyInput, Field, baseInputClass,
  Table, THead, TH, TBody, TR, TD, EmptyState,
} from '@/shared/components/ui'
import type { BadgeTone } from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import HistoricoPagamentosModal from './components/HistoricoPagamentosModal'

interface ContaPagar {
  id: number; descricao: string; fornecedor: string; valor: number
  valorPago: number; dataVencimento: string; dataPagamento: string | null
  categoria: string; status: string; observacao: string
}

const fmt = formatBRL

// ABERTO/PARCIAL = pendente, aguardando pagamento (atenção); PAGO = concluído;
// CANCELADO = neutro.
const statusTom: Record<string, BadgeTone> = {
  ABERTO: 'warning',
  PAGO: 'success',
  PARCIAL: 'warning',
  CANCELADO: 'neutral',
}

export default function ContasPagarPage() {
  const qc = useQueryClient()
  const { podeVerIdentificacao } = usePermissao()
  const [statusFiltro, setStatusFiltro] = useState('ABERTO')
  const [showForm, setShowForm] = useState(false)
  const [pagandoId, setPagandoId] = useState<number | null>(null)
  const [valorPag, setValorPag] = useState(0)
  // Histórico de pagamentos (somente administradores): de uma conta, ou o relatório geral
  const [historicoContaId, setHistoricoContaId] = useState<number | null>(null)
  const [mostrarRelatorio, setMostrarRelatorio] = useState(false)
  const [form, setForm] = useState({
    descricao: '', fornecedor: '', valor: 0, dataVencimento: '', categoria: '', observacao: ''
  })

  const contas = useQuery<ContaPagar[]>({
    queryKey: ['contas-pagar', statusFiltro],
    queryFn: () => api.get(`/financeiro/contas-pagar?status=${statusFiltro}`).then(r => r.data),
  })

  const vencidas = useQuery<ContaPagar[]>({
    queryKey: ['contas-pagar-vencidas'],
    queryFn: () => api.get('/financeiro/contas-pagar/vencidas').then(r => r.data),
  })

  const criar = useMutation({
    mutationFn: () => api.post('/financeiro/contas-pagar', { ...form }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['contas-pagar'] })
      setShowForm(false)
      setForm({ descricao: '', fornecedor: '', valor: 0, dataVencimento: '', categoria: '', observacao: '' })
    },
  })

  const pagar = useMutation({
    mutationFn: () => api.post(`/financeiro/contas-pagar/${pagandoId}/pagar`, { valor: valorPag }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['contas-pagar'] })
      qc.invalidateQueries({ queryKey: ['historico-pagamentos'] })
      qc.invalidateQueries({ queryKey: ['contas-pagar-vencidas'] })
      setPagandoId(null)
      setValorPag(0)
    },
  })

  const cancelar = useMutation({
    mutationFn: (id: number) => api.post(`/financeiro/contas-pagar/${id}/cancelar`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['contas-pagar'] }),
  })

  const contaDoPagamento = pagandoId !== null ? contas.data?.find(c => c.id === pagandoId) : undefined

  const totalAberto = contas.data?.reduce((s, c) => s + (c.valor - c.valorPago), 0) ?? 0

  return (
    <div className="p-6 space-y-5">
      <PageHeader
        title="Contas a Pagar"
        subtitle="Gestão de pagamentos e fornecedores"
        actions={
          <div className="flex gap-2">
            {podeVerIdentificacao && (
              <Button variant="secondary" onClick={() => setMostrarRelatorio(true)}>
                <FileText size={16} /> Histórico de pagamentos
              </Button>
            )}
            <Button variant="primary" onClick={() => setShowForm(true)}>
              <Plus size={16} /> Nova Conta
            </Button>
          </div>
        }
      />

      {/* Alerta de vencidas */}
      {(vencidas.data?.length ?? 0) > 0 && (
        <div className="bg-danger-50 border border-danger-100 rounded-xl p-4 flex items-start gap-3">
          <AlertCircle size={20} className="text-danger-500 mt-0.5 flex-shrink-0" />
          <div>
            <p className="text-sm font-semibold text-danger-700">
              {vencidas.data?.length} conta(s) vencida(s) sem pagamento
            </p>
            <p className="text-xs text-danger-500 mt-0.5">
              Total: {fmt(vencidas.data?.reduce((s, c) => s + c.valor, 0) ?? 0)}
            </p>
          </div>
        </div>
      )}

      {/* Filtros + total */}
      <Card padding="sm" className="flex items-center gap-3 flex-wrap">
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
          <p className="text-lg font-bold text-danger-600 tabular-nums">{fmt(totalAberto)}</p>
        </div>
      </Card>

      {/* Tabela */}
      <Card padding="none" className="overflow-hidden">
        <Table>
          <THead>
            <tr>
              <TH>Descrição</TH>
              <TH>Fornecedor</TH>
              <TH>Vencimento</TH>
              <TH align="right">Valor</TH>
              <TH align="right">Pago</TH>
              <TH>Status</TH>
              <TH />
            </tr>
          </THead>
          <TBody>
            {contas.data?.length === 0 && (
              <tr><td colSpan={7}><EmptyState>Nenhuma conta encontrada</EmptyState></td></tr>
            )}
            {contas.data?.map(c => (
              <TR key={c.id}>
                <TD className="font-medium">{c.descricao}</TD>
                <TD className="text-gray-500">{c.fornecedor || '—'}</TD>
                <TD>
                  <span className={new Date(c.dataVencimento) < new Date() && c.status === 'ABERTO'
                    ? 'text-danger-600 font-medium' : 'text-gray-600'}>
                    {new Date(c.dataVencimento).toLocaleDateString('pt-BR')}
                  </span>
                </TD>
                <TD align="right" className="font-medium tabular-nums">{fmt(c.valor)}</TD>
                <TD align="right" className="text-success-600 tabular-nums">{fmt(c.valorPago)}</TD>
                <TD>
                  <StatusBadge tone={statusTom[c.status] ?? 'neutral'}>{c.status}</StatusBadge>
                </TD>
                <TD>
                  <div className="flex items-center gap-2">
                    {podeVerIdentificacao && c.valorPago > 0 && (
                      <Button variant="ghost" size="sm" onClick={() => setHistoricoContaId(c.id)}
                        title="Histórico de pagamentos">
                        <History size={16} />
                      </Button>
                    )}
                    {(c.status === 'ABERTO' || c.status === 'PARCIAL') && (
                      <div className="flex gap-2">
                        <Button variant="success" size="sm" onClick={() => { setPagandoId(c.id); setValorPag(0) }}>
                          Pagar
                        </Button>
                        <Button variant="outline-danger" size="sm" onClick={() => cancelar.mutate(c.id)}>
                          Cancelar
                        </Button>
                      </div>
                    )}
                  </div>
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
      </Card>

      {/* Histórico de pagamentos (administradores) */}
      {podeVerIdentificacao && historicoContaId !== null && (
        <HistoricoPagamentosModal tipo="pagar" contaId={historicoContaId} onClose={() => setHistoricoContaId(null)} />
      )}
      {podeVerIdentificacao && mostrarRelatorio && (
        <HistoricoPagamentosModal tipo="pagar" onClose={() => setMostrarRelatorio(false)} />
      )}

      {/* Modal nova conta */}
      {showForm && (
        <Modal
          title="Nova Conta a Pagar"
          onClose={() => setShowForm(false)}
          footer={
            <>
              <Button variant="secondary" fullWidth onClick={() => setShowForm(false)}>Cancelar</Button>
              <Button
                variant="primary"
                fullWidth
                loading={criar.isPending}
                disabled={!form.descricao || !form.valor || !form.dataVencimento}
                onClick={() => criar.mutate()}
              >
                Criar
              </Button>
            </>
          }
        >
          <Field label="Descrição *">
            <input
              value={form.descricao}
              onChange={e => setForm(p => ({ ...p, descricao: e.target.value }))}
              placeholder="Ex: Compra de bovino"
              className={baseInputClass}
            />
          </Field>
          <Field label="Fornecedor">
            <input
              value={form.fornecedor}
              onChange={e => setForm(p => ({ ...p, fornecedor: e.target.value }))}
              placeholder="Nome do fornecedor"
              className={baseInputClass}
            />
          </Field>
          <Field label="Valor *">
            <CurrencyInput value={form.valor} onChange={v => setForm(p => ({ ...p, valor: v }))} />
          </Field>
          <Field label="Vencimento *">
            <input
              type="date"
              value={form.dataVencimento}
              onChange={e => setForm(p => ({ ...p, dataVencimento: e.target.value }))}
              className={baseInputClass}
            />
          </Field>
          <Field label="Categoria">
            <input
              value={form.categoria}
              onChange={e => setForm(p => ({ ...p, categoria: e.target.value }))}
              placeholder="Ex: Matéria-prima"
              className={baseInputClass}
            />
          </Field>
        </Modal>
      )}

      {/* Modal pagar */}
      {pagandoId !== null && (
        <Modal
          title="Registrar Pagamento"
          onClose={() => setPagandoId(null)}
          maxWidth="sm"
          footer={
            <>
              <Button variant="secondary" fullWidth onClick={() => setPagandoId(null)}>Cancelar</Button>
              <Button variant="success" fullWidth disabled={!valorPag} loading={pagar.isPending} onClick={() => pagar.mutate()}>
                Confirmar
              </Button>
            </>
          }
        >
          {contaDoPagamento && (
            <p className="text-xs text-gray-500">
              Saldo da conta: <span className="font-semibold text-danger-600 tabular-nums">
                {fmt(contaDoPagamento.valor - contaDoPagamento.valorPago)}
              </span>
            </p>
          )}
          <Field label="Valor Pago">
            <CurrencyInput value={valorPag} onChange={setValorPag} autoFocus />
          </Field>
        </Modal>
      )}
    </div>
  )
}
