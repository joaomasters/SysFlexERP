import type { ReactNode } from 'react'

interface CardProps {
  children: ReactNode
  className?: string
  /** Padding interno padrão. Use 'none' quando o conteúdo controla o próprio espaçamento (ex: tabelas). */
  padding?: 'none' | 'sm' | 'md'
}

const paddingClasses = { none: '', sm: 'p-4', md: 'p-6' }

/** Container branco padrão (cards, painéis de filtro, tabelas) — rounded-xl + shadow/border. */
export default function Card({ children, className = '', padding = 'md' }: CardProps) {
  return (
    <div className={`bg-white rounded-xl shadow border border-gray-100 ${paddingClasses[padding]} ${className}`}>
      {children}
    </div>
  )
}
