import type { ReactNode, ThHTMLAttributes, TdHTMLAttributes } from 'react'

/** Tabela padrão do sistema — usar dentro de um <Card padding="none"> com overflow-hidden. */
export function Table({ children }: { children: ReactNode }) {
  return <table className="w-full text-sm">{children}</table>
}

export function THead({ children }: { children: ReactNode }) {
  return <thead className="bg-gray-50 border-b">{children}</thead>
}

export function TH({ children, align = 'left', className = '', ...rest }:
  { children?: ReactNode; align?: 'left' | 'right' | 'center'; className?: string } & ThHTMLAttributes<HTMLTableCellElement>) {
  const alignClass = align === 'right' ? 'text-right' : align === 'center' ? 'text-center' : 'text-left'
  return (
    <th className={`px-4 py-3 ${alignClass} text-xs font-semibold text-gray-500 uppercase tracking-wide ${className}`} {...rest}>
      {children}
    </th>
  )
}

export function TBody({ children }: { children: ReactNode }) {
  return <tbody className="divide-y divide-gray-100">{children}</tbody>
}

export function TR({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <tr className={`hover:bg-gray-50 transition-colors ${className}`}>{children}</tr>
}

export function TD({ children, align = 'left', className = '', ...rest }:
  { children?: ReactNode; align?: 'left' | 'right' | 'center'; className?: string } & TdHTMLAttributes<HTMLTableCellElement>) {
  const alignClass = align === 'right' ? 'text-right' : align === 'center' ? 'text-center' : 'text-left'
  return (
    <td className={`px-4 py-3 ${alignClass} ${className}`} {...rest}>
      {children}
    </td>
  )
}

/** Estado vazio padrão de tabela/lista. */
export function EmptyState({ children }: { children: ReactNode }) {
  return <p className="text-center text-gray-400 py-8 text-sm">{children}</p>
}

/** Estado de carregamento padrão de tabela/lista. */
export function LoadingState({ children = 'Carregando...' }: { children?: ReactNode }) {
  return <div className="p-8 text-center text-gray-500 text-sm">{children}</div>
}
