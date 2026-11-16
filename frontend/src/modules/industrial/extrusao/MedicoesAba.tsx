import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/api/axios'
import { Field, baseInputClass, StatusBadge, Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState } from '@/shared/components/ui'
import { hojeISO } from '@/shared/utils/data'
import { ETAPAS_RECEITA, faixaTexto, type Medicao } from '@/types/industrial'
import { dataHoraBR, num } from '../comum'

/** Consulta das medições de processo, com filtro das que saíram da faixa da receita. */
export default function MedicoesAba() {
  const [de, setDe] = useState(hojeISO())
  const [ate, setAte] = useState(hojeISO())
  const [somenteFora, setSomenteFora] = useState(false)
  const valido = !!de && !!ate && ate >= de
  const { data = [], isLoading } = useQuery<Medicao[]>({
    queryKey: ['ind-medicoes', de, ate, somenteFora], enabled: valido,
    queryFn: () => api.get('/industrial/medicoes', { params: { de, ate, somenteFora } }).then(r => r.data),
  })

  return (
    <div>
      <div className="flex flex-wrap items-end gap-3 mb-4">
        <Field label="De" htmlFor="med-de"><input id="med-de" type="date" value={de} onChange={e => setDe(e.target.value)} className={baseInputClass} /></Field>
        <Field label="Até" htmlFor="med-ate"><input id="med-ate" type="date" value={ate} min={de} onChange={e => setAte(e.target.value)} className={baseInputClass} /></Field>
        <label className="flex items-center gap-2 text-sm text-gray-700 pb-2">
          <input type="checkbox" checked={somenteFora} onChange={e => setSomenteFora(e.target.checked)} /> Só fora de especificação
        </label>
      </div>
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>Quando</TH><TH>OP · máquina</TH><TH>Receita</TH><TH>Valores</TH><TH>Resultado</TH></tr></THead>
            <TBody>
              {data.map(m => (
                <TR key={m.id}>
                  <TD className="tabular-nums text-sm whitespace-nowrap">{dataHoraBR(m.medidoEm)}<div className="text-xs text-gray-500">{m.usuarioNome ?? ''} · {m.origem === 'SENSOR' ? 'sensor' : 'manual'}</div></TD>
                  <TD className="text-sm"><span className="font-mono">{m.ordemNumero}</span>{m.maquinaCodigo ? ` · ${m.maquinaCodigo}` : ''}</TD>
                  <TD className="text-sm"><span className="font-mono">{m.receitaCodigo}</span> rev. {m.receitaRevisao}<div className="text-xs text-gray-500">{ETAPAS_RECEITA[m.etapa]}</div></TD>
                  <TD className="text-xs">
                    {m.valores.map(v => (
                      <div key={v.codigo} className={v.dentroFaixa ? 'text-gray-700' : 'text-danger-700 font-semibold'}>
                        {v.nome}: {num(v.valor, 4)}{v.unidade ? ` ${v.unidade}` : ''} <span className="text-gray-400 font-normal">({faixaTexto(v.minimo, v.maximo)})</span>
                      </div>
                    ))}
                    {m.observacao && <div className="text-gray-500 italic mt-1">{m.observacao}</div>}
                  </TD>
                  <TD>{m.foraEspecificacao ? <StatusBadge tone="danger">Fora da faixa</StatusBadge> : <StatusBadge tone="success">OK</StatusBadge>}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && data.length === 0 && <EmptyState>Nenhuma medição no período.</EmptyState>}
      </div>
    </div>
  )
}
