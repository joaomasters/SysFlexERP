import { useEffect, useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { HardHat, Pause, Play, Square, Package, AlertTriangle } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { PageHeader, Modal, Field, baseInputClass, StatusBadge, LoadingState, EmptyState } from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import {
  STATUS_MAQUINA, novaChave,
  type Apontamento, type ChaoRecursos, type FilaItem, type MaquinaChao, type MotivoParada,
} from '@/types/industrial'
import { dataBR, horas, num } from './comum'
import ConsumoModal from './execucao/ConsumoModal'
import { MODULO_APONTAMENTO } from './execucao/ExecucaoOPSecao'

const CHAVE_CENTRO = 'ind-chao-centro'
const lerCentro = () => { try { return Number(localStorage.getItem(CHAVE_CENTRO)) || 0 } catch { return 0 } }
const gravarCentro = (id: number) => { try { localStorage.setItem(CHAVE_CENTRO, String(id)) } catch { /* sem armazenamento: só não lembra */ } }

const CORES = {
  primario: 'bg-primary-600 hover:bg-primary-700 text-white',
  sucesso: 'bg-success-600 hover:bg-success-700 text-white',
  alerta: 'bg-warning-500 hover:bg-warning-600 text-white',
  perigo: 'bg-danger-600 hover:bg-danger-700 text-white',
  neutro: 'bg-white hover:bg-gray-50 text-gray-800 border-2 border-gray-300',
}

/** Botão do tablet: alto, texto grande, fácil de acertar com luva. */
function BotaoGrande({ children, cor = 'primario', onClick, disabled, carregando, className = '' }:
  { children: ReactNode; cor?: keyof typeof CORES; onClick?: () => void; disabled?: boolean; carregando?: boolean; className?: string }) {
  return (
    <button type="button" onClick={onClick} disabled={disabled || carregando}
      className={`min-h-[56px] px-5 rounded-xl text-base font-semibold flex items-center justify-center gap-2 transition-colors
        disabled:opacity-50 disabled:cursor-not-allowed ${CORES[cor]} ${className}`}>
      {carregando ? '...' : children}
    </button>
  )
}

/**
 * Chão de fábrica (tablet): o operador escolhe o centro uma vez, vê a fila de operações e aponta
 * início, pausa com motivo, fim com quantidades e consumo de material. Também para e libera máquinas.
 */
export default function ChaoDeFabricaPage() {
  const qc = useQueryClient()
  const { podeCriar } = usePermissao()
  const [centroId, setCentroId] = useState(lerCentro)
  const [iniciando, setIniciando] = useState<FilaItem | null>(null)
  const [parando, setParando] = useState<MaquinaChao | null>(null)
  const podeApontar = podeCriar(MODULO_APONTAMENTO)

  const { data: recursos } = useQuery<ChaoRecursos>({
    queryKey: ['ind-chao-recursos'], queryFn: () => api.get('/industrial/chao/recursos').then(r => r.data), refetchInterval: 30_000,
  })
  const meu = useQuery<Apontamento | null>({
    queryKey: ['ind-chao-meu'], queryFn: () => api.get('/industrial/chao/meu-apontamento').then(r => (r.status === 204 ? null : r.data)),
    refetchInterval: 30_000,
  })
  const { data: fila, isLoading: carregandoFila } = useQuery<FilaItem[]>({
    queryKey: ['ind-chao-fila', centroId], enabled: !!centroId, refetchInterval: 30_000,
    queryFn: () => api.get('/industrial/chao/fila', { params: { centroId } }).then(r => r.data),
  })

  useEffect(() => {
    if (recursos && centroId && !recursos.centros.some(c => c.id === centroId)) setCentroId(0)
  }, [recursos, centroId])

  const recarregar = () => {
    for (const k of ['ind-chao-meu', 'ind-chao-fila', 'ind-chao-recursos', 'ind-execucao', 'ind-ordens', 'ind-ordem']) {
      qc.invalidateQueries({ queryKey: [k] })
    }
  }
  const encerrarParada = useMutation({
    mutationFn: (id: number) => api.post(`/industrial/paradas/${id}/encerrar`),
    onSuccess: () => { toast.success('Máquina liberada.'); recarregar() },
  })

  const maquinasCentro = recursos?.maquinas.filter(m => m.centroTrabalhoId === centroId) ?? []

  return (
    <div className="p-4 sm:p-6 max-w-5xl mx-auto">
      <PageHeader title="Chão de fábrica" icon={<HardHat size={22} />} subtitle="Apontamento de produção, paradas e consumo" />

      <div className="mb-5">
        <Field label="Centro de trabalho" htmlFor="chao-centro">
          <select id="chao-centro" value={centroId} className={`${baseInputClass} text-lg py-3`}
            onChange={e => { const id = Number(e.target.value); setCentroId(id); gravarCentro(id) }}>
            <option value={0}>Escolha onde você está trabalhando...</option>
            {recursos?.centros.map(c => <option key={c.id} value={c.id}>{c.codigo} · {c.nome}</option>)}
          </select>
        </Field>
      </div>

      {meu.data && <MeuApontamento a={meu.data} atualizadoEm={meu.dataUpdatedAt} motivos={recursos?.motivos ?? []} onMudou={recarregar} />}

      {!!centroId && (
        <>
          <h2 className="text-lg font-bold text-gray-900 mb-2">Fila do centro</h2>
          {carregandoFila ? <LoadingState /> : !fila?.length ? (
            <div className="bg-white rounded-xl border border-gray-100 mb-6"><EmptyState>Nenhuma operação liberada para este centro.</EmptyState></div>
          ) : (
            <div className="grid gap-3 mb-6">
              {fila.map(f => (
                <div key={f.operacaoId} className="bg-white rounded-xl border border-gray-200 p-4 flex flex-wrap items-center gap-4">
                  <div className="flex-1 min-w-[220px]">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono font-bold text-lg">{f.ordemNumero}</span>
                      <span className="text-gray-400">·</span>
                      <span className="font-semibold">{f.sequencia} {f.descricao}</span>
                      {f.atrasada && <StatusBadge tone="danger">Atrasada</StatusBadge>}
                      {f.ordemStatus === 'PAUSADA' && <StatusBadge tone="warning">OP pausada</StatusBadge>}
                      {f.operacaoStatus === 'EM_EXECUCAO' && <StatusBadge tone="info">Em execução</StatusBadge>}
                    </div>
                    <div className="text-gray-700">{f.produtoNome}</div>
                    <div className="text-sm text-gray-500 tabular-nums">
                      Feito {num(f.produzidoBoa)} de {num(f.quantidadeOrdem)} {f.unidade}{f.refugo > 0 ? ` · refugo ${num(f.refugo)}` : ''}
                      {' '}· prioridade {f.prioridade} · entrega {dataBR(f.fimPrevisto)}
                      {f.maquinaPlanejadaCodigo ? ` · máquina ${f.maquinaPlanejadaCodigo}` : ''}
                    </div>
                  </div>
                  {podeApontar && (
                    <BotaoGrande cor="sucesso" disabled={!!meu.data || f.ordemStatus === 'PAUSADA'} onClick={() => setIniciando(f)}>
                      <Play size={18} /> Iniciar
                    </BotaoGrande>
                  )}
                </div>
              ))}
            </div>
          )}
          {meu.data && <p className="text-sm text-gray-500 -mt-4 mb-6">Finalize o seu apontamento aberto para iniciar outro.</p>}

          <h2 className="text-lg font-bold text-gray-900 mb-2">Máquinas do centro</h2>
          {maquinasCentro.length === 0 ? <p className="text-sm text-gray-500">Centro sem máquinas cadastradas (trabalho manual).</p> : (
            <div className="grid sm:grid-cols-2 gap-3">
              {maquinasCentro.map(m => (
                <div key={m.id} className="bg-white rounded-xl border border-gray-200 p-4">
                  <div className="flex items-center justify-between gap-2">
                    <div><span className="font-mono font-bold">{m.codigo}</span> <span className="text-gray-600">{m.descricao}</span></div>
                    <StatusBadge tone={STATUS_MAQUINA[m.status].tom}>{STATUS_MAQUINA[m.status].rotulo}</StatusBadge>
                  </div>
                  {m.ordemEmUso && <div className="text-sm text-gray-500 mt-1">Produzindo {m.ordemEmUso}</div>}
                  {m.paradaAberta && (
                    <div className="text-sm text-warning-700 mt-1">
                      Parada: {m.paradaAberta.motivoDescricao} há {horas(m.paradaAberta.minutos)}
                    </div>
                  )}
                  {podeApontar && (
                    <div className="mt-3">
                      {m.paradaAberta && !m.paradaAberta.apontamentoId ? (
                        <BotaoGrande cor="sucesso" className="w-full" carregando={encerrarParada.isPending}
                          onClick={() => encerrarParada.mutate(m.paradaAberta!.id)}>Encerrar parada</BotaoGrande>
                      ) : !m.apontamentoAbertoId && m.status !== 'EM_MANUTENCAO' && (
                        <BotaoGrande cor="neutro" className="w-full" onClick={() => setParando(m)}>Registrar parada</BotaoGrande>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {iniciando && recursos && (
        <IniciarModal item={iniciando} maquinas={maquinasCentro} onClose={() => setIniciando(null)}
          onIniciado={() => { setIniciando(null); recarregar() }} />
      )}
      {parando && recursos && (
        <MotivoModal titulo={`Parar ${parando.codigo}`} motivos={recursos.motivos} onClose={() => setParando(null)}
          enviar={(motivoId, chave, observacao) => api.post('/industrial/paradas', { chave, maquinaId: parando.id, motivoId, observacao })}
          onFeito={() => { toast.success('Parada registrada.'); setParando(null); recarregar() }} />
      )}
    </div>
  )
}

function MeuApontamento({ a, atualizadoEm, motivos, onMudou }:
  { a: Apontamento; atualizadoEm: number; motivos: MotivoParada[]; onMudou: () => void }) {
  const [agora, setAgora] = useState(Date.now())
  const [pausando, setPausando] = useState(false)
  const [finalizando, setFinalizando] = useState(false)
  const [consumindo, setConsumindo] = useState(false)
  useEffect(() => { const t = setInterval(() => setAgora(Date.now()), 15_000); return () => clearInterval(t) }, [])

  const decorrido = Math.max(0, (agora - atualizadoEm) / 60_000)
  const trabalhado = a.minutosTrabalhados + (a.status === 'EM_ANDAMENTO' ? decorrido : 0)
  const pausado = a.status === 'PAUSADO'

  const retomar = useMutation({
    mutationFn: () => api.post(`/industrial/apontamentos/${a.id}/retomar`),
    onSuccess: () => { toast.success('Produção retomada.'); onMudou() },
  })

  return (
    <div className={`rounded-2xl border-2 p-4 sm:p-5 mb-6 ${pausado ? 'border-warning-400 bg-warning-50' : 'border-primary-400 bg-primary-50'}`}>
      <div className="text-xs font-semibold uppercase text-gray-500">Seu apontamento</div>
      <div className="flex flex-wrap items-baseline gap-x-3 mt-1">
        <span className="font-mono font-bold text-xl">{a.ordemNumero}</span>
        <span className="font-semibold text-lg">{a.operacaoSequencia} {a.operacaoDescricao}</span>
      </div>
      <div className="text-gray-700">{a.produtoNome}{a.maquinaCodigo ? ` · máquina ${a.maquinaCodigo}` : ''}</div>
      <div className="mt-2 text-3xl font-bold tabular-nums">{horas(trabalhado)}</div>
      <div className="text-sm text-gray-600">trabalhando{a.minutosParado > 0 ? ` · ${horas(a.minutosParado)} parado` : ''}</div>
      {pausado && a.paradaAberta && (
        <div className="mt-2 flex items-center gap-2 text-warning-800 font-semibold">
          <AlertTriangle size={18} /> Pausado: {a.paradaAberta.motivoDescricao}
        </div>
      )}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mt-4">
        {pausado
          ? <BotaoGrande cor="sucesso" carregando={retomar.isPending} onClick={() => retomar.mutate()}><Play size={18} /> Retomar</BotaoGrande>
          : <BotaoGrande cor="alerta" onClick={() => setPausando(true)}><Pause size={18} /> Pausar</BotaoGrande>}
        <BotaoGrande cor="perigo" onClick={() => setFinalizando(true)}><Square size={18} /> Finalizar</BotaoGrande>
        <BotaoGrande cor="neutro" onClick={() => setConsumindo(true)}><Package size={18} /> Material</BotaoGrande>
      </div>

      {pausando && (
        <MotivoModal titulo="Por que parou?" motivos={motivos} onClose={() => setPausando(false)}
          enviar={(motivoId, chave, observacao) => api.post(`/industrial/apontamentos/${a.id}/pausar`, { chave, motivoId, observacao })}
          onFeito={() => { toast.success('Pausa registrada.'); setPausando(false); onMudou() }} />
      )}
      {finalizando && <FinalizarModal a={a} onClose={() => setFinalizando(false)} onFeito={() => { setFinalizando(false); onMudou() }} />}
      {consumindo && <ConsumoModal ordemId={a.ordemId} apontamentoId={a.id} onClose={() => setConsumindo(false)} />}
    </div>
  )
}

function IniciarModal({ item, maquinas, onClose, onIniciado }:
  { item: FilaItem; maquinas: MaquinaChao[]; onClose: () => void; onIniciado: () => void }) {
  const [chave] = useState(novaChave)
  const compativeis = maquinas.filter(m => !item.tipoMaquinaId || m.tipoMaquinaId === item.tipoMaquinaId)
  const livre = (m: MaquinaChao) => !m.apontamentoAbertoId && !m.paradaAberta && m.status !== 'EM_MANUTENCAO'
  const [maquinaId, setMaquinaId] = useState<number | null>(() => {
    const planejada = compativeis.find(m => m.id === item.maquinaPlanejadaId && livre(m))
    return planejada?.id ?? (compativeis.filter(livre).length === 1 ? compativeis.filter(livre)[0].id : null)
  })
  const iniciar = useMutation({
    mutationFn: () => api.post('/industrial/apontamentos', { chave, operacaoId: item.operacaoId, maquinaId }),
    onSuccess: () => { toast.success('Apontamento iniciado.'); onIniciado() },
  })
  const precisaMaquina = maquinas.length > 0

  return (
    <Modal title={`Iniciar ${item.ordemNumero} · ${item.sequencia} ${item.descricao}`} onClose={onClose} maxWidth="lg"
      footer={<>
        <BotaoGrande cor="neutro" className="flex-1" onClick={onClose}>Voltar</BotaoGrande>
        <BotaoGrande cor="sucesso" className="flex-1" carregando={iniciar.isPending} disabled={precisaMaquina && !maquinaId}
          onClick={() => iniciar.mutate()}><Play size={18} /> Iniciar</BotaoGrande>
      </>}>
      <p className="text-gray-700">{item.produtoNome} · {num(item.quantidadeOrdem)} {item.unidade}</p>
      {item.instrucoes && <div className="rounded-lg bg-gray-50 p-3 text-sm whitespace-pre-wrap"><b>Instruções:</b> {item.instrucoes}</div>}
      {precisaMaquina && (
        <div>
          <div className="text-sm font-semibold text-gray-700 mb-2">Máquina{item.tipoMaquinaNome ? ` (${item.tipoMaquinaNome})` : ''}</div>
          {compativeis.length === 0 ? <p className="text-sm text-danger-600">Nenhuma máquina do tipo pedido neste centro.</p> : (
            <div className="grid gap-2">
              {compativeis.map(m => (
                <button key={m.id} type="button" disabled={!livre(m)} onClick={() => setMaquinaId(m.id)}
                  className={`text-left rounded-xl border-2 p-3 disabled:opacity-50 ${maquinaId === m.id ? 'border-success-500 bg-success-50' : 'border-gray-200'}`}>
                  <span className="font-mono font-bold">{m.codigo}</span> {m.descricao}
                  <span className="block text-sm text-gray-500">
                    {m.apontamentoAbertoId ? `Em uso (${m.ordemEmUso})` : m.paradaAberta ? `Parada: ${m.paradaAberta.motivoDescricao}`
                      : STATUS_MAQUINA[m.status].rotulo}
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}

function MotivoModal({ titulo, motivos, onClose, enviar, onFeito }: {
  titulo: string; motivos: MotivoParada[]; onClose: () => void
  enviar: (motivoId: number, chave: string, observacao: string | null) => Promise<unknown>; onFeito: () => void
}) {
  const [chave] = useState(novaChave)
  const [motivoId, setMotivoId] = useState(0)
  const [observacao, setObservacao] = useState('')
  const registrar = useMutation({ mutationFn: () => enviar(motivoId, chave, observacao.trim() || null), onSuccess: onFeito })
  return (
    <Modal title={titulo} onClose={onClose} maxWidth="lg"
      footer={<>
        <BotaoGrande cor="neutro" className="flex-1" onClick={onClose}>Voltar</BotaoGrande>
        <BotaoGrande cor="alerta" className="flex-1" disabled={!motivoId} carregando={registrar.isPending} onClick={() => registrar.mutate()}>
          Confirmar parada
        </BotaoGrande>
      </>}>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {motivos.map(m => (
          <button key={m.id} type="button" onClick={() => setMotivoId(m.id)}
            className={`min-h-[56px] rounded-xl border-2 p-3 text-left font-semibold ${motivoId === m.id ? 'border-warning-500 bg-warning-50' : 'border-gray-200'}`}>
            {m.descricao}
          </button>
        ))}
      </div>
      <Field label="Observação" htmlFor="parada-obs">
        <input id="parada-obs" value={observacao} maxLength={500} onChange={e => setObservacao(e.target.value)} className={`${baseInputClass} py-3`} />
      </Field>
    </Modal>
  )
}

function FinalizarModal({ a, onClose, onFeito }: { a: Apontamento; onClose: () => void; onFeito: () => void }) {
  const [chave] = useState(novaChave)
  const [boa, setBoa] = useState('')
  const [refugo, setRefugo] = useState('')
  const [motivoRefugo, setMotivoRefugo] = useState('')
  const [concluir, setConcluir] = useState(false)
  const temRefugo = Number(refugo) > 0
  const finalizar = useMutation({
    mutationFn: () => api.post(`/industrial/apontamentos/${a.id}/finalizar`, {
      chave, quantidadeBoa: Number(boa || 0), quantidadeRefugo: Number(refugo || 0),
      motivoRefugo: temRefugo ? motivoRefugo : null, concluirOperacao: concluir,
    }).then(r => r.data as Apontamento),
    onSuccess: r => {
      toast.success(r.lote ? `Finalizado. Lote ${r.lote} entrou no estoque.` : 'Apontamento finalizado.')
      onFeito()
    },
  })
  return (
    <Modal title={`Finalizar ${a.ordemNumero} · ${a.operacaoSequencia} ${a.operacaoDescricao}`} onClose={onClose} maxWidth="lg"
      footer={<>
        <BotaoGrande cor="neutro" className="flex-1" onClick={onClose}>Voltar</BotaoGrande>
        <BotaoGrande cor="perigo" className="flex-1" carregando={finalizar.isPending}
          onClick={() => (temRefugo && !motivoRefugo.trim() ? toast.error('Informe o motivo do refugo.') : finalizar.mutate())}>
          Finalizar
        </BotaoGrande>
      </>}>
      <div className="grid grid-cols-2 gap-3">
        <Field label={`Quantidade boa (${a.unidade})`} htmlFor="fim-boa">
          <input id="fim-boa" type="number" inputMode="decimal" step="any" min="0" value={boa} autoFocus
            onChange={e => setBoa(e.target.value)} className={`${baseInputClass} text-2xl py-3 tabular-nums`} />
        </Field>
        <Field label="Refugo" htmlFor="fim-refugo">
          <input id="fim-refugo" type="number" inputMode="decimal" step="any" min="0" value={refugo}
            onChange={e => setRefugo(e.target.value)} className={`${baseInputClass} text-2xl py-3 tabular-nums`} />
        </Field>
      </div>
      {temRefugo && (
        <Field label="Motivo do refugo *" htmlFor="fim-motivo">
          <input id="fim-motivo" value={motivoRefugo} maxLength={200} onChange={e => setMotivoRefugo(e.target.value)} className={`${baseInputClass} py-3`} />
        </Field>
      )}
      <label className="flex items-start gap-3 rounded-xl border-2 border-gray-200 p-3 cursor-pointer">
        <input type="checkbox" className="mt-1 h-5 w-5" checked={concluir} onChange={e => setConcluir(e.target.checked)} />
        <span><b>Concluir esta operação</b><span className="block text-sm text-gray-500">Marque quando não houver mais nada a fazer nesta operação da OP.</span></span>
      </label>
      {a.ultimaOperacao && (
        <p className="text-sm text-success-700">Última operação: a quantidade boa entra no estoque como produto acabado, com lote da OP.</p>
      )}
    </Modal>
  )
}
