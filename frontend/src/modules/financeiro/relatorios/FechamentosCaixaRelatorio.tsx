import { useQuery } from '@tanstack/react-query'
import { Scale, TrendingUp, TrendingDown } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { formatBRL } from '@/shared/utils/mask'
import { Card, Table, THead, TH, TBody, TR, TD, EmptyState } from '@/shared/components/ui'
import PainelRelatorio, { Indicador, usePeriodo } from './PainelRelatorio'

interface Fechamento {
  caixaId: number; operador: string | null; abertura: string; fechamento: string
  esperado: number | null; informado: number | null; diferenca: number | null
}

const quando = (iso: string) => new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

/** Conferência dos fechamentos de caixa: o que foi contado na gaveta contra o esperado. */
export default function FechamentosCaixaRelatorio() {
  const p = usePeriodo()
  const { data = [], isLoading } = useQuery<Fechamento[]>({
    queryKey: ['rel-fechamentos-caixa', p.inicio, p.fim],
    queryFn: () => api.get(`/financeiro/relatorios/fechamentos-caixa?${p.query}`).then(r => r.data),
    enabled: p.valido,
  })
  const sobras = data.reduce((s, f) => s + Math.max(0, f.diferenca ?? 0), 0)
  const faltas = data.reduce((s, f) => s + Math.min(0, f.diferenca ?? 0), 0)

  return (
    <PainelRelatorio id="rel-fechamentos-print" titulo="Fechamentos de caixa (sobras e faltas)" descricaoImpressao={p.descricao} filtros={p.filtros}>
      <div className="grid grid-cols-3 gap-4 print:gap-2">
        <Indicador label="Caixas fechados" valor={data.length} icone={<Scale size={20} />} cor="text-info-600 bg-info-50" />
        <Indicador label="Total de sobras" valor={formatBRL(sobras)} icone={<TrendingUp size={20} />} cor="text-success-600 bg-success-50" />
        <Indicador label="Total de faltas" valor={formatBRL(Math.abs(faltas))} icone={<TrendingDown size={20} />}
          cor={faltas < 0 ? 'text-danger-600 bg-danger-50' : 'text-gray-500 bg-gray-100'} />
      </div>

      <Card padding="none" className="overflow-hidden print:border print:shadow-none">
        <Table>
          <THead>
            <tr>
              <TH>Caixa</TH><TH>Operador</TH><TH>Aberto em</TH><TH>Fechado em</TH>
              <TH align="right">Esperado</TH><TH align="right">Contado</TH><TH align="right">Diferença</TH>
            </tr>
          </THead>
          <TBody>
            {data.map(f => (
              <TR key={f.caixaId}>
                <TD className="tabular-nums">#{f.caixaId}</TD>
                <TD className="font-medium">{f.operador ?? '—'}</TD>
                <TD className="text-gray-600">{quando(f.abertura)}</TD>
                <TD className="text-gray-600">{quando(f.fechamento)}</TD>
                <TD align="right" className="tabular-nums">{f.esperado == null ? '—' : formatBRL(f.esperado)}</TD>
                <TD align="right" className="tabular-nums">{f.informado == null ? '—' : formatBRL(f.informado)}</TD>
                <TD align="right" className={`tabular-nums font-semibold ${
                  (f.diferenca ?? 0) < 0 ? 'text-danger-600' : (f.diferenca ?? 0) > 0 ? 'text-success-600' : 'text-gray-500'}`}>
                  {f.diferenca == null ? '—' : `${f.diferenca > 0 ? '+' : ''}${formatBRL(f.diferenca)}`}
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
        {!isLoading && data.length === 0 && <EmptyState>Nenhum caixa fechado no período.</EmptyState>}
      </Card>
    </PainelRelatorio>
  )
}
