import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { TrendingUp, TrendingDown } from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts'
import { api } from '@/shared/api/axios'
import { formatBRL, formatPercent } from '@/shared/utils/mask'
import { PageHeader, Card, Button, CurrencyInput, Field, baseInputClass } from '@/shared/components/ui'

interface DreDTO {
  periodo: string
  receitaBruta: number
  cmv: number
  lucroBruto: number
  percentualLucroBruto: number
  custosOperacionais: number
  lucroLiquido: number
  percentualLucroLiquido: number
  margensPorProduto: {
    produtoId: number
    nomeProduto: string
    quantidadeVendida: number
    receita: number
    cmv: number
    margem: number
    percentualMargem: number
  }[]
}

const brl = formatBRL
const pct = (v: number) => formatPercent(v)

// Cores dos indicadores do DRE seguem os tokens semânticos: receita é uma
// informação neutra (info), CMV/custos são saídas (danger/warning), lucro é
// o resultado positivo (success) — o Lucro Líquido inverte para danger se negativo.
export default function DrePage() {
  const agora = new Date()
  const [ano, setAno]   = useState(agora.getFullYear())
  const [mes, setMes]   = useState(agora.getMonth() + 1)
  const [opex, setOpex] = useState(0)

  const { data: dre, isLoading, refetch } = useQuery<DreDTO>({
    queryKey: ['dre', ano, mes, opex],
    queryFn: () =>
      api.get('/financeiro/dre', { params: { ano, mes, custosOperacionais: opex } })
         .then(r => r.data),
  })

  const cards = dre ? [
    { label: 'Receita Bruta',        value: dre.receitaBruta,        cor: 'text-info-600',    bg: 'bg-info-50' },
    { label: 'CMV',                   value: dre.cmv,                 cor: 'text-danger-600',  bg: 'bg-danger-50',  neg: true },
    { label: 'Lucro Bruto',           value: dre.lucroBruto,          cor: 'text-success-600', bg: 'bg-success-50', pct: dre.percentualLucroBruto },
    { label: 'Custos Operacionais',   value: dre.custosOperacionais,  cor: 'text-warning-600', bg: 'bg-warning-50', neg: true },
    { label: 'Lucro Líquido',         value: dre.lucroLiquido,        cor: dre.lucroLiquido >= 0 ? 'text-success-700' : 'text-danger-700', bg: dre.lucroLiquido >= 0 ? 'bg-success-100' : 'bg-danger-100', pct: dre.percentualLucroLiquido },
  ] : []

  return (
    <div className="p-6">
      <PageHeader title="DRE Simplificado" subtitle="Demonstrativo de Resultado do Exercício" />

      {/* Filtros */}
      <Card className="flex items-end gap-4 mb-6">
        <Field label="Mês">
          <select value={mes} onChange={e => setMes(Number(e.target.value))} className={`${baseInputClass} w-40`}>
            {Array.from({ length: 12 }, (_, i) => (
              <option key={i+1} value={i+1}>
                {new Date(2000, i).toLocaleString('pt-BR', { month: 'long' })}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Ano">
          <input type="number" value={ano} onChange={e => setAno(Number(e.target.value))}
            className={`${baseInputClass} w-24`} />
        </Field>
        <Field label="Custos Op.">
          <CurrencyInput value={opex} onChange={setOpex} className="w-36" />
        </Field>
        <Button variant="primary" onClick={() => refetch()}>Calcular</Button>
      </Card>

      {isLoading && <div className="text-center py-12 text-gray-500 text-sm">Calculando...</div>}

      {dre && (
        <>
          {/* Cards DRE */}
          <div className="grid grid-cols-5 gap-4 mb-6">
            {cards.map((c, i) => (
              <div key={i} className={`rounded-xl p-4 ${c.bg}`}>
                <p className="text-xs font-medium text-gray-500 mb-1">{c.label}</p>
                <p className={`text-lg font-bold tabular-nums ${c.cor}`}>
                  {brl(c.value)}
                </p>
                {c.pct !== undefined && (
                  <p className={`text-xs mt-0.5 flex items-center gap-1 ${c.cor}`}>
                    {c.value >= 0 ? <TrendingUp size={11} /> : <TrendingDown size={11} />}
                    {pct(c.pct)} da receita
                  </p>
                )}
              </div>
            ))}
          </div>

          {/* Gráfico de margem por produto */}
          <Card>
            <h2 className="font-semibold text-gray-900 mb-4">Margem de Lucro por Corte</h2>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={dre.margensPorProduto} layout="vertical"
                margin={{ top: 0, right: 20, left: 120, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                <XAxis type="number" tickFormatter={v => `${v.toFixed(0)}%`} domain={[0, 100]} />
                <YAxis type="category" dataKey="nomeProduto" tick={{ fontSize: 12 }} width={120} />
                <Tooltip formatter={(v: number) => `${v.toFixed(1)}%`} />
                <Bar dataKey="percentualMargem" radius={[0, 4, 4, 0]}>
                  {dre.margensPorProduto.map((entry, i) => (
                    <Cell
                      key={i}
                      fill={
                        entry.percentualMargem >= 30 ? '#2F6B4F'   // success-600
                        : entry.percentualMargem >= 15 ? '#B9780F' // warning-600
                        : '#C23A2C'                                 // danger-600
                      }
                    />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
            <p className="text-xs text-gray-400 mt-2">
              Verde ≥ 30% • Amarelo ≥ 15% • Vermelho &lt; 15%
            </p>
          </Card>
        </>
      )}
    </div>
  )
}
