import type { ReactNode } from 'react'

interface FieldProps {
  label: ReactNode
  error?: string
  hint?: string
  children: ReactNode
  className?: string
}

/**
 * Wrapper padrão de campo de formulário: label + input + mensagem de erro/ajuda.
 * Usar em todo formulário do sistema para manter tipografia e espaçamento
 * consistentes (label 12px/medium/cinza-600, erro 12px vermelho, ajuda 11px cinza-400).
 */
export default function Field({ label, error, hint, children, className = '' }: FieldProps) {
  return (
    <div className={className}>
      <label className="block text-xs font-medium text-gray-600 mb-1">{label}</label>
      {children}
      {error && <p className="text-danger-600 text-xs mt-1">{error}</p>}
      {!error && hint && <p className="text-[11px] text-gray-400 mt-1">{hint}</p>}
    </div>
  )
}

/** Classe padrão para inputs de texto/select simples (não mascarados). */
export const baseInputClass =
  'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white ' +
  'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500 transition-colors'

export const disabledInputClass =
  'w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-gray-100 text-gray-400 cursor-not-allowed'
