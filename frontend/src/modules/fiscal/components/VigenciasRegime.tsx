import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { Button, Card, Field, baseInputClass, Table, THead, TH, TBody, TR, TD } from '@/shared/components/ui'
import { REGIMES_TRIBUTARIOS } from '@/types/fiscal'

/*
 Regime tributário por vigência: a apuração de um mês usa o regime daquele mês. Vigências nunca
 são editadas; uma nova encerra a aberta na véspera (backend: EnquadramentoService).
*/

interface Vigencia { id: number; regimeTributario: number; inicio: string; fim: string | null; observacao: string | null }

const rotulo = (r: number) => REGIMES_TRIBUTARIOS.find(x => x.value === r)?.label ?? `Regime ${r}`
const data = (d: string) => new Date(`${d}T00:00:00`).toLocaleDateString('pt-BR')

export default function VigenciasRegime() {
  const qc = useQueryClient()
  const { podeEditar } = usePermissao()
  const [regime, setRegime] = useState(1)
  const [inicio, setInicio] = useState('')
  const [observacao, setObservacao] = useState('')

  const { data: vigencias = [] } = useQuery<Vigencia[]>({
    queryKey: ['fiscal-enquadramentos'],
    queryFn: () => api.get('/fiscal/enquadramentos').then(r => r.data),
  })

  const registrar = useMutation({
    mutationFn: () => api.post('/fiscal/enquadramentos', { regimeTributario: regime, inicio, observacao }),
    onSuccess: () => {
      toast.success('Nova vigência registrada.')
      setInicio('')
      setObservacao('')
      qc.invalidateQueries({ queryKey: ['fiscal-enquadramentos'] })
      qc.invalidateQueries({ queryKey: ['fiscal-configuracao'] })
    },
  })

  return (
    <Card padding="md" className="space-y-4">
      <div>
        <h2 className="text-sm font-semibold text-gray-800">Vigências do regime tributário</h2>
        <p className="text-xs text-gray-500">
          O regime vale por período: a apuração de um mês usa o regime daquele mês. Uma vigência nova encerra a atual na
          véspera; vigências passadas não são alteradas.
        </p>
      </div>
      <Table>
        <THead><TH>Regime</TH><TH>Início</TH><TH>Fim</TH><TH>Observação</TH></THead>
        <TBody>
          {vigencias.map(v => (
            <TR key={v.id}>
              <TD>{rotulo(v.regimeTributario)}</TD>
              <TD className="tabular-nums">{data(v.inicio)}</TD>
              <TD className="tabular-nums">{v.fim ? data(v.fim) : 'em vigor'}</TD>
              <TD className="text-xs text-gray-500">{v.observacao ?? '—'}</TD>
            </TR>
          ))}
        </TBody>
      </Table>
      {podeEditar('FISCAL') && (
        <div className="grid gap-3 md:grid-cols-4 md:items-end">
          <Field label="Novo regime" htmlFor="vigencia-regime">
            <select id="vigencia-regime" value={regime} onChange={e => setRegime(Number(e.target.value))} className={baseInputClass}>
              {REGIMES_TRIBUTARIOS.map(r => <option key={r.value} value={r.value}>{r.label}</option>)}
            </select>
          </Field>
          <Field label="A partir de" htmlFor="vigencia-inicio">
            <input id="vigencia-inicio" type="date" value={inicio} onChange={e => setInicio(e.target.value)} className={baseInputClass} />
          </Field>
          <Field label="Observação" htmlFor="vigencia-obs">
            <input id="vigencia-obs" value={observacao} maxLength={255} onChange={e => setObservacao(e.target.value)}
                   placeholder="Ex.: desenquadramento do Simples" className={baseInputClass} />
          </Field>
          <Button variant="secondary" disabled={!inicio} loading={registrar.isPending} onClick={() => registrar.mutate()}>
            Registrar vigência
          </Button>
        </div>
      )}
    </Card>
  )
}
