import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../shared/api/axios'
import { Plus } from 'lucide-react'
import { formatBRL, formatWeightDisplay } from '@/shared/utils/mask'
import {
  PageHeader, Card, Modal, Button, StatusBadge, WeightInput, Field, baseInputClass,
  Table, THead, TH, TBody, TR, TD, EmptyState,
} from '@/shared/components/ui'
import type { BadgeTone } from '@/shared/components/ui'

interface Produto { id: number; nome: string; unidadeMedida: string; precoCusto: number }
interface Perda {
  id: number
  produto: { nome: string; unidadeMedida: string }
  quantidade: number
  custoTotal: number
  motivo: string
  observacao: string
  createdAt: string
}

const MOTIVOS = ['VENCIMENTO', 'AVARIA', 'FURTO', 'DESOSSA', 'OUTROS']
const fmt = formatBRL
const kg3 = formatWeightDisplay

// FURTO é o mais grave (crime); VENCIMENTO/AVARIA pedem atenção mas são
// operacionais; DESOSSA é perda esperada do processo (não é bem um "problema").
const motivoTom: Record<string, BadgeTone> = {
  FURTO: 'danger',
  VENCIMENTO: 'warning',
  AVARIA: 'warning',
  DESOSSA: 'neutral',
  OUTROS: 'neutral',
}

const hoje = new Date().toISOString().slice(0, 10)

export default function PerdasPage() {
  const qc = useQueryClient()
  const [inicio, setInicio] = useState(hoje)
  const [fim, setFim] = useState(hoje)
  const [showForm, setShowForm] = useState(false)
  const [produtoId, setProdutoId] = useState('')
  const [quantidade, setQuantidade] = useState(0)
  const [motivo, setMotivo] = useState('VENCIMENTO')
  const [observacao, setObservacao] = useState('')

  const podeLancar = !!produtoId && quantidade > 0

  const produtos = useQuery<Produto[]>({
    queryKey: ['produtos'],
    queryFn: () => api.get('/estoque/produtos').then(r => r.data),
  })

  const perdas = useQuery<Perda[]>({
    queryKey: ['perdas', inicio, fim],
    queryFn: () => api.get(`/estoque/perdas?inicio=${inicio}&fim=${fim}`).then(r => r.data),
  })

  const lancar = useMutation({
    mutationFn: () => api.post('/estoque/perdas', {
      produtoId: parseInt(produtoId),
      quantidade,
      motivo,
      observacao,
      usuarioId: 1,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['perdas'] })
      setShowForm(false)
      setProdutoId('')
      setQuantidade(0)
      setObservacao('')
    },
  })

  const totalPerdas = perdas.data?.reduce((s, p) => s + (p.custoTotal ?? 0), 0) ?? 0
  const produtoSel = produtos.data?.find(p => p.id === parseInt(produtoId))

  return (
    <div className="p-6 space-y-5">
      <PageHeader
        title="Controle de Perdas"
        subtitle="Vencimentos, avarias, furtos e quebras"
        actions={
          <Button variant="primary" onClick={() => setShowForm(true)}>
            <Plus size={16} /> Lançar Perda
          </Button>
        }
      />

      {/* Filtro de período */}
      <Card padding="sm" className="flex gap-4 items-end">
        <Field label="De">
          <input type="date" value={inicio} onChange={e => setInicio(e.target.value)} className={baseInputClass} />
        </Field>
        <Field label="Até">
          <input type="date" value={fim} onChange={e => setFim(e.target.value)} className={baseInputClass} />
        </Field>
        <div className="ml-auto text-right">
          <p className="text-xs text-gray-400">Total de perdas no período</p>
          <p className="text-xl font-bold text-danger-600 tabular-nums">{fmt(totalPerdas)}</p>
        </div>
      </Card>

      {/* Tabela */}
      <Card padding="none" className="overflow-hidden">
        <Table>
          <THead>
            <tr>
              <TH>Produto</TH>
              <TH>Motivo</TH>
              <TH align="right">Qtd</TH>
              <TH align="right">Custo</TH>
              <TH>Observação</TH>
              <TH>Data</TH>
            </tr>
          </THead>
          <TBody>
            {perdas.data?.length === 0 && (
              <tr><td colSpan={6}><EmptyState>Nenhuma perda no período</EmptyState></td></tr>
            )}
            {perdas.data?.map(p => (
              <TR key={p.id}>
                <TD className="font-medium">{p.produto.nome}</TD>
                <TD>
                  <StatusBadge tone={motivoTom[p.motivo] ?? 'neutral'}>{p.motivo}</StatusBadge>
                </TD>
                <TD align="right" className="tabular-nums">{kg3(p.quantidade)} {p.produto.unidadeMedida}</TD>
                <TD align="right" className="font-medium text-danger-600 tabular-nums">{fmt(p.custoTotal)}</TD>
                <TD className="text-gray-500 text-xs">{p.observacao || '—'}</TD>
                <TD className="text-gray-400">
                  {new Date(p.createdAt).toLocaleDateString('pt-BR')}
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
      </Card>

      {/* Modal lançar perda */}
      {showForm && (
        <Modal
          title="Lançar Perda"
          onClose={() => setShowForm(false)}
          footer={
            <>
              <Button variant="secondary" fullWidth onClick={() => setShowForm(false)}>Cancelar</Button>
              <Button variant="primary" fullWidth disabled={!podeLancar} loading={lancar.isPending} onClick={() => lancar.mutate()}>
                Lançar
              </Button>
            </>
          }
        >
          <Field label="Produto">
            <select value={produtoId} onChange={e => setProdutoId(e.target.value)} className={baseInputClass}>
              <option value="">Selecione...</option>
              {produtos.data?.map(p => (
                <option key={p.id} value={p.id}>{p.nome}</option>
              ))}
            </select>
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Quantidade">
              <WeightInput value={quantidade} onChange={setQuantidade} unit={(produtoSel?.unidadeMedida ?? 'kg').toLowerCase()} />
            </Field>
            <Field label="Motivo">
              <select value={motivo} onChange={e => setMotivo(e.target.value)} className={baseInputClass}>
                {MOTIVOS.map(m => <option key={m}>{m}</option>)}
              </select>
            </Field>
          </div>
          <Field label="Observação">
            <input type="text" value={observacao} onChange={e => setObservacao(e.target.value)} className={baseInputClass} />
          </Field>
        </Modal>
      )}
    </div>
  )
}
