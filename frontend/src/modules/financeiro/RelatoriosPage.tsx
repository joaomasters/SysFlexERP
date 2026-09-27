import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../shared/api/axios'
import { BarChart2, TrendingUp, TrendingDown, Package, ShoppingBag } from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { formatBRL, formatWeightDisplay } from '@/shared/utils/mask'
import { PageHeader, Card, Field, baseInputClass, Table, THead, TH, TBody, TR, TD } from '@/shared/components/ui'

interface RelatorioVendas {
  totalVendas: number; quantidadeVendas: number; ticketMedio: number; totalPerdas: number
  topProdutos: { produtoId: number; nomeProduto: string; quantidadeTotal: number; valorTotal: number }[]
}
interface FluxoCaixa {
  totalRecebimentos: number; totalPagamentos: number; saldo: number
}

const fmt = formatBRL
const hoje = new Date().toISOString().slice(0, 10)
const primeiroDiaMes = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10)

export default function RelatoriosPage() {
  const [inicio, setInicio] = useState(primeiroDiaMes)
  const [fim, setFim] = useState(hoje)

  const relVendas = useQuery<RelatorioVendas>({
    queryKey: ['rel-vendas', inicio, fim],
    queryFn: () => api.get(`/financeiro/relatorios/vendas?inicio=${inicio}&fim=${fim}`).then(r => r.data),
  })

  const fluxo = useQuery<FluxoCaixa>({
    queryKey: ['fluxo-caixa', inicio, fim],
    queryFn: () => api.get(`/financeiro/relatorios/fluxo-caixa?inicio=${inicio}&fim=${fim}`).then(r => r.data),
  })

  const cards = [
    { label: 'Total de Vendas', value: fmt(relVendas.data?.totalVendas ?? 0), icon: TrendingUp, color: 'text-success-600 bg-success-50' },
    { label: 'Qtd. Vendas', value: relVendas.data?.quantidadeVendas ?? 0, icon: ShoppingBag, color: 'text-info-600 bg-info-50' },
    { label: 'Ticket Médio', value: fmt(relVendas.data?.ticketMedio ?? 0), icon: BarChart2, color: 'text-purple-600 bg-purple-50' },
    { label: 'Total Perdas', value: fmt(relVendas.data?.totalPerdas ?? 0), icon: TrendingDown, color: 'text-danger-600 bg-danger-50' },
  ]

  const saldoPositivo = (fluxo.data?.saldo ?? 0) >= 0

  return (
    <div className="p-6 space-y-6">
      <PageHeader title="Relatórios" subtitle="Análise de desempenho e controle gerencial" />

      {/* Filtro de período */}
      <Card padding="sm" className="flex gap-4 items-end">
        <Field label="De">
          <input type="date" value={inicio} onChange={e => setInicio(e.target.value)} className={baseInputClass} />
        </Field>
        <Field label="Até">
          <input type="date" value={fim} onChange={e => setFim(e.target.value)} className={baseInputClass} />
        </Field>
      </Card>

      {/* Cards de resumo */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {cards.map(c => (
          <Card key={c.label} padding="md">
            <div className={`inline-flex p-2 rounded-lg ${c.color} mb-3`}>
              <c.icon size={20} />
            </div>
            <p className="text-2xl font-bold text-gray-900 tabular-nums">{c.value}</p>
            <p className="text-xs text-gray-500 mt-1">{c.label}</p>
          </Card>
        ))}
      </div>

      {/* Fluxo de Caixa */}
      <Card>
        <h2 className="text-base font-semibold text-gray-700 mb-4">Fluxo de Caixa</h2>
        <div className="grid grid-cols-3 gap-4">
          <div className="text-center p-4 bg-success-50 rounded-lg">
            <p className="text-xs text-gray-500">Entradas</p>
            <p className="text-xl font-bold text-success-600 tabular-nums">{fmt(fluxo.data?.totalRecebimentos ?? 0)}</p>
          </div>
          <div className="text-center p-4 bg-danger-50 rounded-lg">
            <p className="text-xs text-gray-500">Saídas</p>
            <p className="text-xl font-bold text-danger-600 tabular-nums">{fmt(fluxo.data?.totalPagamentos ?? 0)}</p>
          </div>
          <div className={`text-center p-4 rounded-lg ${saldoPositivo ? 'bg-info-50' : 'bg-warning-50'}`}>
            <p className="text-xs text-gray-500">Saldo</p>
            <p className={`text-xl font-bold tabular-nums ${saldoPositivo ? 'text-info-600' : 'text-warning-600'}`}>
              {fmt(fluxo.data?.saldo ?? 0)}
            </p>
          </div>
        </div>
      </Card>

      {/* Top 10 Produtos */}
      {relVendas.data?.topProdutos && relVendas.data.topProdutos.length > 0 && (
        <Card>
          <h2 className="text-base font-semibold text-gray-700 mb-4 flex items-center gap-2">
            <Package size={18} className="text-gray-500" />
            Top 10 Produtos por Faturamento
          </h2>
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={relVendas.data.topProdutos} layout="vertical"
                margin={{ left: 20, right: 20, top: 5, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                <XAxis type="number" tickFormatter={v => `R$${v.toFixed(0)}`} tick={{ fontSize: 11 }} />
                <YAxis type="category" dataKey="nomeProduto" width={120} tick={{ fontSize: 11 }} />
                <Tooltip formatter={(v: number) => fmt(v)} />
                <Bar dataKey="valorTotal" name="Faturamento" fill="#A32B1E" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Tabela ranking */}
          <Table>
            <THead>
              <tr>
                <TH>#</TH>
                <TH>Produto</TH>
                <TH align="right">Qtd Vendida</TH>
                <TH align="right">Faturamento</TH>
              </tr>
            </THead>
            <TBody>
              {relVendas.data.topProdutos.map((p, i) => (
                <TR key={p.produtoId}>
                  <TD className="text-gray-400 font-mono">{i + 1}</TD>
                  <TD className="font-medium">{p.nomeProduto}</TD>
                  <TD align="right" className="text-gray-600 tabular-nums">{formatWeightDisplay(p.quantidadeTotal ?? 0)}</TD>
                  <TD align="right" className="font-semibold text-success-600 tabular-nums">{fmt(p.valorTotal)}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </Card>
      )}
    </div>
  )
}
