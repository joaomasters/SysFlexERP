import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../shared/api/axios'
import { CheckCircle2, XCircle, Filter, Printer } from 'lucide-react'
import { formatWeightDisplay } from '@/shared/utils/mask'
import { PageHeader, Card, Button, StatusBadge, WeightInput, baseInputClass } from '@/shared/components/ui'
import type { BadgeTone } from '@/shared/components/ui'
import RelatorioEstoqueModal from './components/RelatorioEstoqueModal'

interface Inventario {
  id: number; status: string; observacao: string
  dataInicio: string; dataFim: string; createdAt: string
}
interface InventarioItem {
  id: number
  produto: { id: number; nome: string; unidadeMedida: string }
  saldoSistema: number; saldoContado: number | null; divergencia: number | null
}

const hoje = new Date().toISOString().slice(0, 10)
const trintaDiasAtras = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
const kg3 = formatWeightDisplay

// ABERTO = contagem em andamento (processo, não problema); FINALIZADO = concluído;
// CANCELADO = neutro.
const statusTom: Record<string, BadgeTone> = {
  ABERTO: 'info',
  FINALIZADO: 'success',
  CANCELADO: 'neutral',
}

export default function InventarioPage() {
  const qc = useQueryClient()
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [observacao, setObservacao] = useState('')
  const [contagens, setContagens] = useState<Record<number, number>>({})
  const [showRelatorio, setShowRelatorio] = useState(false)

  // Filtro de período da lista de inventários — padrão de 30 dias, com
  // opção de limpar pra ver o histórico completo.
  const [filtroInicio, setFiltroInicio] = useState(trintaDiasAtras)
  const [filtroFim, setFiltroFim]       = useState(hoje)

  const lista = useQuery<Inventario[]>({
    queryKey: ['inventarios', filtroInicio, filtroFim],
    queryFn: () => api.get('/estoque/inventario', {
      params: { inicio: filtroInicio || undefined, fim: filtroFim || undefined },
    }).then(r => r.data),
  })

  const itens = useQuery<InventarioItem[]>({
    queryKey: ['inventario-itens', selectedId],
    queryFn: () => api.get(`/estoque/inventario/${selectedId}/itens`).then(r => r.data),
    enabled: !!selectedId,
  })

  const abrir = useMutation({
    mutationFn: () => api.post('/estoque/inventario/abrir', { usuarioId: 1, observacao }),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ['inventarios'] })
      setSelectedId(r.data.id)
    },
  })

  const contar = useMutation({
    mutationFn: ({ produtoId, val }: { produtoId: number; val: number }) =>
      api.patch(`/estoque/inventario/${selectedId}/itens/${produtoId}`, {
        saldoContado: val,
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['inventario-itens', selectedId] }),
  })

  const finalizar = useMutation({
    mutationFn: () => api.post(`/estoque/inventario/${selectedId}/finalizar`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['inventarios'] })
      qc.invalidateQueries({ queryKey: ['inventario-itens', selectedId] })
    },
  })

  const cancelar = useMutation({
    mutationFn: () => api.post(`/estoque/inventario/${selectedId}/cancelar`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['inventarios'] }),
  })

  const inventarioAtivo = lista.data?.find(i => i.id === selectedId)
  const isAberto = inventarioAtivo?.status === 'ABERTO'

  return (
    <div className="p-6 space-y-5">
      <PageHeader
        title="Inventário Físico"
        subtitle="Contagem física vs saldo do sistema"
        actions={
          <Button variant="secondary" onClick={() => setShowRelatorio(true)}>
            <Printer size={16} /> Relatório de Estoque
          </Button>
        }
      />

      <div className="grid grid-cols-3 gap-5">
        {/* Painel esquerdo — lista */}
        <div className="col-span-1 space-y-3">
          <Card padding="sm" className="space-y-3">
            <p className="text-sm font-semibold text-gray-700">Novo Inventário</p>
            <input type="text" value={observacao} onChange={e => setObservacao(e.target.value)}
              placeholder="Observação (opcional)"
              className={baseInputClass} />
            <Button variant="primary" fullWidth loading={abrir.isPending} onClick={() => abrir.mutate()}>
              Abrir Inventário
            </Button>
          </Card>

          <Card padding="sm" className="space-y-2">
            <p className="text-xs font-semibold text-gray-500 flex items-center gap-1.5">
              <Filter size={12} /> Filtrar por período
            </p>
            <div className="flex gap-2">
              <input type="date" value={filtroInicio} onChange={e => setFiltroInicio(e.target.value)}
                className="w-full border border-gray-300 rounded-lg px-2 py-1.5 text-xs" />
              <input type="date" value={filtroFim} onChange={e => setFiltroFim(e.target.value)}
                className="w-full border border-gray-300 rounded-lg px-2 py-1.5 text-xs" />
            </div>
            {(filtroInicio || filtroFim) && (
              <button
                onClick={() => { setFiltroInicio(''); setFiltroFim('') }}
                className="text-[11px] text-info-600 hover:text-info-700 font-medium"
              >
                Ver histórico completo
              </button>
            )}
          </Card>

          <div className="space-y-2">
            {lista.data?.map(inv => (
              <button key={inv.id} onClick={() => setSelectedId(inv.id)}
                className={`w-full text-left p-3 rounded-xl border text-sm transition-colors
                  ${selectedId === inv.id ? 'border-primary-500 bg-primary-50' : 'bg-white hover:bg-gray-50 border-gray-200'}`}>
                <div className="flex items-center justify-between">
                  <span className="font-medium">#{inv.id}</span>
                  <StatusBadge tone={statusTom[inv.status] ?? 'neutral'}>{inv.status}</StatusBadge>
                </div>
                <p className="text-xs text-gray-400 mt-1">
                  {new Date(inv.createdAt).toLocaleDateString('pt-BR')}
                </p>
              </button>
            ))}
            {lista.data?.length === 0 && (
              <p className="text-center text-xs text-gray-400 py-4">Nenhum inventário no período.</p>
            )}
          </div>
        </div>

        {/* Painel direito — itens */}
        <div className="col-span-2">
          {!selectedId && (
            <div className="h-full flex items-center justify-center text-gray-400 text-sm">
              Selecione um inventário para visualizar os itens
            </div>
          )}
          {selectedId && (
            <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
              <div className="px-5 py-3 border-b bg-gray-50 flex items-center justify-between">
                <span className="text-sm font-semibold text-gray-700">Itens — Inventário #{selectedId}</span>
                {isAberto && (
                  <div className="flex gap-2">
                    <Button variant="secondary" size="sm" onClick={() => cancelar.mutate()}>
                      <XCircle size={14} /> Cancelar
                    </Button>
                    <Button variant="success" size="sm" onClick={() => finalizar.mutate()}>
                      <CheckCircle2 size={14} /> Finalizar e Ajustar
                    </Button>
                  </div>
                )}
              </div>
              <div className="overflow-y-auto max-h-[60vh]">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-xs text-gray-400 border-b">
                      <th className="px-4 py-2 text-left">Produto</th>
                      <th className="px-4 py-2 text-right">Sistema</th>
                      <th className="px-4 py-2 text-right">Contado</th>
                      <th className="px-4 py-2 text-right">Divergência</th>
                    </tr>
                  </thead>
                  <tbody>
                    {itens.data?.map(item => (
                      <tr key={item.id} className="border-b last:border-0 hover:bg-gray-50">
                        <td className="px-4 py-2.5 font-medium">{item.produto.nome}</td>
                        <td className="px-4 py-2.5 text-right text-gray-500 tabular-nums">
                          {kg3(item.saldoSistema)} {item.produto.unidadeMedida}
                        </td>
                        <td className="px-4 py-2.5 text-right">
                          {isAberto ? (
                            <WeightInput
                              value={contagens[item.produto.id] ?? item.saldoContado ?? 0}
                              onChange={v => setContagens(p => ({ ...p, [item.produto.id]: v }))}
                              onBlur={() => {
                                const v = contagens[item.produto.id]
                                if (v !== undefined) contar.mutate({ produtoId: item.produto.id, val: v })
                              }}
                              unit={item.produto.unidadeMedida.toLowerCase()}
                              size="sm"
                              className="w-32 ml-auto"
                            />
                          ) : (
                            <span className="tabular-nums">{item.saldoContado != null ? kg3(item.saldoContado) : '—'}</span>
                          )}
                        </td>
                        <td className="px-4 py-2.5 text-right font-medium tabular-nums">
                          {item.divergencia != null ? (
                            <span className={item.divergencia >= 0 ? 'text-success-600' : 'text-danger-600'}>
                              {item.divergencia >= 0 ? '+' : ''}{kg3(item.divergencia)}
                            </span>
                          ) : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      </div>

      {showRelatorio && <RelatorioEstoqueModal onClose={() => setShowRelatorio(false)} />}
    </div>
  )
}