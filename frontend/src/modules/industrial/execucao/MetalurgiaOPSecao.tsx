import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { Button, StatusBadge } from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { ETAPAS_RECEITA, TIPOS_PERDA, faixaTexto, type MetalurgiaOP } from '@/types/industrial'
import { dataHoraBR, num } from '../comum'
import { MODULO_APONTAMENTO } from './ExecucaoOPSecao'

/** Rendimento metálico, lotes de matéria-prima consumidos, medições de processo e perdas da OP. */
export default function MetalurgiaOPSecao({ ordemId }: { ordemId: number }) {
  const qc = useQueryClient()
  const { podeExcluir } = usePermissao()
  const { data: m } = useQuery<MetalurgiaOP>({
    queryKey: ['ind-metalurgia', ordemId], queryFn: () => api.get(`/industrial/ordens/${ordemId}/metalurgia`).then(r => r.data),
  })
  const estornar = useMutation({
    mutationFn: ({ id, motivo }: { id: number; motivo: string }) => api.post(`/industrial/perdas/${id}/estornar`, { motivo }),
    onSuccess: () => { toast.success('Perda estornada.'); qc.invalidateQueries({ queryKey: ['ind-metalurgia', ordemId] }) },
  })
  if (!m) return null
  const vazio = !m.medicoes.length && !m.perdas.length && !m.lotesConsumidos.length && !m.rendimento.entradaKg
  if (vazio) return null
  const r = m.rendimento

  return (
    <div className="space-y-3">
      <h4 className="text-xs font-semibold text-gray-500 uppercase">Metalurgia</h4>
      <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 text-sm">
        <Caixa rotulo="Metal consumido" valor={r.entradaKg == null ? '—' : `${num(r.entradaKg, 1)} kg`} />
        <Caixa rotulo="Produzido" valor={r.produzidoKg == null ? '—' : `${num(r.produzidoKg, 1)} kg`} />
        <Caixa rotulo="Perdas registradas" valor={`${num(r.perdasKg, 1)} kg`} />
        <Caixa rotulo="Não apurado" valor={r.naoApuradoKg == null ? '—' : `${num(r.naoApuradoKg, 1)} kg`} />
        <Caixa rotulo="Rendimento" valor={r.rendimentoPercentual == null ? '—' : `${num(r.rendimentoPercentual, 1)}%`} destaque />
      </div>
      {r.avisos.length > 0 && <ul className="text-xs text-warning-700 list-disc pl-5">{r.avisos.map(a => <li key={a}>{a}</li>)}</ul>}

      {m.lotesConsumidos.length > 0 && (
        <p className="text-sm text-gray-600">
          Lotes de matéria-prima: {m.lotesConsumidos.map(l => `${l.numero} (${l.ligaCodigo}, ${num(l.quantidade, 1)})`).join(' · ')}
        </p>
      )}

      {m.medicoes.length > 0 && (
        <ul className="divide-y divide-gray-100 text-sm">
          {m.medicoes.map(md => (
            <li key={md.id} className="py-2">
              <div className="flex flex-wrap items-center gap-2">
                {md.foraEspecificacao ? <StatusBadge tone="danger">Fora da faixa</StatusBadge> : <StatusBadge tone="success">OK</StatusBadge>}
                <span>{ETAPAS_RECEITA[md.etapa]} · <span className="font-mono">{md.receitaCodigo}</span> rev. {md.receitaRevisao}</span>
                <span className="text-gray-400 tabular-nums">{dataHoraBR(md.medidoEm)} · {md.usuarioNome ?? ''}{md.maquinaCodigo ? ` · ${md.maquinaCodigo}` : ''}</span>
              </div>
              <div className="text-xs mt-1">
                {md.valores.map(v => (
                  <span key={v.codigo} className={`mr-3 ${v.dentroFaixa ? 'text-gray-600' : 'text-danger-700 font-semibold'}`}>
                    {v.nome}: {num(v.valor, 4)}{v.unidade ? ` ${v.unidade}` : ''} ({faixaTexto(v.minimo, v.maximo)})
                  </span>
                ))}
              </div>
              {md.observacao && <div className="text-xs text-gray-500 italic">{md.observacao}</div>}
            </li>
          ))}
        </ul>
      )}

      {m.perdas.length > 0 && (
        <ul className="text-sm space-y-1">
          {m.perdas.map(p => (
            <li key={p.id} className={`flex flex-wrap items-center gap-x-3 ${p.estornada ? 'opacity-60 line-through' : ''}`}>
              <span>{TIPOS_PERDA[p.tipo]}: <b className="tabular-nums">{num(p.quantidadeKg, 2)} kg</b></span>
              <span className="text-gray-400 tabular-nums">{dataHoraBR(p.createdAt)} · {p.usuarioNome ?? ''}</span>
              {!p.estornada && podeExcluir(MODULO_APONTAMENTO) && (
                <Button size="sm" variant="ghost" className="ml-auto no-underline" onClick={() => {
                  const motivo = window.prompt('Motivo do estorno da perda:')
                  if (motivo?.trim()) estornar.mutate({ id: p.id, motivo: motivo.trim() })
                }}>Estornar</Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function Caixa({ rotulo, valor, destaque }: { rotulo: string; valor: string; destaque?: boolean }) {
  return (
    <div className={`rounded-lg p-2 ${destaque ? 'bg-primary-50' : 'bg-gray-50'}`}>
      <div className="text-xs text-gray-500">{rotulo}</div>
      <div className="font-semibold tabular-nums">{valor}</div>
    </div>
  )
}
