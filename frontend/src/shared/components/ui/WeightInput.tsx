import { forwardRef, useState, useEffect } from 'react'
import type { InputHTMLAttributes } from 'react'
import { maskWeightDigits, formatWeightDisplay } from '@/shared/utils/mask'

interface WeightInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'size'> {
  /** Valor numérico real em kg (ex: 1.5). Sempre a fonte da verdade. */
  value: number
  onChange: (value: number) => void
  /** Unidade exibida como sufixo (kg, g, un, cx...). Padrão: kg. */
  unit?: string
  /** Casas decimais da máscara. Padrão: 3 (grama de precisão). */
  decimals?: number
  className?: string
  size?: 'sm' | 'md'
  /** Estilo escuro — usado nas telas de operação do PDV (tema "console de caixa"). */
  dark?: boolean
}

/**
 * Campo de peso (kg/g) com a mesma máscara de preenchimento "caixa
 * eletrônico" usada no CurrencyInput, mas com casas decimais e unidade
 * configuráveis. Único componente que deve ser usado para capturar peso em
 * qualquer tela (estoque, desossa, balança, PDV).
 */
const WeightInput = forwardRef<HTMLInputElement, WeightInputProps>(function WeightInput(
  { value, onChange, unit = 'kg', decimals = 3, className = '', size = 'md', dark = false, ...rest },
  ref
) {
  const [display, setDisplay] = useState(formatWeightDisplay(value, decimals))

  useEffect(() => {
    setDisplay(formatWeightDisplay(value, decimals))
  }, [value, decimals])

  const sizeClasses = size === 'sm' ? 'px-3 py-1.5 text-sm' : 'px-3 py-2 text-sm'
  const disabled = rest.disabled

  return (
    <div className={`relative ${className}`}>
      <input
        ref={ref}
        type="text"
        inputMode="numeric"
        placeholder={formatWeightDisplay(0, decimals)}
        value={display}
        onChange={(e) => {
          const { display: d, value: v } = maskWeightDigits(e.target.value, decimals)
          setDisplay(d)
          onChange(v)
        }}
        className={`w-full border rounded-lg pl-3 text-right tabular-nums
          focus:outline-none focus:ring-2 focus:ring-primary-500
          transition-colors ${unit ? 'pr-9' : 'pr-3'} ${sizeClasses}
          ${dark
            ? (disabled ? 'bg-gray-800 text-gray-600 border-gray-800 cursor-not-allowed' : 'bg-gray-800 border-gray-700 text-white')
            : (disabled ? 'bg-gray-100 text-gray-400 border-gray-200 cursor-not-allowed' : 'border-gray-300 bg-white focus:border-primary-500')}`}
        {...rest}
      />
      {unit && (
        <span className={`pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-sm ${dark ? 'text-gray-500' : 'text-gray-400'}`}>
          {unit}
        </span>
      )}
    </div>
  )
})

export default WeightInput