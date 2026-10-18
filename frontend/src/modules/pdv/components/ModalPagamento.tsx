import React, { useState, useEffect, useRef, useCallback } from 'react'
import {
  X, Banknote, CreditCard, Smartphone, BookOpen, FileCheck,
  CheckCircle, Loader2, Trash2, Copy,
} from 'lucide-react'
import { api } from '@/shared/api/axios'
import toast from 'react-hot-toast'
import type { PagamentoDTO } from '@/types/venda'
import { formatBRL } from '@/shared/utils/mask'
import { CurrencyInput } from '@/shared/components/ui'
import PainelCartao from './PainelCartao'

// ─── tipos internos ──────────────────────────────────────────────────────────
type Metodo  = 'DINHEIRO' | 'DEBITO' | 'CREDITO' | 'PIX' | 'CHEQUE' | 'FIADO'
type PixStatus   = 'idle' | 'gerando' | 'aguardando' | 'aprovado' | 'erro'

interface Props {
  totalVenda: number
  vendaId?:   number
  onConfirmar: (pagamentos: PagamentoDTO[]) => void
  onCancelar:  () => void
}

// Tema escuro proposital — este modal faz parte do console de operação do
// PDV (ver nota em PDVPage.tsx). Os campos de valor usam o mesmo
// CurrencyInput (máscara "caixa eletrônico") do resto do sistema, só que
// com a variante `dark`.
const brl = formatBRL

// ─── metadados dos métodos ────────────────────────────────────────────────────
const METODOS: { value: Metodo; label: string; icon: React.ElementType; cor: string }[] = [
  { value: 'DINHEIRO', label: 'Dinheiro',  icon: Banknote,    cor: 'bg-emerald-600' },
  { value: 'DEBITO',   label: 'Débito',    icon: CreditCard,  cor: 'bg-blue-600'    },
  { value: 'CREDITO',  label: 'Crédito',   icon: CreditCard,  cor: 'bg-violet-600'  },
  { value: 'PIX',      label: 'PIX',       icon: Smartphone,  cor: 'bg-teal-600'    },
  { value: 'CHEQUE',   label: 'Cheque',    icon: FileCheck,   cor: 'bg-amber-600'   },
  { value: 'FIADO',    label: 'Fiado',     icon: BookOpen,    cor: 'bg-orange-600'  },
]

export default function ModalPagamento({ totalVenda, vendaId, onConfirmar, onCancelar }: Props) {
  const [pagamentos,  setPagamentos]  = useState<PagamentoDTO[]>([])
  const [metodo,      setMetodo]      = useState<Metodo>('DINHEIRO')

  // ── Dinheiro / Cheque / Fiado ─────────────────────────────────────────────
  const [valor,       setValor]       = useState(0)
  const [chequeBanco, setChequeBanco] = useState('')
  const [chequeNum,   setChequeNum]   = useState('')
  const [chequeTit,   setChequeTit]   = useState('')

  // ── Cartão (Débito / Crédito) — estado vive no PainelCartao ───────────────
  const [cartaoOcupado, setCartaoOcupado] = useState(false)
  // Pagamento de maquininha já aprovado que o operador pediu para remover
  const [confirmandoRemocao, setConfirmandoRemocao] = useState<number | null>(null)

  // ── PIX ───────────────────────────────────────────────────────────────────
  const [pixSt,       setPixSt]       = useState<PixStatus>('idle')
  const [pixQr,       setPixQr]       = useState('')           // pix copia-e-cola
  const [pixImg,      setPixImg]      = useState('')           // base64
  const [pixMpId,     setPixMpId]     = useState('')
  const pixPollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const totalPago = pagamentos.reduce((s: number, p: PagamentoDTO) => s + p.valor, 0)
  const restante  = Math.max(0, totalVenda - totalPago)
  const troco     = totalPago > totalVenda ? totalPago - totalVenda : 0
  const pago      = totalPago >= totalVenda - 0.009

  // Preenche valor com o restante quando muda de método
  useEffect(() => {
    setValor(restante > 0 ? restante : 0)
    setPixSt('idle')
    stopPixPoll()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [metodo])

  // ── helpers gerais ─────────────────────────────────────────────────────────
  function addPagamento(forma: Metodo, v: number, extra?: Partial<PagamentoDTO>) {
    if (v <= 0) return
    const novoPag: PagamentoDTO = { formaPagamento: forma, valor: v, ...extra }
    setPagamentos((prev: PagamentoDTO[]) => [...prev, novoPag])
    setValor(0)
  }

  function removePagamento(i: number) {
    // Cartão aprovado em maquininha integrada: o dinheiro já foi cobrado do
    // cliente — remover daqui não estorna. Pede confirmação explícita.
    if (pagamentos[i]?.transacaoTerminalId && confirmandoRemocao !== i) {
      setConfirmandoRemocao(i)
      return
    }
    setConfirmandoRemocao(null)
    setPagamentos((prev: PagamentoDTO[]) => prev.filter((_: PagamentoDTO, idx: number) => idx !== i))
  }

  // ── Dinheiro / Fiado ──────────────────────────────────────────────────────
  function confirmarDinheiro() {
    if (!valor) return
    addPagamento(metodo, valor)
  }

  // ── Cheque ────────────────────────────────────────────────────────────────
  function confirmarCheque() {
    if (!valor || !chequeBanco || !chequeNum) {
      toast.error('Preencha banco e número do cheque')
      return
    }
    addPagamento('CHEQUE', valor, {
      chequeBanco,
      chequeNumero: chequeNum,
      chequeTitular: chequeTit,
    })
    setChequeBanco('')
    setChequeNum('')
    setChequeTit('')
  }

  // ── Cartão ────────────────────────────────────────────────────────────────
  function cartaoAprovado(p: PagamentoDTO) {
    setPagamentos((prev: PagamentoDTO[]) => [...prev, p])
    setValor(0)
  }

  // ── PIX ───────────────────────────────────────────────────────────────────
  const stopPixPoll = useCallback(() => {
    if (pixPollRef.current) {
      clearInterval(pixPollRef.current)
      pixPollRef.current = null
    }
  }, [])

  useEffect(() => () => stopPixPoll(), [stopPixPoll])

  async function gerarPix() {
    const v = valor
    if (!v) return

    setPixSt('gerando')
    setPixQr('')
    setPixImg('')

    try {
      const { data } = await api.post('/pagamento/pix/criar', {
        valor:   v,
        vendaId: vendaId ?? null,
      })
      setPixQr(data.qrCode)
      setPixImg(data.qrCodeBase64)
      setPixMpId(data.mpPaymentId)
      setPixSt('aguardando')

      // Polling a cada 3s
      pixPollRef.current = setInterval(async () => {
        try {
          const { data: st } = await api.get(`/pagamento/pix/status/${data.mpPaymentId}`)
          if (st.status === 'APROVADO') {
            stopPixPoll()
            setPixSt('aprovado')
            addPagamento('PIX', v, { referenciaExterna: String(data.mpPaymentId) })
            toast.success('PIX confirmado! ✓')
          } else if (st.status === 'EXPIRADO') {
            stopPixPoll()
            setPixSt('erro')
            toast.error('PIX expirado. Gere um novo.')
          }
        } catch { /* ignora erros de polling */ }
      }, 3000)

    } catch (e: unknown) {
      setPixSt('erro')
      const msg = e instanceof Error ? e.message : 'Erro ao gerar PIX'
      toast.error(msg)
    }
  }

  function copiarPix() {
    if (!pixQr) return
    navigator.clipboard.writeText(pixQr).then(() => toast.success('Copiado!'))
  }

  // ── Confirmar venda ────────────────────────────────────────────────────────
  function confirmarVenda() {
    if (!pago) return
    stopPixPoll()
    onConfirmar(pagamentos)
  }

  // ════════════════════════════════════════════════════════════════════════════
  //  RENDER
  // ════════════════════════════════════════════════════════════════════════════
  return (
    <div className="fixed inset-0 bg-black/75 flex items-center justify-center z-50 p-4">
      <div className="bg-gray-900 rounded-2xl w-full max-w-lg shadow-2xl text-white flex flex-col max-h-[95vh]">

        {/* ── Header ─────────────────────────────────────────────────────── */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-700 shrink-0">
          <h2 className="text-lg font-bold">Pagamento</h2>
          <button
            onClick={onCancelar}
            disabled={cartaoOcupado}
            title={cartaoOcupado ? 'Finalize ou cancele a cobrança no cartão primeiro' : undefined}
            className="p-1 hover:text-red-400 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
          >
            <X size={20} />
          </button>
        </div>

        <div className="overflow-y-auto flex-1 px-6 py-4 space-y-4">

          {/* ── Totais ───────────────────────────────────────────────────── */}
          <div className="grid grid-cols-3 gap-2 text-center">
            <div className="bg-gray-800 rounded-xl p-3">
              <p className="text-xs text-gray-400 mb-1">Total</p>
              <p className="text-xl font-black tabular-nums">{brl(totalVenda)}</p>
            </div>
            <div className="bg-gray-800 rounded-xl p-3">
              <p className="text-xs text-gray-400 mb-1">Pago</p>
              <p className="text-xl font-black tabular-nums text-emerald-400">{brl(totalPago)}</p>
            </div>
            <div className={`rounded-xl p-3 ${troco > 0 ? 'bg-yellow-900/50' : restante > 0 ? 'bg-red-900/40' : 'bg-emerald-900/40'}`}>
              <p className="text-xs text-gray-400 mb-1">{troco > 0 ? 'Troco' : 'Restante'}</p>
              <p className={`text-xl font-black tabular-nums ${troco > 0 ? 'text-yellow-400' : restante > 0 ? 'text-red-400' : 'text-emerald-400'}`}>
                {brl(troco > 0 ? troco : restante)}
              </p>
            </div>
          </div>

          {/* ── Pagamentos adicionados ───────────────────────────────────── */}
          {pagamentos.length > 0 && (
            <div className="space-y-1">
              {pagamentos.map((p, i) => (
                <div key={i} className="bg-gray-800 rounded-lg px-3 py-2 text-sm">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <CheckCircle size={14} className="text-emerald-400 shrink-0" />
                      <span className="text-gray-300">{p.formaPagamento}</span>
                      {p.parcelas && p.parcelas > 1 && <span className="text-xs text-gray-500">{p.parcelas}x</span>}
                      {(p.bandeira || p.nsu) && (
                        <span className="text-xs text-gray-500">{[p.bandeira, p.nsu && `NSU ${p.nsu}`].filter(Boolean).join(' · ')}</span>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="tabular-nums font-medium">{brl(p.valor)}</span>
                      <button onClick={() => removePagamento(i)} className="text-gray-600 hover:text-red-400">
                        <Trash2 size={13} />
                      </button>
                    </div>
                  </div>
                  {confirmandoRemocao === i && (
                    <div className="mt-2 pt-2 border-t border-gray-700 text-xs space-y-1.5">
                      <p className="text-amber-400">
                        Este cartão já foi APROVADO na maquininha. Remover daqui não devolve o dinheiro:
                        faça o cancelamento/estorno na própria maquininha.
                      </p>
                      <div className="flex gap-3">
                        <button onClick={() => removePagamento(i)} className="text-red-400 hover:text-red-300 font-medium">
                          Já estornei, remover
                        </button>
                        <button onClick={() => setConfirmandoRemocao(null)} className="text-gray-400 hover:text-white">
                          Manter
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}

          {/* ── Seletor de método ────────────────────────────────────────── */}
          {!pago && (
            <>
              <div className="grid grid-cols-3 gap-2">
                {METODOS.map(m => {
                  const Icon = m.icon
                  const ativo = metodo === m.value
                  return (
                    <button
                      key={m.value}
                      onClick={() => setMetodo(m.value)}
                      disabled={cartaoOcupado && !ativo}
                      className={`flex flex-col items-center gap-1.5 py-3 rounded-xl text-xs font-medium transition-all
                        disabled:opacity-40 disabled:cursor-not-allowed
                        ${ativo ? m.cor + ' text-white shadow-lg scale-105' : 'bg-gray-800 text-gray-400 hover:bg-gray-700'}`}
                    >
                      <Icon size={20} />
                      {m.label}
                    </button>
                  )
                })}
              </div>

              {/* ── Painel Dinheiro / Fiado ─────────────────────────────── */}
              {(metodo === 'DINHEIRO' || metodo === 'FIADO') && (
                <div className="space-y-3">
                  <div className="flex gap-2">
                    <CurrencyInput
                      value={valor}
                      onChange={setValor}
                      onKeyDown={e => e.key === 'Enter' && confirmarDinheiro()}
                      dark
                      autoFocus
                      className="flex-1 [&_input]:text-lg [&_input]:font-bold"
                    />
                    <button
                      onClick={confirmarDinheiro}
                      className="px-5 bg-emerald-600 hover:bg-emerald-500 rounded-lg font-bold transition-colors"
                    >
                      ✓
                    </button>
                  </div>
                  {metodo === 'DINHEIRO' && valor > restante && (
                    <p className="text-sm text-yellow-400">
                      Troco: {brl(valor - restante)}
                    </p>
                  )}
                </div>
              )}

              {/* ── Painel Cartão (Débito / Crédito) ───────────────────── */}
              {(metodo === 'DEBITO' || metodo === 'CREDITO') && (
                <PainelCartao
                  key={metodo}
                  metodo={metodo}
                  valor={valor}
                  setValor={setValor}
                  vendaId={vendaId}
                  onAprovado={cartaoAprovado}
                  onOcupado={setCartaoOcupado}
                />
              )}

              {/* ── Painel PIX ─────────────────────────────────────────── */}
              {metodo === 'PIX' && (
                <div className="space-y-3">
                  <div className="flex gap-2">
                    <CurrencyInput
                      value={valor}
                      onChange={setValor}
                      disabled={pixSt === 'aguardando' || pixSt === 'aprovado'}
                      dark
                      className="flex-1 [&_input]:text-lg [&_input]:font-bold [&_input]:focus:border-teal-500"
                    />
                    {(pixSt === 'idle' || pixSt === 'erro') ? (
                      <button
                        onClick={gerarPix}
                        disabled={!valor}
                        className="px-4 bg-teal-600 hover:bg-teal-500 rounded-lg font-bold transition-colors disabled:opacity-50"
                      >
                        Gerar QR
                      </button>
                    ) : null}
                  </div>

                  {pixSt === 'gerando' && (
                    <div className="flex items-center gap-2 text-teal-400 text-sm">
                      <Loader2 size={16} className="animate-spin" /> Gerando QR Code...
                    </div>
                  )}

                  {pixSt === 'aguardando' && (
                    <div className="space-y-3">
                      {/* QR Code */}
                      {pixImg && (
                        <div className="flex justify-center">
                          <div className="bg-white p-3 rounded-xl">
                            <img
                              src={`data:image/png;base64,${pixImg}`}
                              alt="QR Code PIX"
                              className="w-48 h-48 object-contain"
                            />
                          </div>
                        </div>
                      )}

                      {/* Pix Copia e Cola */}
                      {pixQr && (
                        <div className="bg-gray-800 rounded-lg p-3 space-y-2">
                          <p className="text-xs text-gray-400">Pix Copia e Cola</p>
                          <p className="text-xs text-teal-300 break-all font-mono leading-relaxed line-clamp-3">
                            {pixQr}
                          </p>
                          <button
                            onClick={copiarPix}
                            className="flex items-center gap-1.5 text-xs text-teal-400 hover:text-teal-300"
                          >
                            <Copy size={12} /> Copiar código
                          </button>
                        </div>
                      )}

                      <div className="flex items-center gap-2 text-sm text-teal-300 justify-center">
                        <Loader2 size={14} className="animate-spin" />
                        Aguardando pagamento...
                      </div>
                      <button
                        onClick={() => { stopPixPoll(); setPixSt('idle'); setPixMpId('') }}
                        className="text-xs text-gray-500 hover:text-gray-300 w-full text-center"
                      >
                        Cancelar e gerar novo
                      </button>
                    </div>
                  )}

                  {pixSt === 'aprovado' && (
                    <div className="bg-emerald-900/30 border border-emerald-700 rounded-xl p-4 flex items-center gap-3">
                      <CheckCircle size={28} className="text-emerald-400 shrink-0" />
                      <div>
                        <p className="font-bold text-emerald-400">PIX confirmado!</p>
                        <p className="text-xs text-gray-400">ID: {pixMpId}</p>
                      </div>
                    </div>
                  )}

                  {pixSt === 'erro' && (
                    <p className="text-sm text-red-400 text-center">
                      Erro ao gerar PIX. Verifique se MP_ACCESS_TOKEN está configurado no Railway.
                    </p>
                  )}
                </div>
              )}

              {/* ── Painel Cheque ───────────────────────────────────────── */}
              {metodo === 'CHEQUE' && (
                <div className="space-y-3">
                  <CurrencyInput
                    value={valor}
                    onChange={setValor}
                    dark
                    placeholder="Valor do cheque"
                    className="[&_input]:text-lg [&_input]:font-bold"
                  />
                  <div className="grid grid-cols-2 gap-2">
                    <input
                      placeholder="Banco"
                      value={chequeBanco}
                      onChange={e => setChequeBanco(e.target.value)}
                      className="bg-gray-800 border border-gray-600 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-amber-500"
                    />
                    <input
                      placeholder="Nº do Cheque *"
                      value={chequeNum}
                      onChange={e => setChequeNum(e.target.value)}
                      className="bg-gray-800 border border-gray-600 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-amber-500"
                    />
                  </div>
                  <input
                    placeholder="Titular do cheque"
                    value={chequeTit}
                    onChange={e => setChequeTit(e.target.value)}
                    className="w-full bg-gray-800 border border-gray-600 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-amber-500"
                  />
                  <button
                    onClick={confirmarCheque}
                    disabled={!valor || !chequeBanco || !chequeNum}
                    className="w-full py-3 bg-amber-600 hover:bg-amber-500 rounded-xl font-bold transition-colors disabled:opacity-50"
                  >
                    Registrar Cheque
                  </button>
                </div>
              )}
            </>
          )}

          {/* ── Venda Paga ── banner ─────────────────────────────────────── */}
          {pago && (
            <div className="bg-emerald-900/30 border border-emerald-700 rounded-xl p-4 flex items-center gap-3">
              <CheckCircle size={28} className="text-emerald-400 shrink-0" />
              <div>
                <p className="font-bold text-emerald-400">Pagamento completo!</p>
                {troco > 0 && <p className="text-sm text-yellow-400">Troco a devolver: {brl(troco)}</p>}
              </div>
            </div>
          )}
        </div>

        {/* ── Footer ─────────────────────────────────────────────────────── */}
        <div className="px-6 py-4 border-t border-gray-700 flex gap-3 shrink-0">
          <button
            onClick={onCancelar}
            disabled={cartaoOcupado}
            className="flex-1 py-3 rounded-xl bg-gray-700 hover:bg-gray-600 text-sm font-medium transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Cancelar
          </button>
          <button
            onClick={confirmarVenda}
            disabled={!pago}
            className="flex-1 py-3 rounded-xl bg-emerald-600 hover:bg-emerald-500
                       disabled:bg-gray-700 disabled:text-gray-500 disabled:cursor-not-allowed
                       font-bold text-sm transition-colors"
          >
            {pago ? `Finalizar Venda${troco > 0 ? ` · Troco ${brl(troco)}` : ''}` : 'Aguardando pagamento...'}
          </button>
        </div>
      </div>
    </div>
  )
}
