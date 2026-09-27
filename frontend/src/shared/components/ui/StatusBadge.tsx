import type { ReactNode } from 'react'

export type BadgeTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'purple'

interface StatusBadgeProps {
  tone: BadgeTone
  children: ReactNode
  className?: string
}

/**
 * Pílula de status padrão do sistema (fundo claro + texto forte, rounded-full).
 * O `tone` representa o SIGNIFICADO do status, não uma cor pré-definida por
 * palavra — o mesmo texto ("ABERTO") pode ter tons diferentes em telas
 * diferentes dependendo do que significa no contexto:
 *
 * - success: estado concluído/positivo — PAGO, QUITADO, FINALIZADO, EMITIDA, ativo
 * - warning: aguardando ação, ainda sem problema — PARCIAL, PENDENTE, conta em aberto
 * - danger:  problema/negativo — VENCIDO, FURTO, AVARIA, excluído
 * - info:    processo em andamento — caixa aberto, inventário em contagem, agrupado
 * - neutral: inativo/cancelado sem urgência — CANCELADO, inativo
 * - purple:  rótulo de categoria (não é bem um "status") — tipo de cliente, etc.
 */
const toneClasses: Record<BadgeTone, string> = {
  success: 'bg-success-100 text-success-700',
  warning: 'bg-warning-100 text-warning-700',
  danger:  'bg-danger-100 text-danger-700',
  info:    'bg-info-100 text-info-700',
  neutral: 'bg-gray-100 text-gray-500',
  purple:  'bg-purple-100 text-purple-700',
}

export default function StatusBadge({ tone, children, className = '' }: StatusBadgeProps) {
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium whitespace-nowrap
        ${toneClasses[tone]} ${className}`}
    >
      {children}
    </span>
  )
}
