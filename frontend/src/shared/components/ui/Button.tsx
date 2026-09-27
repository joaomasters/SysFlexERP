import { forwardRef } from 'react'
import type { ButtonHTMLAttributes } from 'react'
import { Loader2 } from 'lucide-react'

export type ButtonVariant = 'primary' | 'success' | 'danger' | 'warning' | 'secondary' | 'ghost' | 'outline-danger'
export type ButtonSize = 'sm' | 'md'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  loading?: boolean
  fullWidth?: boolean
}

/**
 * Botão padrão do sistema. A variante define o SIGNIFICADO da ação, não só a
 * cor — use sempre a variante certa para a ação, em vez de escolher cor por
 * conta própria, para que "confirmar/pagar" seja sempre verde, "cancelar/
 * excluir" sempre vermelho, etc. em todas as telas.
 *
 * - primary:   ação principal do formulário/tela (Salvar, Gerar carga agora, Abrir Inventário)
 * - success:   confirmação positiva (Pagar, Confirmar, Aprovar)
 * - danger:    ação destrutiva ou negativa (Excluir, Cancelar Venda, Estornar)
 * - warning:   ação que exige atenção mas não é destrutiva (Reabrir, Ajustar)
 * - secondary: ação alternativa neutra (Cancelar modal, Voltar, Fechar)
 * - ghost:     ação terciária discreta, geralmente com ícone (linha de tabela)
 * - outline-danger: ação destrutiva de baixa ênfase (Cancelar item numa lista)
 */
const variantClasses: Record<ButtonVariant, string> = {
  primary:        'bg-primary-600 hover:bg-primary-700 text-white disabled:bg-primary-300',
  success:        'bg-success-600 hover:bg-success-700 text-white disabled:bg-success-300',
  danger:         'bg-danger-600 hover:bg-danger-700 text-white disabled:bg-danger-300',
  warning:        'bg-warning-600 hover:bg-warning-700 text-white disabled:bg-warning-300',
  secondary:      'border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:text-gray-300',
  ghost:          'text-gray-500 hover:bg-gray-100 hover:text-gray-700 disabled:text-gray-300',
  'outline-danger': 'border border-danger-200 text-danger-600 hover:bg-danger-50 disabled:text-danger-200 disabled:border-danger-100',
}

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'px-3 py-1.5 text-xs',
  md: 'px-4 py-2.5 text-sm',
}

const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', loading, fullWidth, disabled, className = '', children, ...rest },
  ref
) {
  return (
    <button
      ref={ref}
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-1.5 rounded-lg font-medium
        transition-colors disabled:cursor-not-allowed
        ${variantClasses[variant]} ${sizeClasses[size]} ${fullWidth ? 'w-full' : ''} ${className}`}
      {...rest}
    >
      {loading && <Loader2 size={14} className="animate-spin" />}
      {children}
    </button>
  )
})

export default Button
