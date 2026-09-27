import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, ArrowRight } from 'lucide-react'
import { api } from '@/shared/api/axios'
import toast from 'react-hot-toast'
import type { FaturamentoCliente, Cliente } from '@/types/venda'
import { formatBRL } from '@/shared/utils/mask'
import {
  PageHeader, Card, Modal, Button, StatusBadge, Field, baseInputClass,
  Table, THead, TH, TBody, TR, TD, LoadingState,
} from '@/shared/components/ui'
import type { BadgeTone } from '@/shared/components/ui'

const brl = formatBRL

// ABERTO = fechamento em andamento, ainda dentro do prazo (processo, não problema);
// PARCIAL = pago em parte (atenção); QUITADO = concluído; VENCIDO = passou do prazo (crítico).
const statusTom: Record<string, BadgeTone> = {
  ABERTO:  'info',
  PARCIAL: 'warning',
  QUITADO: 'success',
  VENCIDO: 'danger',
}

export default function FaturamentoPage() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [showForm, setShowForm] = useState(false)
  const [clienteId, setClienteId] = useState('')
  const [inicio, setInicio]       = useState('')
  const [fim, setFim]             = useState('')

  const { data: faturamentos = [], isLoading } = useQuery<FaturamentoCliente[]>({
    queryKey: ['faturamentos'],
    queryFn: () => api.get('/financeiro/faturamento/abertos').then(r => r.data),
  })

  const { data: clientes = [] } = useQuery<Cliente[]>({
    queryKey: ['clientes'],
    queryFn: () => api.get('/financeiro/clientes').then(r => r.data).catch(() => []),
  })

  const gerar = useMutation({
    mutationFn: () =>
      api.post('/financeiro/faturamento/fechar', null, {
        params: { clienteId, inicio, fim },
      }),
    onSuccess: () => {
      toast.success('Faturamento gerado!')
      qc.invalidateQueries({ queryKey: ['faturamentos'] })
      setShowForm(false)
    },
  })

  return (
    <div className="p-6">
      <PageHeader
        title="Faturamento"
        subtitle="Fechamento de contas"
        actions={
          <Button variant="primary" onClick={() => setShowForm(true)}>
            <Plus size={16} /> Gerar Fechamento
          </Button>
        }
      />

      {/* Tabela */}
      <Card padding="none" className="overflow-hidden">
        {isLoading ? (
          <LoadingState />
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>Cliente</TH>
                <TH>Período</TH>
                <TH align="right">Total</TH>
                <TH align="right">Pago</TH>
                <TH align="right">Saldo</TH>
                <TH align="center">Status</TH>
                <TH>Vencimento</TH>
                <TH />
              </tr>
            </THead>
            <TBody>
              {faturamentos.map(f => (
                <TR key={f.id}>
                  <TD className="font-medium">{f.cliente.nome}</TD>
                  <TD className="text-gray-500 text-xs">
                    {f.periodoInicio} a {f.periodoFim}
                  </TD>
                  <TD align="right" className="tabular-nums">{brl(f.totalVendas)}</TD>
                  <TD align="right" className="tabular-nums text-success-600">{brl(f.totalPago)}</TD>
                  <TD align="right" className="tabular-nums font-bold text-danger-600">{brl(f.saldoDevedor)}</TD>
                  <TD align="center">
                    <StatusBadge tone={statusTom[f.status] ?? 'neutral'}>{f.status}</StatusBadge>
                  </TD>
                  <TD className="text-gray-500 text-xs">{f.dataVencimento}</TD>
                  <TD>
                    {f.status !== 'QUITADO' && (
                      <Button
                        variant="secondary" size="sm"
                        onClick={() => navigate(`/financeiro/contas-receber?clienteId=${f.cliente.id}`)}
                        title="O pagamento é registrado na tela de Contas a Receber"
                      >
                        Receber <ArrowRight size={13} />
                      </Button>
                    )}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {/* Modal gerar fechamento */}
      {showForm && (
        <Modal
          title="Gerar Fechamento de Faturamento"
          onClose={() => setShowForm(false)}
          footer={
            <>
              <Button variant="secondary" fullWidth onClick={() => setShowForm(false)}>Cancelar</Button>
              <Button
                variant="primary" fullWidth
                loading={gerar.isPending}
                disabled={!clienteId || !inicio || !fim}
                onClick={() => gerar.mutate()}
              >
                Gerar
              </Button>
            </>
          }
        >
          <Field label="Cliente">
            <select value={clienteId} onChange={e => setClienteId(e.target.value)} className={baseInputClass}>
              <option value="">Selecione um cliente...</option>
              {clientes.map(c => (
                <option key={c.id} value={c.id}>{c.nome}</option>
              ))}
            </select>
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label="Data Início">
              <input type="date" value={inicio} onChange={e => setInicio(e.target.value)} className={baseInputClass} />
            </Field>
            <Field label="Data Fim">
              <input type="date" value={fim} onChange={e => setFim(e.target.value)} className={baseInputClass} />
            </Field>
          </div>

          <p className="text-[11px] text-gray-400">
            Só entram no fechamento vendas fiado ainda em aberto nesse período — vendas já pagas
            na hora (dinheiro/cartão/PIX) não são cobradas de novo aqui.
          </p>
        </Modal>
      )}
    </div>
  )
}
