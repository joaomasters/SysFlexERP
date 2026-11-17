import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/api/axios'
import { Field, baseInputClass, LoadingState, StatusBadge, Table, THead, TH, TBody, TR, TD } from '@/shared/components/ui'
import { hojeISO } from '@/shared/utils/data'
import type { PainelIndicadores } from '@/types/industrial'
import { horas, num } from '../comum'

const somarDias = (iso: string, dias: number) => {
  const d = new Date(`${iso}T12:00:00`)
  d.setDate(d.getDate() + dias)
  return d.toISOString().slice(0, 10)
}
const pct = (v?: number | null, casas = 1) => (v == null ? '—' : `${(v * 100).toLocaleString('pt-BR', { maximumFractionDigits: casas })}%`)

/** Número em destaque com rótulo e um complemento (o "de quanto"). */
function Destaque({ rotulo, valor, detalhe }: { rotulo: string; valor: string; detalhe?: string }) {
  return (
    <div className="bg-white rounded-xl border border-gray-100 p-4">
      <div className="text-xs text-gray-500">{rotulo}</div>
      <div className="text-2xl font-bold tabular-nums text-gray-900 mt-1">{valor}</div>
      {detalhe && <div className="text-xs text-gray-500 mt-0.5">{detalhe}</div>}
    </div>
  )
}

/** Barras horizontais de uma série: magnitude por categoria, com o valor escrito ao lado (sem depender de cor). */
function Barras({ titulo, itens }: { titulo: string; itens: { rotulo: string; valor: number; texto: string; nota?: string }[] }) {
  const max = Math.max(1, ...itens.map(i => i.valor))
  return (
    <section className="bg-white rounded-xl border border-gray-100 p-4">
      <h3 className="text-sm font-semibold text-gray-700 mb-3">{titulo}</h3>
      {itens.length === 0 ? <p className="text-sm text-gray-500">Nada no período.</p> : (
        <ul className="space-y-2">
          {itens.map(i => (
            <li key={i.rotulo} className="grid grid-cols-12 items-center gap-2 text-sm" title={`${i.rotulo}: ${i.texto}`}>
              <span className="col-span-4 truncate text-gray-700">{i.rotulo}{i.nota && <span className="ml-1 text-xs text-gray-500">({i.nota})</span>}</span>
              <span className="col-span-6 h-3 rounded bg-gray-100 overflow-hidden">
                <span className="block h-full rounded-r bg-primary-500" style={{ width: `${(i.valor / max) * 100}%` }} />
              </span>
              <span className="col-span-2 text-right tabular-nums text-gray-700">{i.texto}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/** Painel de indicadores do período: produção, prazo, paradas, qualidade, expedição e OEE (quando confiável). */
export default function IndicadoresAba() {
  const [de, setDe] = useState(somarDias(hojeISO(), -29))
  const [ate, setAte] = useState(hojeISO())
  const valido = !!de && !!ate && ate >= de
  const { data: p, isLoading } = useQuery<PainelIndicadores>({
    queryKey: ['ind-indicadores', de, ate], enabled: valido,
    queryFn: () => api.get('/industrial/gestao/indicadores', { params: { de, ate } }).then(r => r.data),
  })

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <Field label="De" htmlFor="ind-de"><input id="ind-de" type="date" value={de} onChange={e => setDe(e.target.value)} className={baseInputClass} /></Field>
        <Field label="Até" htmlFor="ind-ate" hint="Até 92 dias"><input id="ind-ate" type="date" value={ate} min={de} onChange={e => setAte(e.target.value)} className={baseInputClass} /></Field>
      </div>
      {!valido ? <p className="text-sm text-gray-500">Escolha um período válido.</p> : isLoading || !p ? <LoadingState /> : (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            <Destaque rotulo="OPs concluídas no prazo" valor={`${p.opsConcluidasNoPrazo} de ${p.opsConcluidas}`}
              detalhe={p.opsConcluidas ? `${Math.round((p.opsConcluidasNoPrazo / p.opsConcluidas) * 100)}% no prazo` : 'nenhuma concluída no período'} />
            <Destaque rotulo="OPs abertas atrasadas" valor={String(p.opsAbertasAtrasadas)} detalhe={`de ${p.opsAbertas} abertas hoje`} />
            <Destaque rotulo="Refugo" valor={p.refugoPercentual == null ? '—' : `${num(p.refugoPercentual, 1)}%`} detalhe={`${p.apontamentos} apontamentos finalizados`} />
            <Destaque rotulo="Paradas não planejadas" valor={horas(p.minutosParadaPerda)} detalhe={`+ ${horas(p.minutosParadaPlanejada)} planejadas`} />
            <Destaque rotulo="Inspeções" valor={`${p.inspecoesAprovadas} aprovadas`} detalhe={`${p.inspecoesReprovadas} reprovadas`} />
            <Destaque rotulo="Não conformidades abertas" valor={String(p.ncsAbertas)} detalhe={`${p.lotesBloqueados} lotes bloqueados · ${p.lotesEmQuarentena} em quarentena`} />
            <Destaque rotulo="Expedições carregadas" valor={String(p.expedicoesCarregadas)} detalhe={`${num(p.kgExpedidos, 0)} kg${p.kgCompleto ? '' : ' (há itens sem peso)'}`} />
          </div>

          <div className="grid lg:grid-cols-2 gap-4">
            <Barras titulo="Produção boa por produto (última operação)"
              itens={p.producao.map(x => ({ rotulo: x.nome, valor: x.boa, texto: `${num(x.boa)} ${x.unidade}`, nota: x.refugo > 0 ? `refugo ${num(x.refugo)}` : undefined }))} />
            <Barras titulo="Paradas por motivo"
              itens={p.paradasPorMotivo.map(x => ({ rotulo: x.motivo, valor: x.minutos, texto: horas(x.minutos), nota: x.planejada ? 'planejada' : `${x.ocorrencias}×` }))} />
          </div>

          <section className="bg-white rounded-xl border border-gray-100 p-4">
            <h3 className="text-sm font-semibold text-gray-700 mb-1">OEE por máquina</h3>
            <p className="text-xs text-gray-500 mb-3">
              Disponibilidade × performance × qualidade. Só aparece quando a máquina tem turnos, ao menos 3 apontamentos no período,
              tempo padrão no roteiro e números plausíveis; senão, a linha diz o que falta.
            </p>
            {p.oee.length === 0 ? <p className="text-sm text-gray-500">Nenhum apontamento com máquina no período.</p> : (
              <Table>
                <THead><tr><TH>Máquina</TH><TH align="right">Planejado</TH><TH align="right">Operando</TH><TH align="right">Disponib.</TH><TH align="right">Perform.</TH><TH align="right">Qualidade</TH><TH align="right">OEE</TH></tr></THead>
                <TBody>
                  {p.oee.map(o => (
                    <TR key={o.maquinaId}>
                      <TD><span className="font-mono font-semibold">{o.codigo}</span> <span className="text-gray-600">{o.descricao}</span>
                        {!o.confiavel && <ul className="text-xs text-warning-700 list-disc pl-4 mt-1">{o.motivos.map(m => <li key={m}>{m}</li>)}</ul>}</TD>
                      <TD align="right" className="tabular-nums">{horas(o.minutosPlanejados)}</TD>
                      <TD align="right" className="tabular-nums">{horas(o.minutosOperando)}</TD>
                      <TD align="right" className="tabular-nums">{pct(o.disponibilidade)}</TD>
                      <TD align="right" className="tabular-nums">{pct(o.performance)}</TD>
                      <TD align="right" className="tabular-nums">{pct(o.qualidade)}</TD>
                      <TD align="right">{o.confiavel ? <b className="tabular-nums">{pct(o.oee)}</b> : <StatusBadge tone="neutral">Dados insuficientes</StatusBadge>}</TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
            )}
          </section>
        </>
      )}
    </div>
  )
}
