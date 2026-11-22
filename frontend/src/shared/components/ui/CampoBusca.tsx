import { useEffect, useState } from 'react'
import { Search, X } from 'lucide-react'
import { baseInputClass } from './Field'

/** Minúsculas e sem acento, para a busca ignorar maiúsculas e acentuação. */
export const normalizarBusca = (s: string | null | undefined) =>
  (s ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim()

/** true se algum dos textos contém o termo (já normalizado ou não). Termo vazio casa tudo. */
export function casaBusca(termo: string, ...textos: (string | number | null | undefined)[]) {
  const t = normalizarBusca(termo)
  if (!t) return true
  return textos.some(x => normalizarBusca(x == null ? '' : String(x)).includes(t))
}

interface Props {
  /** Texto digitado, já com debounce (use para filtrar). */
  onBuscar: (termo: string) => void
  placeholder: string
  ariaLabel?: string
  className?: string
}

/**
 * Campo de busca no padrão da tela de Produtos (lupa à esquerda, "x" para limpar).
 * Entrega o termo com 300 ms de atraso, para não filtrar a cada tecla.
 */
export default function CampoBusca({ onBuscar, placeholder, ariaLabel, className = 'mb-4' }: Props) {
  const [valor, setValor] = useState('')

  useEffect(() => {
    const t = setTimeout(() => onBuscar(valor.trim()), 300)
    return () => clearTimeout(t)
    // onBuscar costuma ser um setState estável; ignorá-lo evita reiniciar o atraso
    // quando a página pai passa uma função nova a cada renderização.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [valor])

  return (
    <div className={`relative ${className}`}>
      <Search size={16} aria-hidden="true" className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
      <input
        type="search"
        aria-label={ariaLabel ?? placeholder}
        placeholder={placeholder}
        value={valor}
        onChange={e => setValor(e.target.value)}
        className={`${baseInputClass} pl-9 pr-9 py-2.5 [&::-webkit-search-cancel-button]:hidden`}
      />
      {valor && (
        <button
          type="button"
          onClick={() => setValor('')}
          aria-label="Limpar busca"
          className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded text-gray-400 hover:text-gray-600
            focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
        >
          <X size={16} />
        </button>
      )}
    </div>
  )
}
