import { Minus, Plus } from 'lucide-react'
import { formatBRL } from '@/shared/utils/mask'

/** Denominações em centavos (evita erro de ponto flutuante na soma). */
const NOTAS = [20000, 10000, 5000, 2000, 1000, 500, 200]
const MOEDAS = [100, 50, 25, 10, 5]

/** Quantidade contada por denominação, em centavos: { 5000: 2, 100: 3 }. */
export type Contagem = Record<number, number>

export const totalContagem = (c: Contagem) =>
  Object.entries(c).reduce((s, [cent, q]) => s + Number(cent) * q, 0) / 100

/** Formato esperado pela API: [{ valor: 50, quantidade: 2 }]. Ignora quantidades zeradas. */
export const contagemParaApi = (c: Contagem) => ({
  cedulas: Object.entries(c)
    .filter(([, q]) => q > 0)
    .map(([cent, q]) => ({ valor: Number(cent) / 100, quantidade: q })),
})

interface Props {
  contagem: Contagem
  onChange: (c: Contagem) => void
  disabled?: boolean
}

function Linha({ cent, qtd, onQtd, disabled }: { cent: number; qtd: number; onQtd: (q: number) => void; disabled?: boolean }) {
  const set = (q: number) => onQtd(Math.max(0, Math.min(9999, q)))
  return (
    <div className="flex items-center gap-2 py-1 min-w-0">
      {/* rótulo + subtotal embaixo: a linha não depende da largura da coluna */}
      <div className="w-24 shrink-0 leading-tight">
        <span className="block text-sm text-gray-700 tabular-nums">{formatBRL(cent / 100)}</span>
        <span className="block h-4 text-[11px] text-gray-500 tabular-nums">{qtd > 0 ? `= ${formatBRL((cent * qtd) / 100)}` : ''}</span>
      </div>
      <div className="flex items-center shrink-0">
        <button type="button" disabled={disabled || qtd <= 0} onClick={() => set(qtd - 1)} aria-label={`Menos uma de ${formatBRL(cent / 100)}`}
          className="h-8 w-8 flex items-center justify-center rounded-l-lg bg-gray-100 border border-gray-200 text-gray-600 hover:bg-gray-200 disabled:opacity-40">
          <Minus size={14} />
        </button>
        <input
          inputMode="numeric"
          value={qtd || ''}
          placeholder="0"
          disabled={disabled}
          aria-label={`Quantidade de ${formatBRL(cent / 100)}`}
          onChange={e => set(parseInt(e.target.value.replace(/\D/g, ''), 10) || 0)}
          onFocus={e => e.target.select()}
          className="h-8 w-12 text-center text-sm tabular-nums bg-gray-100 border-y border-gray-200 text-gray-900                     focus:outline-none focus:ring-2 focus:ring-success-500 focus:z-10"
        />
        <button type="button" disabled={disabled || qtd >= 9999} onClick={() => set(qtd + 1)} aria-label={`Mais uma de ${formatBRL(cent / 100)}`}
          className="h-8 w-8 flex items-center justify-center rounded-r-lg bg-gray-100 border border-gray-200 text-gray-600 hover:bg-gray-200 disabled:opacity-40">
          <Plus size={14} />
        </button>
      </div>
    </div>
  )
}

/** Contagem de cédulas e moedas (tema escuro do PDV). O total é calculado por quem usa, via totalContagem. */
export default function ContagemCedulas({ contagem, onChange, disabled }: Props) {
  const atualizar = (cent: number, q: number) => {
    const novo = { ...contagem }
    if (q > 0) novo[cent] = q; else delete novo[cent]
    onChange(novo)
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-x-8 rounded-xl bg-background border border-border p-3">
      <div>
        <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-500 mb-1">Cédulas</p>
        {NOTAS.map(c => <Linha key={c} cent={c} qtd={contagem[c] ?? 0} onQtd={q => atualizar(c, q)} disabled={disabled} />)}
      </div>
      <div>
        <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-500 mb-1 mt-3 md:mt-0">Moedas</p>
        {MOEDAS.map(c => <Linha key={c} cent={c} qtd={contagem[c] ?? 0} onQtd={q => atualizar(c, q)} disabled={disabled} />)}
      </div>
    </div>
  )
}