import { useState } from 'react'
import { CurrencyInput } from '@/shared/components/ui'
import { formatBRL } from '@/shared/utils/mask'
import type { ItemVenda } from '@/types/venda'

interface Props {
  item: ItemVenda
  onConfirmar: (valor: number) => Promise<void>
  onCancelar: () => void
}

const arredondar = (v: number) => Math.round(v * 100) / 100

/** Desconto em reais ou em % sobre o valor cheio do item. Tema escuro, como o resto do PDV. */
export default function DescontoItemModal({ item, onConfirmar, onCancelar }: Props) {
  const valorCheio = arredondar(item.totalItem + (item.descontoItem ?? 0))
  const [modo, setModo] = useState<'reais' | 'percentual'>('reais')
  const [reais, setReais] = useState(item.descontoItem ?? 0)
  const [percentual, setPercentual] = useState('')
  const [salvando, setSalvando] = useState(false)

  const desconto = modo === 'reais'
    ? reais
    : arredondar(valorCheio * (Number(percentual.replace(',', '.')) || 0) / 100)
  const valido = desconto >= 0 && desconto < valorCheio

  async function confirmar(valor: number) {
    setSalvando(true)
    try {
      await onConfirmar(valor)
    } catch {
      // erro já mostrado pelo interceptor do axios; o modal fica aberto para corrigir
    } finally {
      setSalvando(false)
    }
  }

  return (
    <div
      className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4"
      // stopPropagation: F2/F4/F10 do PDV não podem agir por trás do modal
      onKeyDown={e => { e.stopPropagation(); if (e.key === 'Escape') onCancelar() }}
    >
      <form
        onSubmit={e => { e.preventDefault(); if (valido && !salvando) confirmar(desconto) }}
        className="bg-gray-900 border border-gray-800 rounded-2xl w-full max-w-sm p-6 space-y-4"
      >
        <div>
          <h2 className="text-lg font-bold">Desconto no item</h2>
          <p className="text-sm text-gray-400">{item.produto.nome} · {formatBRL(valorCheio)}</p>
        </div>

        <div className="grid grid-cols-2 gap-1 rounded-lg bg-gray-800 p-1">
          {([['reais', 'R$'], ['percentual', '%']] as const).map(([valor, rotulo]) => (
            <button
              key={valor}
              type="button"
              onClick={() => setModo(valor)}
              className={`py-1.5 rounded-md text-sm font-medium transition-colors
                ${modo === valor ? 'bg-gray-600 text-white' : 'text-gray-400 hover:text-white'}`}
            >
              {rotulo}
            </button>
          ))}
        </div>

        {modo === 'reais' ? (
          <CurrencyInput value={reais} onChange={setReais} dark autoFocus className="[&_input]:text-2xl [&_input]:font-bold" />
        ) : (
          <input
            autoFocus
            inputMode="decimal"
            value={percentual}
            onChange={e => setPercentual(e.target.value.replace(/[^\d.,]/g, ''))}
            placeholder="0"
            aria-label="Percentual de desconto"
            className="w-full rounded-lg bg-gray-800 border border-gray-700 px-3 py-2 text-2xl font-bold text-white
                       focus:outline-none focus:ring-2 focus:ring-emerald-500"
          />
        )}

        <div className="text-sm space-y-1">
          <div className="flex justify-between text-gray-400">
            <span>Desconto</span>
            <span className="tabular-nums text-amber-400">-{formatBRL(desconto)}</span>
          </div>
          <div className="flex justify-between font-bold">
            <span>Item fica por</span>
            <span className="tabular-nums text-emerald-400">{formatBRL(Math.max(0, arredondar(valorCheio - desconto)))}</span>
          </div>
          {!valido && <p className="text-xs text-red-400">O desconto deve ser menor que o valor do item.</p>}
        </div>

        <div className="flex gap-3">
          <button type="button" onClick={onCancelar}
            className="flex-1 py-3 rounded-xl bg-gray-700 hover:bg-gray-600 text-sm font-medium">
            Cancelar
          </button>
          {(item.descontoItem ?? 0) > 0 && (
            <button type="button" onClick={() => confirmar(0)} disabled={salvando}
              className="flex-1 py-3 rounded-xl bg-gray-700 hover:bg-gray-600 text-sm font-medium disabled:opacity-50">
              Remover
            </button>
          )}
          <button type="submit" disabled={!valido || salvando}
            className="flex-1 py-3 rounded-xl bg-emerald-600 hover:bg-emerald-500 disabled:bg-gray-700 disabled:text-gray-500 font-bold text-sm">
            Aplicar
          </button>
        </div>
      </form>
    </div>
  )
}
