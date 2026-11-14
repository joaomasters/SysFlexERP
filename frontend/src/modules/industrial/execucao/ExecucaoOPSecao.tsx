import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { Button, Field, Modal, StatusBadge, Table, THead, TH, TBody, TR, TD, baseInputClass } from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { STATUS_APONTAMENTO, type ExecucaoOP } from '@/types/industrial'
import { dataHoraBR, horas, num } from '../comum'
import ConsumoModal from './ConsumoModal'

export const MODULO_APONTAMENTO = 'IND_APONTAMENTO'

/** Andamento da OP para o PCP: produzido, operações (com WIP), materiais, apontamentos, consumos e paradas. */
export default function ExecucaoOPSecao({ ordemId }: { ordemId: number }) {
  const qc = useQueryClient()
  const { podeCriar, podeExcluir } = usePermissao()
  const [consumindo, setConsumindo] = useState(false)
  const [estorno, setEstorno] = useState<{ tipo: 'apontamento' | 'consumo'; id: number; titulo: string; motivo: string } | null>(null)
  const { data: e } = useQuery<ExecucaoOP>({
    queryKey: ['ind-execucao', ordemId], queryFn: () => api.get(`/industrial/ordens/${ordemId}/execucao`).then(r => r.data),
  })
  const estornar = useMutation({
    mutationFn: () => api.post(`/industrial/${estorno!.tipo === 'apontamento' ? 'apontamentos' : 'consumos'}/${estorno!.id}/estornar`, { motivo: estorno!.motivo }),
    onSuccess: () => {
      toast.success('Estornado.')
      qc.invalidateQueries({ queryKey: ['ind-execucao', ordemId] })
      qc.invalidateQueries({ queryKey: ['ind-ordem', ordemId] })
      qc.invalidateQueries({ queryKey: ['ind-ordens'] })
      setEstorno(null)
    },
  })
  if (!e) return null
  const temExecucao = e.apontamentos.length > 0 || e.consumos.length > 0

  return (
    <section className="space-y-4 border-t border-gray-100 pt-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-sm font-semibold text-gray-700">Execução</h3>
        {podeCriar(MODULO_APONTAMENTO) && ['LIBERADA', 'EM_PRODUCAO', 'PAUSADA', 'CONCLUIDA'].includes(e.status) && (
          <Button size="sm" variant="secondary" onClick={() => setConsumindo(true)}>Registrar consumo</Button>
        )}
      </div>
      <div>
        <div className="flex justify-between text-sm mb-1">
          <span>Produzido: <b className="tabular-nums">{num(e.produzido)} de {num(e.quantidade)} {e.unidade}</b> ({num(e.percentualProduzido, 1)}%)</span>
          {e.refugo > 0 && <span className="text-danger-600 tabular-nums">Refugo: {num(e.refugo)}</span>}
        </div>
        <div className="h-2 rounded bg-gray-100 overflow-hidden">
          <div className="h-full bg-success-500" style={{ width: `${Math.min(100, e.percentualProduzido)}%` }} />
        </div>
      </div>

      {!temExecucao ? <p className="text-sm text-gray-500">Nenhum apontamento ou consumo ainda.</p> : (
        <>
          <Table>
            <THead><tr><TH>Operação</TH><TH align="right">Boa</TH><TH align="right">Refugo</TH><TH align="right">Aguardando próxima</TH><TH align="right">Tempo real / previsto</TH></tr></THead>
            <TBody>
              {e.operacoes.map(o => (
                <TR key={o.operacaoId}>
                  <TD><span className="tabular-nums text-gray-400 mr-1">{o.sequencia}</span>{o.descricao}
                    {o.apontamentosAbertos > 0 && <StatusBadge tone="info" className="ml-2">em andamento</StatusBadge>}
                    {o.status === 'CONCLUIDA' && <StatusBadge tone="success" className="ml-2">concluída</StatusBadge>}</TD>
                  <TD align="right" className="tabular-nums">{num(o.quantidadeBoa)}</TD>
                  <TD align="right" className="tabular-nums">{num(o.quantidadeRefugo)}</TD>
                  <TD align="right" className="tabular-nums">{o.emProcesso == null ? '—' : num(o.emProcesso)}</TD>
                  <TD align="right" className={`tabular-nums ${o.minutosTrabalhados > o.tempoPrevistoMin ? 'text-warning-700' : ''}`}>
                    {horas(o.minutosTrabalhados)} / {horas(o.tempoPrevistoMin)}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>

          <Table>
            <THead><tr><TH>Material</TH><TH align="right">Previsto</TH><TH align="right">Consumido</TH><TH align="right">Diferença</TH></tr></THead>
            <TBody>
              {e.materiais.map(m => {
                const dif = m.consumido - m.previsto
                return (
                  <TR key={m.produtoId}>
                    <TD>{m.nome}</TD>
                    <TD align="right" className="tabular-nums">{num(m.previsto, 4)} {m.unidade}</TD>
                    <TD align="right" className="tabular-nums">{num(m.consumido, 4)}</TD>
                    <TD align="right" className={`tabular-nums ${dif > 0 ? 'text-warning-700 font-semibold' : 'text-gray-500'}`}>
                      {dif > 0 ? `+${num(dif, 4)}` : num(dif, 4)}
                    </TD>
                  </TR>
                )
              })}
            </TBody>
          </Table>

          <div>
            <h4 className="text-xs font-semibold text-gray-500 uppercase mb-1">Apontamentos</h4>
            <ul className="divide-y divide-gray-100 text-sm">
              {e.apontamentos.map(a => (
                <li key={a.id} className={`py-2 flex flex-wrap items-center gap-x-3 gap-y-1 ${a.status === 'ESTORNADO' ? 'opacity-60' : ''}`}>
                  <StatusBadge tone={STATUS_APONTAMENTO[a.status].tom}>{STATUS_APONTAMENTO[a.status].rotulo}</StatusBadge>
                  <span>Op. {a.operacaoSequencia} · {a.operadorNome ?? 'operador'}{a.maquinaCodigo ? ` · ${a.maquinaCodigo}` : ''}</span>
                  <span className="tabular-nums text-gray-500">{dataHoraBR(a.inicio)} · {horas(a.minutosTrabalhados)}{a.minutosParado > 0 ? ` (+${horas(a.minutosParado)} parado)` : ''}</span>
                  {a.status !== 'EM_ANDAMENTO' && a.status !== 'PAUSADO' && (
                    <span className="tabular-nums">boa {num(a.quantidadeBoa)} · refugo {num(a.quantidadeRefugo)}{a.lote ? ` · lote ${a.lote}` : ''}</span>
                  )}
                  {a.motivoEstorno && <i className="text-gray-500">Estorno: {a.motivoEstorno}</i>}
                  {a.status === 'FINALIZADO' && podeExcluir(MODULO_APONTAMENTO) && (
                    <Button size="sm" variant="outline-danger" className="ml-auto"
                      onClick={() => setEstorno({ tipo: 'apontamento', id: a.id, titulo: `Estornar apontamento da operação ${a.operacaoSequencia}`, motivo: '' })}>
                      Estornar
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          </div>

          {e.consumos.length > 0 && (
            <div>
              <h4 className="text-xs font-semibold text-gray-500 uppercase mb-1">Consumos</h4>
              <ul className="divide-y divide-gray-100 text-sm">
                {e.consumos.map(c => (
                  <li key={c.id} className={`py-2 flex flex-wrap items-center gap-x-3 ${c.estornado ? 'opacity-60 line-through' : ''}`}>
                    <span className="tabular-nums">{num(c.quantidade, 4)} {c.unidade} de {c.nome}</span>
                    {c.loteMaterial && <span className="text-gray-500">lote {c.loteMaterial}</span>}
                    <span className="text-gray-400 tabular-nums">{dataHoraBR(c.createdAt)} · {c.usuarioNome ?? ''}</span>
                    {!c.estornado && podeExcluir(MODULO_APONTAMENTO) && (
                      <Button size="sm" variant="outline-danger" className="ml-auto no-underline"
                        onClick={() => setEstorno({ tipo: 'consumo', id: c.id, titulo: `Estornar consumo de ${c.nome}`, motivo: '' })}>
                        Estornar
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {e.paradas.length > 0 && (
            <div>
              <h4 className="text-xs font-semibold text-gray-500 uppercase mb-1">Paradas durante a OP</h4>
              <ul className="text-sm space-y-0.5">
                {e.paradas.map(p => (
                  <li key={p.id} className="text-gray-600">
                    {p.motivoDescricao}{p.maquinaCodigo ? ` · ${p.maquinaCodigo}` : ''} · <span className="tabular-nums">{horas(p.minutos)}</span>
                    {!p.fim && <StatusBadge tone="warning" className="ml-2">em curso</StatusBadge>}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}

      {consumindo && <ConsumoModal ordemId={ordemId} onClose={() => setConsumindo(false)} />}
      {estorno && (
        <Modal title={estorno.titulo} onClose={() => setEstorno(null)}
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setEstorno(null)}>Voltar</Button>
            <Button variant="danger" fullWidth loading={estornar.isPending}
              onClick={() => (estorno.motivo.trim() ? estornar.mutate() : toast.error('Informe o motivo.'))}>
              Estornar
            </Button>
          </>}>
          <p className="text-sm text-gray-600">O estoque é corrigido: a produção sai e o material consumido volta.</p>
          <Field label="Motivo *" htmlFor="estorno-motivo">
            <textarea id="estorno-motivo" rows={3} maxLength={500} value={estorno.motivo} autoFocus
              onChange={ev => setEstorno({ ...estorno, motivo: ev.target.value })} className={baseInputClass} />
          </Field>
        </Modal>
      )}
    </section>
  )
}
