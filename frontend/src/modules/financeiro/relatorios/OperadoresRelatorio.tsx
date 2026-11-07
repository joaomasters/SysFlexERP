import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/api/axios'
import { formatBRL } from '@/shared/utils/mask'
import { Card, Table, THead, TH, TBody, TR, TD, EmptyState } from '@/shared/components/ui'
import PainelRelatorio, { usePeriodo } from './PainelRelatorio'

interface Operador {
  operadorId: number; nome: string; vendas: number; total: number; ticketMedio: number; descontos: number; canceladas: number
}

/** Desempenho de cada operador de caixa, com os descontos dados e as vendas canceladas. */
export default function OperadoresRelatorio() {
  const p = usePeriodo()
  const { data = [], isLoading } = useQuery<Operador[]>({
    queryKey: ['rel-operadores', p.inicio, p.fim],
    queryFn: () => api.get(`/financeiro/relatorios/operadores?${p.query}`).then(r => r.data),
    enabled: p.valido,
  })

  return (
    <PainelRelatorio id="rel-operadores-print" titulo="Vendas por operador" descricaoImpressao={p.descricao} filtros={p.filtros}>
      <Card padding="none" className="overflow-hidden print:border print:shadow-none">
        <Table>
          <THead>
            <tr>
              <TH>Operador</TH><TH align="right">Vendas</TH><TH align="right">Total vendido</TH><TH align="right">Ticket médio</TH>
              <TH align="right">Descontos dados</TH><TH align="right">Canceladas</TH>
            </tr>
          </THead>
          <TBody>
            {data.map(o => (
              <TR key={o.operadorId}>
                <TD className="font-medium">{o.nome}</TD>
                <TD align="right" className="tabular-nums">{o.vendas}</TD>
                <TD align="right" className="tabular-nums">{formatBRL(o.total)}</TD>
                <TD align="right" className="tabular-nums">{formatBRL(o.ticketMedio)}</TD>
                <TD align="right" className={`tabular-nums ${o.descontos > 0 ? 'text-warning-600' : 'text-gray-500'}`}>
                  {formatBRL(o.descontos)}
                </TD>
                <TD align="right" className={`tabular-nums ${o.canceladas > 0 ? 'text-danger-600 font-medium' : 'text-gray-500'}`}>
                  {o.canceladas}
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
        {!isLoading && data.length === 0 && <EmptyState>Nenhuma venda no período.</EmptyState>}
      </Card>
    </PainelRelatorio>
  )
}
