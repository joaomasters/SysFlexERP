import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { TrendingUp, TrendingDown, ShoppingBag, BarChart2, Package, Percent, Wallet } from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { api } from '@/shared/api/axios'
import { formatBRL, formatWeightDisplay } from '@/shared/utils/mask'
import { Card, Table, THead, TH, TBody, TR, TD } from '@/shared/components/ui'
import PainelRelatorio, { periodoPadrao, dataBR, Indicador, BarraPercentual, FiltroData, pct } from './PainelRelatorio'

interface RelatorioVendas {
  totalVendas: number; quantidadeVendas: number; ticketMedio: number; totalPerdas: number
  totalCMV: number; margemBruta: number; percentualMargem: number
  topProdutos: { produtoId: number; nomeProduto: string; quantidadeTotal: number; valorTotal: number }[]
  porFormaPagamento: { formaPagamento: string; totalValor: number; percentual: number }[]
  vendasPorDia: { data: string; quantidadeVendas: number; totalVendas: number }[]
}
interface FluxoCaixa { totalRecebimentos: number; totalPagamentos: number; saldo: number }

const fmt = formatBRL

const rotuloForma: Record<string, string> = {
  DINHEIRO: 'Dinheiro', PIX: 'PIX', DEBITO: 'Cartão de débito', CREDITO: 'Cartão de crédito',
  CARTAO_DEBITO: 'Cartão de débito', CARTAO_CREDITO: 'Cartão de crédito', FIADO: 'Fiado / a prazo',
}
const nomeForma = (f: string) => rotuloForma[f] ?? f

/** Visão geral: vendas, margem, formas de pagamento, vendas por dia, top produtos e fluxo de caixa. */
export default function DesempenhoRelatorio() {
  const padrao = periodoPadrao()
  const [inicio, setInicio] = useState(padrao.inicio)
  const [fim, setFim] = useState(padrao.fim)
  const periodoValido = !!inicio && !!fim && inicio <= fim

  const vendas = useQuery<RelatorioVendas>({
    queryKey: ['rel-vendas', inicio, fim],
    queryFn: () => api.get(`/financeiro/relatorios/vendas?inicio=${inicio}&fim=${fim}`).then(r => r.data),
    enabled: periodoValido,
  })
  const fluxo = useQuery<FluxoCaixa>({
    queryKey: ['fluxo-caixa', inicio, fim],
    queryFn: () => api.get(`/financeiro/relatorios/fluxo-caixa?inicio=${inicio}&fim=${fim}`).then(r => r.data),
    enabled: periodoValido,
  })

  const v = vendas.data
  const saldoPositivo = (fluxo.data?.saldo ?? 0) >= 0
  const margemPositiva = (v?.margemBruta ?? 0) >= 0
  const porDia = (v?.vendasPorDia ?? []).map(d => ({ ...d, rotulo: dataBR(d.data).slice(0, 5) }))

  return (
    <PainelRelatorio
      id="rel-desempenho-print"
      titulo="Relatório de Desempenho"
      descricaoImpressao={periodoValido ? `Período: ${dataBR(inicio)} a ${dataBR(fim)}` : undefined}
      filtros={
        <>
          <FiltroData label="De" value={inicio} onChange={setInicio} />
          <FiltroData label="Até" value={fim} onChange={setFim} erro={!periodoValido ? 'Período inválido.' : undefined} />
        </>
      }
    >
      {/* Indicadores */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 print:grid-cols-4 print:gap-2">
        <Indicador label="Total de Vendas" valor={fmt(v?.totalVendas ?? 0)} icone={<TrendingUp size={20} />} cor="text-success-600 bg-success-50" />
        <Indicador label="Qtd. Vendas" valor={v?.quantidadeVendas ?? 0} icone={<ShoppingBag size={20} />} cor="text-info-600 bg-info-50" />
        <Indicador label="Ticket Médio" valor={fmt(v?.ticketMedio ?? 0)} icone={<BarChart2 size={20} />} cor="text-purple-600 bg-purple-50" />
        <Indicador label="Total Perdas" valor={fmt(v?.totalPerdas ?? 0)} icone={<TrendingDown size={20} />} cor="text-danger-600 bg-danger-50" />
        <Indicador label="Custo das mercadorias vendidas (CMV)" valor={fmt(v?.totalCMV ?? 0)} icone={<Package size={20} />} cor="text-warning-600 bg-warning-50" />
        <Indicador
          label="Margem bruta" valor={fmt(v?.margemBruta ?? 0)}
          icone={<Wallet size={20} />}
          cor={margemPositiva ? 'text-success-600 bg-success-50' : 'text-danger-600 bg-danger-50'}
        />
        <Indicador
          label="Margem sobre vendas" valor={pct(v?.percentualMargem ?? 0)}
          icone={<Percent size={20} />}
          cor={margemPositiva ? 'text-success-600 bg-success-50' : 'text-danger-600 bg-danger-50'}
        />
        <Indicador
          label="Perdas sobre vendas"
          valor={(v?.totalVendas ?? 0) > 0 ? pct(((v?.totalPerdas ?? 0) / (v?.totalVendas ?? 1)) * 100) : '—'}
          icone={<TrendingDown size={20} />} cor="text-danger-600 bg-danger-50"
        />
      </div>

      {/* Fluxo de Caixa */}
      <Card className="print:border print:shadow-none">
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

      {/* Vendas por forma de pagamento */}
      {v && v.porFormaPagamento?.length > 0 && (
        <Card className="print:border print:shadow-none">
          <h2 className="text-base font-semibold text-gray-700 mb-2">Vendas por forma de pagamento</h2>
          <Table>
            <THead>
              <tr>
                <TH>Forma</TH>
                <TH align="right">Valor</TH>
                <TH align="right">Participação</TH>
                <TH><span className="sr-only">Proporção</span></TH>
              </tr>
            </THead>
            <TBody>
              {v.porFormaPagamento.map(f => (
                <TR key={f.formaPagamento}>
                  <TD className="font-medium">{nomeForma(f.formaPagamento)}</TD>
                  <TD align="right" className="tabular-nums">{fmt(f.totalValor)}</TD>
                  <TD align="right" className="tabular-nums text-gray-600">{pct(f.percentual)}</TD>
                  <TD className="w-1/4"><BarraPercentual valor={f.percentual} /></TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </Card>
      )}

      {/* Vendas por dia */}
      {porDia.length > 0 && (
        <Card className="print:border print:shadow-none">
          <h2 className="text-base font-semibold text-gray-700 mb-4">Vendas por dia</h2>
          <div className="h-64 print:hidden">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={porDia} margin={{ left: 10, right: 10, top: 5, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="rotulo" tick={{ fontSize: 11 }} />
                <YAxis tickFormatter={n => `R$${Number(n).toFixed(0)}`} tick={{ fontSize: 11 }} width={70} />
                <Tooltip formatter={(n: number) => fmt(n)} labelFormatter={l => `Dia ${l}`} />
                <Bar dataKey="totalVendas" name="Vendas" fill="#A32B1E" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
          {/* Tabela: o gráfico (SVG) não sai bem no papel, a tabela sim. */}
          <div className="hidden print:block">
            <Table>
              <THead>
                <tr><TH>Dia</TH><TH align="right">Vendas</TH><TH align="right">Valor</TH></tr>
              </THead>
              <TBody>
                {porDia.map(d => (
                  <TR key={d.data}>
                    <TD>{dataBR(d.data)}</TD>
                    <TD align="right" className="tabular-nums">{d.quantidadeVendas}</TD>
                    <TD align="right" className="tabular-nums">{fmt(d.totalVendas)}</TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          </div>
        </Card>
      )}

      {/* Top 10 produtos */}
      {v && v.topProdutos?.length > 0 && (
        <Card className="print:border print:shadow-none">
          <h2 className="text-base font-semibold text-gray-700 mb-4 flex items-center gap-2">
            <Package size={18} className="text-gray-500" />
            Top 10 Produtos por Faturamento
          </h2>
          <div className="h-72 print:hidden">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={v.topProdutos} layout="vertical" margin={{ left: 20, right: 20, top: 5, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                <XAxis type="number" tickFormatter={n => `R$${Number(n).toFixed(0)}`} tick={{ fontSize: 11 }} />
                <YAxis type="category" dataKey="nomeProduto" width={120} tick={{ fontSize: 11 }} />
                <Tooltip formatter={(n: number) => fmt(n)} />
                <Bar dataKey="valorTotal" name="Faturamento" fill="#A32B1E" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
          <Table>
            <THead>
              <tr><TH>#</TH><TH>Produto</TH><TH align="right">Qtd Vendida</TH><TH align="right">Faturamento</TH></tr>
            </THead>
            <TBody>
              {v.topProdutos.map((p, i) => (
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
    </PainelRelatorio>
  )
}