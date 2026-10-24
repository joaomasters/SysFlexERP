import { useQuery } from '@tanstack/react-query'
import { Wallet, AlertOctagon, CalendarClock, CalendarRange } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { formatBRL } from '@/shared/utils/mask'
import { Card, Table, THead, TH, TBody, TR, TD, EmptyState } from '@/shared/components/ui'
import PainelRelatorio, { Indicador, BarraPercentual, pct } from './PainelRelatorio'

interface RelatorioContasPagar {
  totalAberto: number; totalVencido: number; aVencer7Dias: number; aVencer30Dias: number
  porCategoria: { categoria: string; total: number; percentual: number }[]
}

/** Posição das contas a pagar em aberto: vencidas, a vencer e por categoria (sem filtro de período). */
export default function ContasPagarPosicaoRelatorio() {
  const { data, isLoading } = useQuery<RelatorioContasPagar>({
    queryKey: ['rel-contas-pagar-posicao'],
    queryFn: () => api.get('/financeiro/relatorios/contas-a-pagar').then(r => r.data),
  })

  return (
    <PainelRelatorio id="rel-contas-pagar-posicao-print" titulo="Contas a Pagar — posição" descricaoImpressao="Contas em aberto hoje">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 print:grid-cols-4 print:gap-2">
        <Indicador label="Total em aberto" valor={formatBRL(data?.totalAberto ?? 0)}
          icone={<Wallet size={20} />} cor="text-info-600 bg-info-50" />
        <Indicador label="Vencido" valor={formatBRL(data?.totalVencido ?? 0)}
          icone={<AlertOctagon size={20} />}
          cor={(data?.totalVencido ?? 0) > 0 ? 'text-danger-600 bg-danger-50' : 'text-success-600 bg-success-50'} />
        <Indicador label="A vencer em 7 dias" valor={formatBRL(data?.aVencer7Dias ?? 0)}
          icone={<CalendarClock size={20} />} cor="text-warning-600 bg-warning-50" />
        <Indicador label="A vencer em 30 dias" valor={formatBRL(data?.aVencer30Dias ?? 0)}
          icone={<CalendarRange size={20} />} cor="text-warning-600 bg-warning-50" />
      </div>

      <Card padding="none" className="overflow-hidden print:border print:shadow-none">
        <div className="px-4 pt-4 pb-2">
          <h2 className="text-base font-semibold text-gray-700">Em aberto por categoria</h2>
        </div>
        <Table>
          <THead>
            <tr>
              <TH>Categoria</TH>
              <TH align="right">Total</TH>
              <TH align="right">Participação</TH>
              <TH><span className="sr-only">Proporção</span></TH>
            </tr>
          </THead>
          <TBody>
            {(data?.porCategoria ?? []).map(c => (
              <TR key={c.categoria}>
                <TD className="font-medium">{c.categoria || 'Sem categoria'}</TD>
                <TD align="right" className="tabular-nums">{formatBRL(c.total)}</TD>
                <TD align="right" className="tabular-nums text-gray-600">{pct(c.percentual)}</TD>
                <TD className="w-1/4"><BarraPercentual valor={c.percentual} tom="bg-warning-500" /></TD>
              </TR>
            ))}
          </TBody>
        </Table>
        {!isLoading && (data?.porCategoria?.length ?? 0) === 0 && <EmptyState>Nenhuma conta em aberto.</EmptyState>}
      </Card>
    </PainelRelatorio>
  )
}