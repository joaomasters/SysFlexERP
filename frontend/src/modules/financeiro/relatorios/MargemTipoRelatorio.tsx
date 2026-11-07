import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/api/axios'
import { formatBRL } from '@/shared/utils/mask'
import { Card, Table, THead, TH, TBody, TR, TD, EmptyState } from '@/shared/components/ui'
import { nomeTipoProduto } from '@/types/produto'
import PainelRelatorio, { BarraPercentual, usePeriodo, pct } from './PainelRelatorio'

interface MargemTipo {
  tipoProduto: string; faturamento: number; custo: number; margem: number
  percentualMargem: number; participacao: number; produtos: number
}

const corMargem = (m: number) => (m < 15 ? 'text-danger-600' : m < 30 ? 'text-warning-600' : 'text-success-600')

/** Quanto cada tipo de produto (cortes, bebidas, mercearia...) fatura e quanto deixa de margem. */
export default function MargemTipoRelatorio() {
  const p = usePeriodo()
  const { data = [], isLoading } = useQuery<MargemTipo[]>({
    queryKey: ['rel-margem-tipo', p.inicio, p.fim],
    queryFn: () => api.get(`/financeiro/relatorios/margem-tipo?${p.query}`).then(r => r.data),
    enabled: p.valido,
  })
  const total = data.reduce((s, x) => ({ fat: s.fat + x.faturamento, custo: s.custo + x.custo, margem: s.margem + x.margem }),
    { fat: 0, custo: 0, margem: 0 })

  return (
    <PainelRelatorio id="rel-margem-tipo-print" titulo="Margem por tipo de produto" descricaoImpressao={p.descricao} filtros={p.filtros}>
      <Card padding="none" className="overflow-hidden print:border print:shadow-none">
        <Table>
          <THead>
            <tr>
              <TH>Tipo</TH><TH align="right">Produtos</TH><TH align="right">Faturamento</TH><TH align="right">Custo</TH>
              <TH align="right">Margem</TH><TH align="right">Margem %</TH><TH align="right">Participação</TH>
              <TH><span className="sr-only">Proporção</span></TH>
            </tr>
          </THead>
          <TBody>
            {data.map(x => (
              <TR key={x.tipoProduto}>
                <TD className="font-medium">{nomeTipoProduto(x.tipoProduto)}</TD>
                <TD align="right" className="tabular-nums text-gray-600">{x.produtos}</TD>
                <TD align="right" className="tabular-nums">{formatBRL(x.faturamento)}</TD>
                <TD align="right" className="tabular-nums text-gray-600">{formatBRL(x.custo)}</TD>
                <TD align="right" className="tabular-nums">{formatBRL(x.margem)}</TD>
                <TD align="right" className={`tabular-nums font-medium ${corMargem(x.percentualMargem)}`}>{pct(x.percentualMargem)}</TD>
                <TD align="right" className="tabular-nums text-gray-600">{pct(x.participacao)}</TD>
                <TD className="w-1/6"><BarraPercentual valor={x.participacao} /></TD>
              </TR>
            ))}
            {data.length > 0 && (
              <TR className="bg-gray-50 font-semibold">
                <TD>Total</TD><TD /><TD align="right" className="tabular-nums">{formatBRL(total.fat)}</TD>
                <TD align="right" className="tabular-nums">{formatBRL(total.custo)}</TD>
                <TD align="right" className="tabular-nums">{formatBRL(total.margem)}</TD>
                <TD align="right" className="tabular-nums">{pct(total.fat ? (total.margem / total.fat) * 100 : 0)}</TD>
                <TD /><TD />
              </TR>
            )}
          </TBody>
        </Table>
        {!isLoading && data.length === 0 && <EmptyState>Nenhuma venda no período.</EmptyState>}
      </Card>
      <p className="text-xs text-gray-500">
        O custo é o preço de custo do produto no momento da venda. Produto sem custo cadastrado aparece com margem de 100%.
      </p>
    </PainelRelatorio>
  )
}
