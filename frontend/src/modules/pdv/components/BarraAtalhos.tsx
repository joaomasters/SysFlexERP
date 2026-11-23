import type { ReactNode } from 'react'

export interface Atalho {
  tecla: string
  rotulo: string
  onClick: () => void
  desabilitado?: boolean
  /** Ação destrutiva (cancelar): borda e texto em vermelho. */
  perigo?: boolean
  icone?: ReactNode
}

/** Barra inferior com as teclas de atalho do caixa, clicáveis, no estilo dos PDVs de supermercado. */
export default function BarraAtalhos({ atalhos }: { atalhos: Atalho[] }) {
  return (
    <nav aria-label="Atalhos do caixa" className="shrink-0 flex items-stretch gap-2 px-4 py-2 bg-card border-t border-border overflow-x-auto">
      {atalhos.map(a => (
        <button
          key={a.tecla}
          type="button"
          onClick={a.onClick}
          disabled={a.desabilitado}
          className={`flex-1 min-w-[96px] flex flex-col items-center justify-center gap-0.5 rounded-lg border px-2 py-1.5 text-xs font-medium transition-colors
            disabled:opacity-40 disabled:cursor-not-allowed
            ${a.perigo
              ? 'border-danger-200 bg-danger-50 text-danger-700 hover:bg-danger-100'
              : 'border-gray-200 bg-gray-50 text-gray-700 hover:bg-gray-100'}`}
        >
          {a.tecla
            ? <span className="rounded bg-gray-900 px-1.5 font-mono text-[10px] leading-4 text-white">{a.tecla}</span>
            : <span className="h-4" aria-hidden="true" />}
          <span className="flex items-center gap-1">{a.icone}{a.rotulo}</span>
        </button>
      ))}
    </nav>
  )
}
