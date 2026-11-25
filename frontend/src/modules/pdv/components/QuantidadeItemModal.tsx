import { useState } from 'react'
import { Minus, Plus } from 'lucide-react'
import { WeightInput } from '@/shared/components/ui'
import { formatBRL } from '@/shared/utils/mask'
import type { ItemVenda } from '@/types/venda'

interface Props {
  item: ItemVenda
  onConfirmar: (quantidade: number) => Promise<void>
  onCancelar: () => void
}

const porPeso = (unidade?: string) => unidade === 'KG' || unidade === 'G'

/** Troca a quantidade de um item da comanda (F7). Tema escuro, como o resto do PDV. */
export default function QuantidadeItemModal({ item, onConfirmar, onCancelar }: Props) {
  const peso = porPeso(item.produto.unidadeMedida)
  const [quantidade, setQuantidade] = useState(item.quantidade)
  const [salvando, setSalvando] = useState(false)

  const valida = quantidade > 0 && (peso || Number.isInteger(quantidade))
  const total = Math.round(quantidade * item.precoUnitario * 100) / 100 - (item.descontoItem ?? 0)

  async function confirmar() {
    if (!valida || salvando) return
    setSalvando(true)
    try {
      await onConfirmar(quantidade)
    } catch {
      // erro já mostrado pelo interceptor do axios; o modal fica aberto para corrigir
    } finally {
      setSalvando(false)
    }
  }

  const passo = (delta: number) => setQuantidade(q => Math.max(1, Math.round(q) + delta))

  return (
    <div
      className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
      // stopPropagation: os atalhos do PDV não podem agir por trás do modal
      onKeyDown={e => {
        e.stopPropagation()
        if (e.key === 'Escape') onCancelar()
        if (!peso && (e.key === '+' || e.key === '-')) { e.preventDefault(); passo(e.key === '+' ? 1 : -1) }
      }}
    >
      <form
        onSubmit={e => { e.preventDefault(); confirmar() }}
        className="bg-card border border-border rounded-2xl w-full max-w-sm p-6 space-y-4"
      >
        <div>
          <h2 className="text-lg font-bold">Quantidade</h2>
          <p className="text-sm text-gray-500">{item.produto.nome} · {formatBRL(item.precoUnitario)}{peso ? '/kg' : ' cada'}</p>
        </div>

        {peso ? (
          <WeightInput value={quantidade} onChange={setQuantidade} autoFocus className="[&_input]:text-2xl [&_input]:font-bold" />
        ) : (
          <div className="flex items-center gap-2">
            <button type="button" onClick={() => passo(-1)} aria-label="Menos um"
              className="h-12 w-12 shrink-0 rounded-xl bg-gray-100 hover:bg-gray-200 flex items-center justify-center">
              <Minus size={18} />
            </button>
            <input
              autoFocus
              inputMode="numeric"
              aria-label="Quantidade"
              value={String(quantidade)}
              onFocus={e => e.target.select()}
              onChange={e => setQuantidade(Number(e.target.value.replace(/\D/g, '')) || 0)}
              className="w-full rounded-lg bg-gray-100 border border-gray-200 px-3 py-2 text-center text-2xl font-bold text-gray-900                         tabular-nums focus:outline-none focus:ring-2 focus:ring-success-500"
            />
            <button type="button" onClick={() => passo(1)} aria-label="Mais um"
              className="h-12 w-12 shrink-0 rounded-xl bg-gray-100 hover:bg-gray-200 flex items-center justify-center">
              <Plus size={18} />
            </button>
          </div>
        )}

        <div className="flex justify-between font-bold text-sm">
          <span>Item fica por</span>
          <span className="tabular-nums text-success-700">{formatBRL(Math.max(0, total))}</span>
        </div>
        {!valida && <p className="text-xs text-danger-600">Informe uma quantidade maior que zero.</p>}

        <div className="flex gap-3">
          <button type="button" onClick={onCancelar}
            className="flex-1 py-3 rounded-xl bg-gray-200 hover:bg-gray-300 text-sm font-medium">
            Cancelar <span className="opacity-60">[Esc]</span>
          </button>
          <button type="submit" disabled={!valida || salvando}
            className="flex-1 py-3 rounded-xl bg-success-600 hover:bg-success-500 disabled:bg-gray-200 disabled:text-gray-500 font-bold text-sm text-white">
            Confirmar <span className="opacity-70 font-normal">[Enter]</span>
          </button>
        </div>
      </form>
    </div>
  )
}
