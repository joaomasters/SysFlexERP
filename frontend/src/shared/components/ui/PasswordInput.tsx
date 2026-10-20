import { forwardRef, useState } from 'react'
import type { InputHTMLAttributes } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { baseInputClass } from './Field'

type PasswordInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>

/**
 * Campo de senha com botão "olho" para mostrar/ocultar o que foi digitado.
 * Use em qualquer tela que peça uma senha nova (cadastro de usuário, troca de
 * senha), para o operador conferir o que digitou antes de salvar.
 */
const PasswordInput = forwardRef<HTMLInputElement, PasswordInputProps>(function PasswordInput(
  { className = '', ...rest },
  ref
) {
  const [visivel, setVisivel] = useState(false)

  return (
    <div className="relative">
      <input
        ref={ref}
        type={visivel ? 'text' : 'password'}
        className={`${baseInputClass} pr-10 ${className}`}
        {...rest}
      />
      <button
        type="button"
        onClick={() => setVisivel(v => !v)}
        aria-label={visivel ? 'Ocultar senha' : 'Mostrar senha'}
        aria-pressed={visivel}
        title={visivel ? 'Ocultar senha' : 'Mostrar senha'}
        className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded text-gray-400 hover:text-gray-600
          focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
      >
        {visivel ? <EyeOff size={16} /> : <Eye size={16} />}
      </button>
    </div>
  )
})

export default PasswordInput