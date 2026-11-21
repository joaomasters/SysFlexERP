import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, ChevronDown, ChevronRight } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { Field, baseInputClass, Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState, StatusBadge } from '@/shared/components/ui'
import { hojeISO } from '@/shared/utils/data'
import { STATUS_OP, type Carga } from '@/types/industrial'
import { horas } from '../comum'

const somarDias = (iso: string, dias: number) => {
  const d = new Date(`${iso}T12:00:00`)
  d.setDate(d.getDate() + dias)
  return d.toISOString().slice(0, 10)
}

const corOcupacao = (p?: number | null) =>
  p == null ? 'bg-gray-300' : p > 100 ? 'bg-danger-500' : p >= 85 ? 'bg-warning-500' : 'bg-success-500'

/**
 * Carga × capacidade por centro de trabalho. Capacidade vem dos turnos das máquinas;
 * carga, dos tempos previstos das OPs abertas no período. Só informa: não reprograma nada.
 */
export default function CargaAba() {
  const [de, setDe] = useState(hojeISO())
  const [ate, setAte] = useState(somarDias(hojeISO(), 13))
  const [abertos, setAbertos] = useState<Set<number>>(new Set())
  const valido = !!de && !!ate && ate >= de
  const { data, isLoading, error } = useQuery<Carga>({
    queryKey: ['ind-carga', de, ate], enabled: valido,
    queryFn: () => api.get('/industrial/pcp/carga', { params: { de, ate } }).then(r => r.data),
  })

  const alternar = (id: number) => setAbertos(s => {
    const n = new Set(s)
    if (n.has(id)) n.delete(id)
    else n.add(id)
    return n
  })

  return (
    <div>
      <div className="flex flex-wrap items-end gap-3 mb-4">
        <Field label="De" htmlFor="carga-de">
          <input id="carga-de" type="date" value={de} onChange={e => setDe(e.target.value)} className={baseInputClass} />
        </Field>
        <Field label="Até" htmlFor="carga-ate" hint="Até 92 dias">
          <input id="carga-ate" type="date" value={ate} min={de} onChange={e => setAte(e.target.value)} className={baseInputClass} />
        </Field>
      </div>

      {data && data.ordensSemRoteiro.length > 0 && (
        <div className="mb-4 rounded-lg border border-warning-200 bg-warning-50 p-3 text-sm text-warning-800 flex gap-2">
          <AlertTriangle size={16} className="shrink-0 mt-0.5" />
          <span>Fora da conta por não terem roteiro ativo: {data.ordensSemRoteiro.join(', ')}.</span>
        </div>
      )}

      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {!valido ? <EmptyState>Escolha um período válido.</EmptyState> : isLoading ? <LoadingState /> : error ? null : (
          <Table>
            <THead><tr><TH /><TH>Centro de trabalho</TH><TH align="right">Máquinas</TH><TH align="right">Capacidade</TH><TH align="right">Carga</TH><TH>Ocupação</TH></tr></THead>
            <TBody>
              {data?.centros.flatMap(c => {
                const aberto = abertos.has(c.centroId)
                const p = c.ocupacaoPercentual
                const linhas = [
                  <TR key={c.centroId}>
                    <TD className="w-8">
                      {c.ordens.length > 0 && (
                        <button type="button" aria-label={`${aberto ? 'Esconder' : 'Ver'} OPs de ${c.centroNome}`} onClick={() => alternar(c.centroId)} className="text-gray-500">
                          {aberto ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                        </button>
                      )}
                    </TD>
                    <TD><span className="font-medium">{c.centroNome}</span> <span className="text-xs text-gray-400 font-mono">{c.centroCodigo}</span></TD>
                    <TD align="right" className="tabular-nums">{c.maquinas}</TD>
                    <TD align="right" className="tabular-nums">{horas(c.capacidadeMin)}</TD>
                    <TD align="right" className="tabular-nums">{horas(c.cargaMin)}</TD>
                    <TD className="min-w-[180px]">
                      {p == null ? (
                        <span className="text-xs text-gray-500">{c.cargaMin > 0 ? 'Sem capacidade (sem máquinas ou turnos)' : '—'}</span>
                      ) : (
                        <div className="flex items-center gap-2">
                          <div className="flex-1 h-2 rounded bg-gray-100 overflow-hidden">
                            <div className={`h-full ${corOcupacao(p)}`} style={{ width: `${Math.min(100, Number(p))}%` }} />
                          </div>
                          <span className={`text-sm tabular-nums w-14 text-right ${Number(p) > 100 ? 'text-danger-600 font-semibold' : ''}`}>
                            {Number(p).toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%
                          </span>
                        </div>
                      )}
                    </TD>
                  </TR>,
                ]
                if (aberto) {
                  linhas.push(...c.ordens.map(o => (
                    <TR key={`${c.centroId}-${o.ordemId}`} className="bg-gray-50">
                      <TD />
                      <TD colSpan={3} className="text-sm">
                        <span className="font-mono">{o.numero}</span> · {o.produtoNome}{' '}
                        <StatusBadge tone={STATUS_OP[o.status].tom}>{STATUS_OP[o.status].rotulo}</StatusBadge>
                        {o.estimada && <span className="ml-1 text-xs text-gray-500">(estimada pelo roteiro ativo)</span>}
                      </TD>
                      <TD align="right" className="tabular-nums text-sm">{horas(o.minutos)}</TD>
                      <TD />
                    </TR>
                  )))
                }
                return linhas
              })}
            </TBody>
          </Table>
        )}
        {data && data.centros.length === 0 && <EmptyState>Nenhum centro de trabalho ativo.</EmptyState>}
      </div>
      <p className="text-xs text-gray-500 mt-2">
        Capacidade: turnos das máquinas não inativas de cada centro, nos dias marcados. Carga: tempo previsto das OPs abertas,
        rateado pelos dias da OP que caem no período. Não considera paradas nem eficiência.
      </p>
    </div>
  )
}
