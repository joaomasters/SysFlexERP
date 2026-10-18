import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { CheckCircle, XCircle, Loader2, AlertTriangle, RefreshCw, CreditCard } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { formatBRL } from '@/shared/utils/mask'
import { CurrencyInput } from '@/shared/components/ui'
import type { PagamentoDTO } from '@/types/venda'
import { ROTULO_PROVEDOR, type TerminalPagamento, type TransacaoTerminal } from '@/types/pagamento'

/*
 Pagamento com cartão no PDV (tema escuro do console de operação).

 Fluxo, conforme o modo do terminal escolhido:
  - API_NUVEM:    backend envia para a maquininha (Mercado Pago Point / Stone Connect);
                  aqui só fazemos polling da transação até sair de PENDENTE.
  - AGENTE_LOCAL: o navegador chama o agente TEF do computador (contrato em
                  docs/MAQUININHAS.md) e repassa a resposta ao backend.
  - MANUAL:       operador passa na maquininha e confirma, digitando NSU/autorização.
  - Sem terminal cadastrado: registro manual "livre" (comportamento antigo).

 Pagamentos de terminal vão com transacaoTerminalId — o backend confere no
 fechamento da venda que a transação foi mesmo APROVADA.
*/

type Metodo = 'DEBITO' | 'CREDITO'
type Estado = 'idle' | 'enviando' | 'aguardando' | 'confirmar_manual' | 'aprovado' | 'negado' | 'erro_agente'

const SEM_TERMINAL = 0
const STORAGE_TERMINAL = 'sysflex_pdv_terminal_id'
const INTERVALO_POLLING_MS = 2500
const TIMEOUT_AGENTE_MS = 120_000

interface Props {
  metodo: Metodo
  valor: number
  setValor: (v: number) => void
  vendaId?: number
  onAprovado: (pagamento: PagamentoDTO) => void
  /** true enquanto há cobrança em andamento — o modal trava troca de método e cancelamento */
  onOcupado: (ocupado: boolean) => void
}

function lerTerminalSalvo(): number | null {
  try {
    const v = localStorage.getItem(STORAGE_TERMINAL)
    return v ? Number(v) : null
  } catch {
    return null
  }
}

function salvarTerminal(id: number) {
  try { localStorage.setItem(STORAGE_TERMINAL, String(id)) } catch { /* modo privado etc. */ }
}

export default function PainelCartao({ metodo, valor, setValor, vendaId, onAprovado, onOcupado }: Props) {
  const [estado, setEstado]       = useState<Estado>('idle')
  const [mensagem, setMensagem]   = useState('')
  const [parcelas, setParcelas]   = useState(1)
  const [transacao, setTransacao] = useState<TransacaoTerminal | null>(null)
  const [terminalId, setTerminalId] = useState<number | null>(null)
  const [nsu, setNsu]             = useState('')
  const [autorizacao, setAutorizacao] = useState('')
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)
  // Transações já repassadas como pagamento: o polling pode ver APROVADA mais
  // de uma vez (requisições sobrepostas) e não pode lançar o cartão em dobro.
  const repassadasRef = useRef<Set<number>>(new Set())

  const { data: terminais = [], isLoading } = useQuery<TerminalPagamento[]>({
    queryKey: ['pdv-terminais'],
    queryFn: () => api.get('/pagamento/terminais/disponiveis', { silent: true } as never).then(r => r.data),
    staleTime: 60_000,
  })

  const compativeis = terminais.filter(t => (metodo === 'DEBITO' ? t.aceitaDebito : t.aceitaCredito))
  const terminal = compativeis.find(t => t.id === terminalId) ?? null
  const maxParcelas = terminal ? terminal.maxParcelas : 12
  const ocupado = estado === 'enviando' || estado === 'aguardando' || estado === 'confirmar_manual' || estado === 'erro_agente'

  // Escolha inicial: o último terminal usado NESTE computador, senão o primeiro compatível.
  useEffect(() => {
    if (isLoading) return
    if (terminalId !== null && (terminalId === SEM_TERMINAL || compativeis.some(t => t.id === terminalId))) return
    const salvo = lerTerminalSalvo()
    const escolhido = compativeis.find(t => t.id === salvo) ?? compativeis[0]
    setTerminalId(escolhido ? escolhido.id : SEM_TERMINAL)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoading, metodo, terminais])

  useEffect(() => { onOcupado(ocupado) }, [ocupado, onOcupado])
  useEffect(() => { if (parcelas > maxParcelas) setParcelas(maxParcelas) }, [maxParcelas, parcelas])
  // Ao desmontar (troca de método, venda paga): para o polling e destrava o modal
  useEffect(() => () => { pararPolling(); onOcupado(false) }, [])  // eslint-disable-line react-hooks/exhaustive-deps

  function pararPolling() {
    if (pollRef.current) {
      clearInterval(pollRef.current)
      pollRef.current = null
    }
  }

  function reiniciar() {
    pararPolling()
    setEstado('idle')
    setMensagem('')
    setTransacao(null)
    setNsu('')
    setAutorizacao('')
  }

  /** Aplica o estado de uma transação vinda do backend. */
  function aplicar(t: TransacaoTerminal) {
    setTransacao(t)
    switch (t.status) {
      case 'APROVADA':
        pararPolling()
        if (repassadasRef.current.has(t.id)) break
        repassadasRef.current.add(t.id)
        setEstado('aprovado')
        setMensagem([t.bandeira, t.nsu && `NSU ${t.nsu}`, t.autorizacao && `Aut. ${t.autorizacao}`].filter(Boolean).join(' · '))
        onAprovado({
          formaPagamento: t.tipo,
          valor: t.valor,
          parcelas: t.parcelas,
          transacaoTerminalId: t.id,
          nsu: t.nsu ?? undefined,
          autorizacao: t.autorizacao ?? undefined,
          bandeira: t.bandeira ?? undefined,
        })
        break
      case 'NEGADA':
      case 'CANCELADA':
      case 'ERRO':
        pararPolling()
        setEstado('negado')
        setMensagem(t.mensagem ?? 'Transação não aprovada')
        break
      default:
        if (t.mensagem) setMensagem(t.mensagem)
    }
  }

  async function iniciar() {
    const valorCentavos = Math.round(valor * 100) / 100
    if (!valorCentavos) return

    // Sem maquininha cadastrada: registro manual livre (sem transação no backend)
    if (terminalId === SEM_TERMINAL || !terminal) {
      setEstado('confirmar_manual')
      setMensagem('Passe o cartão na maquininha e confirme abaixo.')
      return
    }
    if (!vendaId) {
      setEstado('negado')
      setMensagem('Abra a venda antes de cobrar no cartão.')
      return
    }

    salvarTerminal(terminal.id)
    setEstado('enviando')
    setMensagem('Enviando para a maquininha...')
    try {
      const { data: t } = await api.post<TransacaoTerminal>('/pagamento/transacoes', {
        terminalId: terminal.id, vendaId, tipo: metodo, parcelas: metodo === 'CREDITO' ? parcelas : 1,
        valor: valorCentavos,
      })
      setTransacao(t)
      if (t.status !== 'PENDENTE') { aplicar(t); return }

      if (t.modoIntegracao === 'API_NUVEM') {
        setEstado('aguardando')
        setMensagem(`Aguardando cartão na maquininha ${t.terminalNome}...`)
        pollRef.current = setInterval(async () => {
          try {
            const { data } = await api.get<TransacaoTerminal>(`/pagamento/transacoes/${t.id}`, { silent: true } as never)
            aplicar(data)
          } catch { /* falha momentânea: tenta no próximo ciclo */ }
        }, INTERVALO_POLLING_MS)
      } else if (t.modoIntegracao === 'AGENTE_LOCAL') {
        setEstado('aguardando')
        setMensagem('Aguardando cartão na maquininha...')
        await cobrarNoAgente(t)
      } else {
        setEstado('confirmar_manual')
        setMensagem(`Digite ${formatBRL(t.valor)} na maquininha ${t.terminalNome} e confirme abaixo.`)
      }
    } catch (e: unknown) {
      setEstado('negado')
      setMensagem(mensagemDeErro(e, 'Não foi possível iniciar a cobrança'))
    }
  }

  /** Contrato do agente local: POST {agenteUrl}/transaction — ver docs/MAQUININHAS.md */
  async function cobrarNoAgente(t: TransacaoTerminal) {
    let resposta: Record<string, unknown>
    try {
      const resp = await fetch(`${t.agenteUrl}/transaction`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          amount: Math.round(t.valor * 100),
          type: t.tipo === 'DEBITO' ? 'DEBIT' : 'CREDIT',
          installments: t.parcelas,
          reference: `SYSFLEX-TT-${t.id}`,
        }),
        signal: AbortSignal.timeout(TIMEOUT_AGENTE_MS),
      })
      if (!resp.ok) throw new Error(`Agente respondeu HTTP ${resp.status}`)
      resposta = await resp.json()
    } catch (e: unknown) {
      setEstado('erro_agente')
      setMensagem(e instanceof Error && e.name !== 'TypeError'
        ? e.message
        : `Agente TEF não respondeu em ${t.agenteUrl}. Verifique se ele está instalado e aberto neste computador.`)
      return
    }

    const aprovada = resposta.status === 'APPROVED' || resposta.approved === true
    await registrarResultado(t.id, {
      aprovada,
      nsu: texto(resposta.nsu),
      autorizacao: texto(resposta.authorizationCode),
      bandeira: texto(resposta.brand),
      mensagem: texto(resposta.message),
      respostaBruta: JSON.stringify(resposta),
    })
  }

  async function registrarResultado(id: number, corpo: Record<string, unknown>) {
    try {
      const { data } = await api.post<TransacaoTerminal>(`/pagamento/transacoes/${id}/resultado`, corpo)
      aplicar(data)
    } catch (e: unknown) {
      setMensagem(mensagemDeErro(e, 'Falha ao registrar o resultado'))
    }
  }

  function confirmarManual(aprovada: boolean) {
    if (transacao) {
      registrarResultado(transacao.id, { aprovada, nsu, autorizacao, mensagem: aprovada ? null : 'Não aprovado (informado pelo operador)' })
      return
    }
    // Registro livre, sem terminal cadastrado
    if (!aprovada) { reiniciar(); return }
    onAprovado({
      formaPagamento: metodo, valor: Math.round(valor * 100) / 100,
      parcelas: metodo === 'CREDITO' ? parcelas : 1,
      nsu: nsu || undefined, autorizacao: autorizacao || undefined,
    })
    setEstado('aprovado')
    setMensagem('Registrado manualmente')
  }

  async function cancelar() {
    if (!transacao) { reiniciar(); return }
    try {
      const { data } = await api.post<TransacaoTerminal>(`/pagamento/transacoes/${transacao.id}/cancelar`)
      if (data.status === 'APROVADA') { aplicar(data); return }
      reiniciar()
    } catch {
      // Ex.: o cliente pagou no último segundo — reconsulta para mostrar o estado real
      try {
        const { data } = await api.get<TransacaoTerminal>(`/pagamento/transacoes/${transacao.id}`, { silent: true } as never)
        aplicar(data)
      } catch { /* mantém a tela como está */ }
    }
  }

  // ── Render ────────────────────────────────────────────────────────────────
  const bloqueado = estado !== 'idle' && estado !== 'negado'

  return (
    <div className="space-y-3">
      {/* Seleção de maquininha */}
      {!isLoading && (
        <select
          value={terminalId ?? SEM_TERMINAL}
          onChange={e => setTerminalId(Number(e.target.value))}
          disabled={bloqueado}
          className="w-full bg-gray-800 border border-gray-600 rounded-lg px-3 py-2 text-sm"
        >
          {compativeis.map(t => (
            <option key={t.id} value={t.id}>{t.nome} · {ROTULO_PROVEDOR[t.provedor]}</option>
          ))}
          <option value={SEM_TERMINAL}>Maquininha não integrada (registrar manualmente)</option>
        </select>
      )}

      <div className="flex gap-2">
        <CurrencyInput
          value={valor}
          onChange={setValor}
          disabled={bloqueado}
          dark
          className="flex-1 [&_input]:text-lg [&_input]:font-bold"
        />
        {metodo === 'CREDITO' && (
          <select
            value={parcelas}
            onChange={e => setParcelas(parseInt(e.target.value))}
            disabled={bloqueado}
            className="bg-gray-800 border border-gray-600 rounded-lg px-3 text-sm"
          >
            {Array.from({ length: maxParcelas }, (_, i) => i + 1).map(n => <option key={n} value={n}>{n}x</option>)}
          </select>
        )}
      </div>

      {(estado === 'idle' || estado === 'negado') && (
        <>
          {estado === 'negado' && (
            <div className="bg-red-900/30 border border-red-700 rounded-xl p-3 flex items-start gap-2">
              <XCircle size={18} className="text-red-400 shrink-0 mt-0.5" />
              <div>
                <p className="text-red-400 font-medium text-sm">Não aprovado</p>
                <p className="text-xs text-gray-400">{mensagem}</p>
              </div>
            </div>
          )}
          <button
            onClick={() => { reiniciar(); iniciar() }}
            disabled={!valor}
            className="w-full py-3 bg-blue-600 hover:bg-blue-500 rounded-xl font-bold transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
          >
            {estado === 'negado' ? <><RefreshCw size={16} /> Tentar novamente</> : <><CreditCard size={16} /> {terminal ? 'Enviar para maquininha' : 'Registrar cartão'}</>}
          </button>
        </>
      )}

      {(estado === 'enviando' || estado === 'aguardando') && (
        <div className="bg-blue-900/30 border border-blue-700 rounded-xl p-4 text-center space-y-2">
          <Loader2 size={32} className="mx-auto text-blue-400 animate-spin" />
          <p className="text-sm font-medium text-blue-300">{mensagem}</p>
          <p className="text-xs text-gray-500">
            {formatBRL(valor)}{metodo === 'CREDITO' && parcelas > 1 ? ` em ${parcelas}x` : ''}
          </p>
          {estado === 'aguardando' && transacao?.modoIntegracao === 'API_NUVEM' && (
            <button onClick={cancelar} className="text-xs text-gray-400 hover:text-red-400">
              Cancelar cobrança
            </button>
          )}
        </div>
      )}

      {estado === 'confirmar_manual' && (
        <div className="bg-gray-800 border border-gray-700 rounded-xl p-4 space-y-3">
          <p className="text-sm text-gray-300">{mensagem}</p>
          <div className="grid grid-cols-2 gap-2">
            <input
              placeholder="NSU / DOC"
              value={nsu}
              onChange={e => setNsu(e.target.value)}
              maxLength={50}
              className="bg-gray-900 border border-gray-600 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-blue-500"
            />
            <input
              placeholder="Autorização"
              value={autorizacao}
              onChange={e => setAutorizacao(e.target.value)}
              maxLength={50}
              className="bg-gray-900 border border-gray-600 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-blue-500"
            />
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => confirmarManual(false)}
              className="flex-1 py-2.5 rounded-lg bg-gray-700 hover:bg-gray-600 text-sm font-medium"
            >
              Não aprovou
            </button>
            <button
              onClick={() => confirmarManual(true)}
              className="flex-1 py-2.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-sm font-bold"
            >
              Aprovado
            </button>
          </div>
        </div>
      )}

      {estado === 'erro_agente' && (
        <div className="bg-amber-900/20 border border-amber-700 rounded-xl p-4 space-y-2">
          <div className="flex items-center gap-2">
            <AlertTriangle size={18} className="text-amber-400 shrink-0" />
            <p className="text-amber-400 font-medium text-sm">Agente da maquininha não respondeu</p>
          </div>
          <p className="text-xs text-gray-400">{mensagem}</p>
          <div className="flex flex-wrap gap-3 pt-1 text-xs">
            <button onClick={() => transacao && cobrarNoAgente(transacao)} className="text-blue-400 hover:text-blue-300 flex items-center gap-1">
              <RefreshCw size={12} /> Tentar de novo
            </button>
            <button
              onClick={() => {
                setEstado('confirmar_manual')
                setMensagem('Se o cartão foi aprovado na maquininha, informe o comprovante.')
              }}
              className="text-gray-300 hover:text-white"
            >
              Concluir manualmente
            </button>
            <button onClick={cancelar} className="text-gray-400 hover:text-red-400">Cancelar</button>
          </div>
        </div>
      )}

      {estado === 'aprovado' && (
        <div className="bg-emerald-900/30 border border-emerald-700 rounded-xl p-4 flex items-center gap-3">
          <CheckCircle size={28} className="text-emerald-400 shrink-0" />
          <div className="flex-1">
            <p className="font-bold text-emerald-400">Aprovado!</p>
            <p className="text-xs text-gray-400">{mensagem}</p>
          </div>
          <button onClick={reiniciar} className="text-xs text-gray-400 hover:text-white">Novo cartão</button>
        </div>
      )}
    </div>
  )
}

function texto(v: unknown): string | null {
  return v === undefined || v === null || v === '' ? null : String(v)
}

function mensagemDeErro(e: unknown, padrao: string): string {
  const resp = (e as { response?: { data?: { message?: string } } })?.response
  return resp?.data?.message ?? (e instanceof Error ? e.message : padrao)
}
