import { useQuery } from '@tanstack/react-query'
import { Wallet, AlertOctagon, Percent } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { formatBRL } from '@/shared/utils/mask'
import { Card, Table, THead, TH, TBody, TR, TD, EmptyState } from '@/shared/components/ui'
import PainelRelatorio, { Indicador, BarraPercentual, pct } from './PainelRelatorio'

interface Inadimplencia {
  totalEmAberto: number; totalVencido: number
  faixas: { faixa: string; contas: number; valor: number }[]
  maioresDevedores: { clienteId: number; nome: string; contas: number; valor: number; vencido: number; vencimentoMaisAntigo: string | null }[]
}

/** Fiado e faturamentos em aberto hoje: quanto está atrasado, há quanto tempo e com quem. */
export default function InadimplenciaRelatorio() {
  const { data, isLoading } = useQuery<Inadimplencia>({
    queryKey: ['rel-inadimplencia'],
    queryFn: () => api.get('/financeiro/relatorios/inadimplencia').then(r => r.data),
  })
  const aberto = data?.totalEmAberto ?? 0
  const vencido = data?.totalVencido ?? 0

  return (
    <PainelRelatorio id="rel-inadimplencia-print" titulo="Contas a receber — inadimplência" descricaoImpressao="Contas em aberto hoje">
      <div className="grid grid-cols-3 gap-4 print:gap-2">
        <Indicador label="Total a receber" valor={formatBRL(aberto)} icone={<Wallet size={20} />} cor="text-info-600 bg-info-50" />
        <Indicador label="Vencido" valor={formatBRL(vencido)} icone={<AlertOctagon size={20} />}
          cor={vencido > 0 ? 'text-danger-600 bg-danger-50' : 'text-success-600 bg-success-50'} />
        <Indicador label="Inadimplência" valor={aberto > 0 ? pct((vencido / aberto) * 100) : '—'}
          detalhe="Parte do total a receber que já venceu" icone={<Percent size={20} />} cor="text-warning-600 bg-warning-50" />
      </div>

      <Card padding="none" className="overflow-hidden print:border print:shadow-none">
        <div className="px-4 pt-4 pb-2"><h2 className="text-base font-semibold text-gray-700">Por tempo de atraso</h2></div>
        <Table>
          <THead>
            <tr><TH>Atraso</TH><TH align="right">Contas</TH><TH align="right">Valor</TH><TH><span className="sr-only">Proporção</span></TH></tr>
          </THead>
          <TBody>
            {(data?.faixas ?? []).map(f => (
              <TR key={f.faixa}>
                <TD className="font-medium">{f.faixa}</TD>
                <TD align="right" className="tabular-nums">{f.contas}</TD>
                <TD align="right" className="tabular-nums">{formatBRL(f.valor)}</TD>
                <TD className="w-1/3">
                  <BarraPercentual valor={aberto ? (f.valor / aberto) * 100 : 0}
                    tom={f.faixa === 'A vencer' ? 'bg-success-500' : 'bg-danger-500'} />
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
      </Card>

      <Card padding="none" className="overflow-hidden print:border print:shadow-none">
        <div className="px-4 pt-4 pb-2"><h2 className="text-base font-semibold text-gray-700">Maiores devedores</h2></div>
        <Table>
          <THead>
            <tr>
              <TH>Cliente</TH><TH align="right">Contas</TH><TH align="right">Em aberto</TH><TH align="right">Vencido</TH>
              <TH>Vencimento mais antigo</TH>
            </tr>
          </THead>
          <TBody>
            {(data?.maioresDevedores ?? []).map(d => (
              <TR key={d.clienteId}>
                <TD className="font-medium">{d.nome}</TD>
                <TD align="right" className="tabular-nums">{d.contas}</TD>
                <TD align="right" className="tabular-nums">{formatBRL(d.valor)}</TD>
                <TD align="right" className={`tabular-nums ${d.vencido > 0 ? 'text-danger-600 font-medium' : 'text-gray-500'}`}>
                  {formatBRL(d.vencido)}
                </TD>
                <TD className="text-gray-600">
                  {d.vencimentoMaisAntigo ? new Date(`${d.vencimentoMaisAntigo}T12:00`).toLocaleDateString('pt-BR') : '—'}
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
        {!isLoading && (data?.maioresDevedores?.length ?? 0) === 0 && <EmptyState>Nenhuma conta em aberto.</EmptyState>}
      </Card>
    </PainelRelatorio>
  )
}
