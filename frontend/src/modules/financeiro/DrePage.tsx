import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { TrendingUp, TrendingDown, Printer } from 'lucide-react'
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

/** Altura de cada barra do gráfico de margem (px) — o gráfico cresce com a lista e rola. */
const ALTURA_BARRA = 26
/** Margens muito negativas (custo errado, venda abaixo do custo) esmagam a escala: o eixo para aqui. */
const PISO_EIXO = -100

const corMargem = (m: number) =>
  m >= 30 ? '#2F6B4F'   // success-600
  : m >= 15 ? '#B9780F' // warning-600
  : '#C23A2C'           // danger-600

function useTelaEstreita() {
  const consulta = '(max-width: 639px)'
  const [estreita, setEstreita] = useState(() => window.matchMedia(consulta).matches)
  useEffect(() => {
    const mq = window.matchMedia(consulta)
    const onChange = () => setEstreita(mq.matches)
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])
  return estreita
}

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

  const nomeMes = new Date(2000, mes - 1).toLocaleString('pt-BR', { month: 'long' })
  const telaEstreita = useTelaEstreita()

  // Do melhor para o pior: quem precisa de atenção fica junto, no fim da lista
  const margens = useMemo(
    () => [...(dre?.margensPorProduto ?? [])].sort((a, b) => b.percentualMargem - a.percentualMargem),
    [dre]
  )
  const menorMargem = margens.length ? margens[margens.length - 1].percentualMargem : 0
  const minimoEixo = Math.max(PISO_EIXO, Math.floor(Math.min(0, menorMargem) / 10) * 10)
  const cortados = margens.filter(m => m.percentualMargem < PISO_EIXO).length
  const faixas = {
    verde: margens.filter(m => m.percentualMargem >= 30).length,
    amarelo: margens.filter(m => m.percentualMargem >= 15 && m.percentualMargem < 30).length,
    vermelho: margens.filter(m => m.percentualMargem < 15).length,
  }

  return (
    <div className="p-4 sm:p-6">
      <style>{`
        @media print {
          body * { visibility: hidden; }
          #dre-print-area, #dre-print-area * { visibility: visible; }
          #dre-print-area { position: absolute; top: 0; left: 0; width: 100%; }
        }
      `}</style>

      <PageHeader
        title="DRE Simplificado"
        subtitle="Demonstrativo de Resultado do Exercício"
        actions={
          <Button variant="secondary" disabled={!dre} onClick={() => window.print()}>
            <Printer size={16} /> Imprimir Relatório
          </Button>
        }
      />

      {/* Filtros — somem na impressão */}
      <Card className="flex flex-wrap items-end gap-3 sm:gap-4 mb-6 print:hidden">
        <Field label="Mês" className="flex-1 min-w-[8rem] sm:flex-none">
          <select value={mes} onChange={e => setMes(Number(e.target.value))} className={`${baseInputClass} sm:w-40`}>
            {Array.from({ length: 12 }, (_, i) => (
              <option key={i+1} value={i+1}>
                {new Date(2000, i).toLocaleString('pt-BR', { month: 'long' })}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Ano" className="w-24">
          <input type="number" inputMode="numeric" value={ano} onChange={e => setAno(Number(e.target.value))}
            className={`${baseInputClass} w-24`} />
        </Field>
        <Field label="Custos Op." className="flex-1 min-w-[8rem] sm:flex-none">
          <CurrencyInput value={opex} onChange={setOpex} className="w-full sm:w-36" />
        </Field>
        <Button variant="primary" onClick={() => refetch()} className="w-full sm:w-auto">Calcular</Button>
      </Card>

      {isLoading && <div className="text-center py-12 text-gray-500 text-sm">Calculando...</div>}

      {dre && (
        <div id="dre-print-area">
          {/* Cabeçalho só visível na impressão */}
          <div className="hidden print:block mb-4">
            <h1 className="text-xl font-bold text-gray-900">DRE Simplificado — {nomeMes}/{ano}</h1>
            <p className="text-xs text-gray-500">Gerado em {new Date().toLocaleString('pt-BR')}</p>
          </div>

          {/* Cards DRE */}
          <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-5 gap-3 sm:gap-4 mb-6 print:grid-cols-3 print:gap-2">
            {cards.map((c, i) => (
              <div key={i} className={`rounded-xl p-3 sm:p-4 min-w-0 ${c.bg} print:border print:border-gray-200 ${
                i === cards.length - 1 ? 'col-span-2 md:col-span-1' : ''}`}>
                <p className="text-xs font-medium text-gray-500 mb-1">{c.label}</p>
                <p className={`text-base sm:text-lg font-bold tabular-nums break-words ${c.cor}`}>
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
          <Card className="print:border print:shadow-none">
            <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 mb-3">
              <h2 className="font-semibold text-gray-900">Margem de Lucro por Produto</h2>
              <p className="text-xs text-gray-500">{margens.length} produtos · do maior para o menor</p>
            </div>

            {/* Resumo por faixa: dá a leitura geral sem precisar rolar o gráfico */}
            <div className="grid grid-cols-3 gap-2 mb-4 text-center">
              <div className="rounded-lg bg-success-50 py-2">
                <p className="text-lg font-bold text-success-600 tabular-nums">{faixas.verde}</p>
                <p className="text-[11px] text-gray-500">30% ou mais</p>
              </div>
              <div className="rounded-lg bg-warning-50 py-2">
                <p className="text-lg font-bold text-warning-600 tabular-nums">{faixas.amarelo}</p>
                <p className="text-[11px] text-gray-500">15% a 30%</p>
              </div>
              <div className="rounded-lg bg-danger-50 py-2">
                <p className="text-lg font-bold text-danger-600 tabular-nums">{faixas.vermelho}</p>
                <p className="text-[11px] text-gray-500">abaixo de 15%</p>
              </div>
            </div>

            {margens.length === 0 ? (
              <p className="text-center text-gray-400 py-8 text-sm">Nenhuma venda no período.</p>
            ) : (
              // Uma barra por produto, com altura fixa: a lista rola em vez de espremer os nomes
              <div className="max-h-[60vh] overflow-y-auto -mx-2 px-2 print:max-h-none print:overflow-visible">
                <ResponsiveContainer width="100%" height={margens.length * ALTURA_BARRA + 30}>
                  <BarChart data={margens} layout="vertical" barCategoryGap={4}
                    margin={{ top: 0, right: telaEstreita ? 8 : 20, left: 0, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                    <XAxis type="number" orientation="top" domain={[minimoEixo, 100]} allowDataOverflow
                      tickFormatter={v => `${Number(v).toFixed(0)}%`} tick={{ fontSize: 11 }} />
                    <YAxis type="category" dataKey="nomeProduto" interval={0}
                      width={telaEstreita ? 110 : 200}
                      tick={{ fontSize: telaEstreita ? 10 : 12 }}
                      tickFormatter={(n: string) => {
                        const max = telaEstreita ? 16 : 30
                        return n.length > max ? `${n.slice(0, max - 1)}…` : n
                      }} />
                    <Tooltip
                      cursor={{ fill: 'rgba(0,0,0,0.04)' }}
                      formatter={(v: number) => [`${v.toFixed(1).replace('.', ',')}%`, 'Margem']}
                    />
                    <Bar dataKey="percentualMargem" radius={[0, 4, 4, 0]} isAnimationActive={false}>
                      {margens.map((entry, i) => <Cell key={i} fill={corMargem(entry.percentualMargem)} />)}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}

            <p className="text-xs text-gray-400 mt-3">
              Verde: 30% ou mais • Amarelo: 15% a 30% • Vermelho: abaixo de 15%
              {cortados > 0 && (
                <> • <span className="text-danger-600">
                  {cortados} {cortados === 1 ? 'produto tem' : 'produtos têm'} margem abaixo de {PISO_EIXO}% (barra cortada no gráfico; confira o custo cadastrado)
                </span></>
              )}
            </p>
          </Card>
        </div>
      )}
    </div>
  )
}