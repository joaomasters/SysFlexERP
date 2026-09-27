import type { ReactNode } from 'react'
import { X } from 'lucide-react'

interface ModalProps {
  title: string
  onClose: () => void
  children: ReactNode
  /** Largura máxima do modal. Padrão 'sm' (formulários curtos, confirmações). */
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl'
  footer?: ReactNode
}

const maxWidthClasses = { sm: 'max-w-sm', md: 'max-w-md', lg: 'max-w-lg', xl: 'max-w-2xl' }

/**
 * Modal padrão do sistema — overlay escuro + card branco arredondado.
 * Modelo de referência: modal "Registrar Pagamento" da tela Contas a Receber.
 */
export default function Modal({ title, onClose, children, maxWidth = 'sm', footer }: ModalProps) {
  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className={`bg-white rounded-2xl w-full ${maxWidthClasses[maxWidth]} shadow-xl`}>
        <div className="flex items-center justify-between px-6 py-4 border-b">
          <h2 className="font-bold text-gray-900">{title}</h2>
          <button onClick={onClose} aria-label="Fechar">
            <X size={20} className="text-gray-400 hover:text-gray-600" />
          </button>
        </div>
        <div className="px-6 py-5 space-y-4">{children}</div>
        {footer && <div className="flex gap-3 px-6 pb-6">{footer}</div>}
      </div>
    </div>
  )
}
