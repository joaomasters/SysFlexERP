import { useQuery } from '@tanstack/react-query'
import { Clock, CalendarDays, Sun } from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { api } from '@/shared/api/axios'
import { formatBRL } from '@/shared/utils/mask'
import { Card, Table, THead, TH, TBody, TR, TD } from '@/shared/components/ui'
import PainelRelatorio, { Indicador, usePeriodo } from './PainelRelatorio'

interface Faixa { chave: number; quantidade: number; total: number }
interface Horarios { porHora: Faixa[]; porDiaSemana: Faixa[] }

const DIAS = ['', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo']
const hora = (h: number) => `${String(h).padStart(2, '0')}h`

/** Quando a loja vende: por hora do dia e por dia da semana (escala de caixas, horário de reposição). */
export default function HorariosRelatorio() {
  const p = usePeriodo()
  const { data } = useQuery<Horarios>({
    queryKey: ['rel-horarios', p.inicio, p.fim],
    queryFn: () => api.get(`/financeiro/relatorios/horarios?${p.query}`).then(r => r.data),
    enabled: p.valido,
  })

  // Só o horário de funcionamento: horas sem nenhuma venda nas pontas saem do gráfico
  const horas = data?.porHora ?? []
  const comVenda = horas.filter(h => h.quantidade > 0)
  const primeira = comVenda[0]?.chave ?? 0
  const ultima = comVenda[comVenda.length - 1]?.chave ?? 23
  const porHora = horas.filter(h => h.chave >= primeira && h.chave <= ultima).map(h => ({ ...h, rotulo: hora(h.chave) }))
  const porDia = (data?.porDiaSemana ?? []).map(d => ({ ...d, rotulo: DIAS[d.chave].slice(0, 3) }))
  const pico = comVenda.reduce<Faixa | null>((m, h) => (!m || h.total > m.total ? h : m), null)
  const melhorDia = (data?.porDiaSemana ?? []).reduce<Faixa | null>((m, d) => (!m || d.total > m.total ? d : m), null)

  return (
    <PainelRelatorio id="rel-horarios-print" titulo="Vendas por horário e dia da semana" descricaoImpressao={p.descricao} filtros={p.filtros}>
      <div className="grid grid-cols-2 lg:grid-cols-3 gap-4 print:grid-cols-3 print:gap-2">
        <Indicador label="Horário de pico" valor={pico ? hora(pico.chave) : '—'}
          detalhe={pico ? `${formatBRL(pico.total)} em ${pico.quantidade} vendas` : undefined}
          icone={<Clock size={20} />} cor="text-primary-600 bg-primary-50" />
        <Indicador label="Melhor dia da semana" valor={melhorDia && melhorDia.total > 0 ? DIAS[melhorDia.chave] : '—'}
          detalhe={melhorDia && melhorDia.total > 0 ? formatBRL(melhorDia.total) : undefined}
          icone={<CalendarDays size={20} />} cor="text-info-600 bg-info-50" />
        <Indicador label="Horário de funcionamento com vendas" valor={comVenda.length ? `${hora(primeira)} às ${hora(ultima + 1)}` : '—'}
          icone={<Sun size={20} />} cor="text-warning-600 bg-warning-50" />
      </div>

      {[
        { titulo: 'Vendas por hora do dia', dados: porHora, nome: (f: Faixa) => hora(f.chave) },
        { titulo: 'Vendas por dia da semana', dados: porDia, nome: (f: Faixa) => DIAS[f.chave] },
      ].map(bloco => (
        <Card key={bloco.titulo} className="print:border print:shadow-none">
          <h2 className="text-base font-semibold text-gray-700 mb-4">{bloco.titulo}</h2>
          <div className="h-64 print:hidden">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={bloco.dados} margin={{ left: 10, right: 10, top: 5, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="rotulo" tick={{ fontSize: 11 }} />
                <YAxis tickFormatter={n => `R$${Number(n).toFixed(0)}`} tick={{ fontSize: 11 }} width={70} />
                <Tooltip formatter={(n: number) => formatBRL(n)} />
                <Bar dataKey="total" name="Vendas" fill="#A32B1E" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
          {/* O gráfico não sai bem no papel: na impressão vai a tabela */}
          <div className="hidden print:block">
            <Table>
              <THead><tr><TH>Quando</TH><TH align="right">Vendas</TH><TH align="right">Total</TH></tr></THead>
              <TBody>
                {bloco.dados.map(f => (
                  <TR key={f.chave}>
                    <TD>{bloco.nome(f)}</TD>
                    <TD align="right" className="tabular-nums">{f.quantidade}</TD>
                    <TD align="right" className="tabular-nums">{formatBRL(f.total)}</TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          </div>
        </Card>
      ))}
    </PainelRelatorio>
  )
}
