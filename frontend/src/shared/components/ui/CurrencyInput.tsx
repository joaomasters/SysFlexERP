import { forwardRef, useState, useEffect } from 'react'
import type { InputHTMLAttributes } from 'react'
import { maskCurrencyDigits, formatCurrencyDisplay } from '@/shared/utils/mask'

interface CurrencyInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'size'> {
  /** Valor numérico real (em reais, ex: 1234.5). Sempre a fonte da verdade. */
  value: number
  /** Disparado com o valor numérico já convertido, pronto para salvar/enviar à API. */
  onChange: (value: number) => void
  className?: string
  /** Tamanho do input — segue a escala padrão dos formulários do sistema. */
  size?: 'sm' | 'md'
  /** Estilo escuro — usado nas telas de operação do PDV (tema "console de caixa"). */
  dark?: boolean
}

/**
 * Campo de valor monetário (R$) com máscara de preenchimento estilo "caixa
 * eletrônico": o usuário digita apenas números e o campo formata sozinho a
 * partir dos centavos (ex: digitar "150050" vira "1.500,50").
 *
 * Este é o único componente que deve ser usado para capturar valores em
 * reais em qualquer tela do sistema (PDV, financeiro, estoque, etc.), para
 * garantir formatação e comportamento consistentes.
 */
const CurrencyInput = forwardRef<HTMLInputElement, CurrencyInputProps>(function CurrencyInput(
  { value, onChange, className = '', size = 'md', dark = false, ...rest },
  ref
) {
  const [display, setDisplay] = useState(formatCurrencyDisplay(value))

  // Mantém o display em sincronia quando o valor muda de fora (ex: reset de
  // formulário, edição carregando dados existentes) e o campo não está focado.
  useEffect(() => {
    setDisplay(formatCurrencyDisplay(value))
  }, [value])

  const sizeClasses = size === 'sm' ? 'px-3 py-1.5 text-sm' : 'px-3 py-2 text-sm'
  const disabled = rest.disabled

  return (
    <div className={`relative ${className}`}>
      <span className={`pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-sm ${dark ? 'text-gray-500' : 'text-gray-400'}`}>
        R$
      </span>
      <input
        ref={ref}
        type="text"
        inputMode="numeric"
        placeholder="0,00"
        value={display}
        onChange={(e) => {
          const { display: d, value: v } = maskCurrencyDigits(e.target.value)
          setDisplay(d)
          onChange(v)
        }}
        className={`w-full border rounded-lg pl-9 pr-3 text-right tabular-nums
          focus:outline-none focus:ring-2 focus:ring-primary-500
          transition-colors ${sizeClasses}
          ${dark
            ? (disabled ? 'bg-gray-800 text-gray-600 border-gray-800 cursor-not-allowed' : 'bg-gray-800 border-gray-700 text-white')
            : (disabled ? 'bg-gray-100 text-gray-400 border-gray-200 cursor-not-allowed' : 'border-gray-300 bg-white focus:border-primary-500')}`}
        {...rest}
      />
    </div>
  )
})

export default CurrencyInput
